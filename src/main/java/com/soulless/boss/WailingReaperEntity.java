package com.soulless.boss;

import com.soulless.effect.Stun;
import com.soulless.registry.ModEntities;
import com.soulless.registry.ModItems;
import com.soulless.registry.ModParticles;
import com.soulless.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;

/**
 * The Wailing Reaper: a towering soul demon summoned at the Soul Altar.
 * <p>
 * Rhythm: drift and keep distance, then one of
 * <ul>
 * <li>BOLT - a wind-up and a lightly homing soul bolt (dodge by sprinting sideways);</li>
 * <li>ERUPT - both arms heave up and souls burst from under the player after a short warning (more damage +
 * slowness; needs more speed to dodge); then the Reaper is stunned for 4 s;</li>
 * <li>ULT - at 50% and 15% health: it hugs itself and screams for 3 s, then ten abyss heads fly out in a ring;
 * then it is stunned for 6 s and takes 20% more damage.</li>
 * </ul>
 * Immune to fire and poison. On death a chest with Soul Forged appears where the altar stood.
 */
public class WailingReaperEntity extends Monster implements GeoEntity {
    public static final float MAX_HEALTH = 800;

    public enum State { IDLE, BOLT, ERUPT, ULT, STUNNED, EMERGE }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(WailingReaperEntity.class, EntityDataSerializers.INT);

    private static final int GAP = 34;
    private static final int BOLT_RELEASE = 16, BOLT_TOTAL = 28;
    private static final int ERUPT_RELEASE = 14, ERUPT_TOTAL = 30, ERUPT_STUN = 80;
    private static final int ULT_RELEASE = 60, ULT_TOTAL = 70, ULT_STUN = 120;
    private static final int EMERGE_TOTAL = 50;
    private static final float[] ULT_AT = {0.5F, 0.15F};
    private static final float VULNERABLE_MULT = 1.2F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    @Nullable private BlockPos home;
    private int stateTicks;
    private int stunLeft;
    private boolean vulnerable;
    private int cycle;
    private int ultsDone;
    @Nullable private Player target;

    public WailingReaperEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;   // a floating ghost: drifts through the altar and terrain (the arena radius keeps it in)
        setPersistenceRequired();
        xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, State.IDLE.ordinal());
    }

    public State state() {
        return State.values()[entityData.get(STATE)];
    }

    private void setState(State s) {
        entityData.set(STATE, s.ordinal());
        stateTicks = 0;
    }

    public void setHome(BlockPos pos) {
        home = pos;
    }

    public void playEmerge() {
        setState(State.EMERGE);
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        // everything is scripted in customServerAiStep
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        bossBar.setProgress(getHealth() / getMaxHealth());
        stateTicks++;
        if (tickCount % 20 == 0 || target == null || !validTarget(target)) target = findTarget();
        if (target != null) getLookControl().setLookAt(target, 10, 10);

        hover();
        if (tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1 + random.nextDouble() * 5, getZ(), 1, 0.6, 0.3, 0.6, 0.02);
            level.sendParticles(ModParticles.SOUL_EMBER.get(), getX(), getY() + 0.3, getZ(), 2, 0.5, 0.2, 0.5, 0.02);
        }

        switch (state()) {
            case EMERGE -> {
                if (stateTicks == 1) level.playSound(null, blockPosition(), ModSounds.REAPER_SCREAM.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
                if (stateTicks >= EMERGE_TOTAL) setState(State.IDLE);
            }
            case IDLE -> {
                moveTowardRange();
                if (target != null && stateTicks >= GAP) chooseAttack();
            }
            case BOLT -> {
                stop();
                if (stateTicks == BOLT_RELEASE && target != null) fireBolt(level);
                if (stateTicks >= BOLT_TOTAL) setState(State.IDLE);
            }
            case ERUPT -> {
                stop();
                if (stateTicks == ERUPT_RELEASE && target != null) {
                    SoulEruptionEntity e = new SoulEruptionEntity(ModEntities.SOUL_ERUPTION.get(), level);
                    e.setOwnerId(getId());
                    e.moveTo(target.getX(), target.getY(), target.getZ());
                    level.addFreshEntity(e);
                }
                if (stateTicks >= ERUPT_TOTAL) stun(ERUPT_STUN, false);
            }
            case ULT -> {
                stop();
                if (stateTicks == 1) {
                    level.playSound(null, blockPosition(), ModSounds.REAPER_SCREAM.get(), SoundSource.HOSTILE, 4.0F, 0.7F);
                }
                if (stateTicks < ULT_RELEASE && stateTicks % 4 == 0) {
                    // souls are pulled in as it gathers itself
                    for (int i = 0; i < 6; i++) {
                        double a = random.nextDouble() * Math.PI * 2, r = 6;
                        Vec3 from = position().add(Math.cos(a) * r, 1 + random.nextDouble() * 5, Math.sin(a) * r);
                        Vec3 v = position().add(0, 3.5, 0).subtract(from).scale(0.08);
                        level.sendParticles(ModParticles.SOUL_EMBER.get(), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
                    }
                }
                if (stateTicks == ULT_RELEASE) releaseHeads(level);
                if (stateTicks >= ULT_TOTAL) stun(ULT_STUN, true);
            }
            case STUNNED -> {
                stop();
                if (--stunLeft <= 0) {
                    vulnerable = false;
                    setState(State.IDLE);
                }
            }
        }
    }

    private void chooseAttack() {
        float hp = getHealth() / getMaxHealth();
        if (ultsDone < ULT_AT.length && hp <= ULT_AT[ultsDone]) {
            ultsDone++;
            setState(State.ULT);
            return;
        }
        cycle++;
        setState(cycle % 4 == 0 ? State.ERUPT : State.BOLT);
        playSound(SoundEvents.WITHER_AMBIENT, 1.0F, 1.4F);
    }

    private void stun(int ticks, boolean vulnerableToo) {
        setState(State.STUNNED);
        stunLeft = ticks;
        vulnerable = vulnerableToo;
        Stun.apply(this, ticks, null);
    }

    private void fireBolt(ServerLevel level) {
        Vec3 hand = handPosition();
        SoulBoltEntity bolt = new SoulBoltEntity(ModEntities.SOUL_BOLT.get(), level);
        bolt.setOwner(this);
        bolt.setTarget(target);
        bolt.moveTo(hand.x, hand.y, hand.z);
        Vec3 dir = target.getEyePosition().subtract(hand).normalize();
        bolt.setDeltaMovement(dir.scale(SoulBoltEntity.SPEED));
        level.addFreshEntity(bolt);
        level.playSound(null, hand.x, hand.y, hand.z, ModSounds.BOLT_FLY.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
    }

    private void releaseHeads(ServerLevel level) {
        // at chest height of a standing player, so the ring actually has to be dodged
        Vec3 c = new Vec3(getX(), (target != null ? target.getY() : groundY()) + 1.0, getZ());
        for (int i = 0; i < 10; i++) {
            // the ring is turned so one head flies straight at the target: standing still is not safe
            double toTarget = target != null ? Math.atan2(target.getZ() - getZ(), target.getX() - getX()) : 0;
            double a = Math.PI * 2 * i / 10 + toTarget;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            AbyssHeadEntity head = new AbyssHeadEntity(level, this, dir);
            head.moveTo(c.x + dir.x * 1.5, c.y, c.z + dir.z * 1.5);
            level.addFreshEntity(head);
        }
        level.playSound(null, blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 3.0F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 120, 2, 1.5, 2, 0.2);
    }

    /** Where the right hand is during a throw: up and to the side of the body, facing the target. */
    private Vec3 handPosition() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return position().add(0, 5.2, 0).add(fwd.scale(1.2)).add(right.scale(1.4));
    }

    // ------------------------------------------------------------------ movement: a slow, floating drift

    /** Horizontal drift velocity chosen by the AI this tick; applied directly in {@link #hover()}. */
    private Vec3 drift = Vec3.ZERO;

    /**
     * Moves the Reaper ourselves (vanilla travel() + ground friction barely moves a floating mob): drift
     * horizontally, bob above the ground, never leave the arena.
     */
    private void hover() {
        double ground = groundY();
        double wantY = ground + 0.4 + Math.sin(tickCount * 0.08) * 0.15;
        Vec3 step = new Vec3(drift.x, (wantY - getY()) * 0.2, drift.z);
        if (home != null) {
            Vec3 h = Vec3.atBottomCenterOf(home);
            Vec3 off = new Vec3(getX() + step.x - h.x, 0, getZ() + step.z - h.z);
            double max = SoulAltarBlockEntity.ARENA_RADIUS - 2.5;
            if (off.length() > max) step = new Vec3(0, step.y, 0).add(off.normalize().scale(-0.1));
        }
        move(net.minecraft.world.entity.MoverType.SELF, step);
        setDeltaMovement(Vec3.ZERO);
    }

    /** Top of the highest solid block in a few blocks above/below, so it rides over the altar dais. */
    private double groundY() {
        BlockPos p = blockPosition();
        for (int dy = 3; dy >= -8; dy--) {
            BlockPos q = p.offset(0, dy, 0);
            if (!level().getBlockState(q).isAir() && level().getBlockState(q.above()).isAir()) return q.getY() + 1;
        }
        return getY();
    }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
    }

    private void moveTowardRange() {
        if (target == null) {
            stop();
            return;
        }
        Vec3 to = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        double d = to.length();
        double speed = 0.11;
        Vec3 want;
        if (d > 12) want = to.normalize().scale(speed);
        else if (d < 6) want = to.normalize().scale(-speed);
        else want = new Vec3(-to.z, 0, to.x).normalize().scale(speed * 0.6 * (((tickCount / 100) & 1) == 0 ? 1 : -1));
        drift = new Vec3(Mth.lerp(0.15, drift.x, want.x), 0, Mth.lerp(0.15, drift.z, want.z));
    }

    private void stop() {
        drift = drift.scale(0.6);
    }

    @Nullable
    private Player findTarget() {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(40), this::validTarget).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private boolean validTarget(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(this) < 48 * 48;
    }

    // ------------------------------------------------------------------ damage rules

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state() == State.EMERGE || source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL)) return false;
        if (source.getEntity() == this || source.getDirectEntity() instanceof SoulBoltEntity || source.getDirectEntity() instanceof AbyssHeadEntity) {
            return false;
        }
        if (vulnerable) amount *= VULNERABLE_MULT;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect() != MobEffects.POISON && super.canBeAffected(effect);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.REAPER_SCREAM.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    // ------------------------------------------------------------------ death: the reward chest

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + random.nextDouble() * 6, getZ(), 6, 0.8, 0.5, 0.8, 0.1);
            if (deathTime % 10 == 0) level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 3, getZ(), 2, 1, 2, 1, 0);
            if (deathTime == 60) {
                placeRewardChest(level);
                level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 3, getZ(), 200, 1.5, 3, 1.5, 0.3);
                remove(RemovalReason.KILLED);
            }
        }
    }

    private void placeRewardChest(ServerLevel level) {
        BlockPos pos = home != null ? home : blockPosition();
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(13, new ItemStack(ModItems.SOUL_FORGED.get()));
        }
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("home", NbtUtils.writeBlockPos(home));
        tag.putInt("ults", ultsDone);
        tag.putInt("cycle", cycle);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("home")) home = NbtUtils.readBlockPos(tag.getCompound("home"));
        ultsDone = tag.getInt("ults");
        cycle = tag.getInt("cycle");
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ GeckoLib

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wailing_reaper.idle");
    private static final RawAnimation BOLT = RawAnimation.begin().thenPlay("animation.wailing_reaper.bolt");
    private static final RawAnimation ERUPT = RawAnimation.begin().thenPlay("animation.wailing_reaper.erupt");
    private static final RawAnimation ULT = RawAnimation.begin().thenPlay("animation.wailing_reaper.ult");
    private static final RawAnimation STUNNED = RawAnimation.begin().thenLoop("animation.wailing_reaper.stunned");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.wailing_reaper.emerge");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.wailing_reaper.death");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(switch (state()) {
                case BOLT -> BOLT;
                case ERUPT -> ERUPT;
                case ULT -> ULT;
                case STUNNED -> STUNNED;
                case EMERGE -> EMERGE;
                default -> IDLE;
            });
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
