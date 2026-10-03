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
import net.minecraft.world.phys.AABB;
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
        entityData.define(PREVIEW, "");
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
                activeMove();
                if (target != null && stateTicks >= GAP && move == Move.ORBIT) chooseAttack();
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
                if (stateTicks == ULT_RELEASE) castShardRain(level);
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
        altitudeWant = 0.2;   // sinks low while reeling
        if (move != Move.ORBIT) endSwoop();
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

    /** Share of the arena the ultimate's ice shards cover. */
    private static final double SHARD_COVERAGE = 0.65;
    /** Spacing of the shard grid: hexagonal cells whose circumradius (spacing / sqrt 3) fits inside a shard's radius. */
    private static final double SHARD_SPACING = 3.4;

    /**
     * The ultimate: ice shards rain on about two thirds of the arena. The arena is tiled with hexagonal cells;
     * a random 65% of them (always including the cells the players stand in, so standing still is never safe)
     * get a shard, each landing a little after the others for a cascading rain.
     */
    private void castShardRain(ServerLevel level) {
        Vec3 c = home != null ? Vec3.atBottomCenterOf(home) : position();
        double reach = SoulAltarBlockEntity.ARENA_RADIUS - 0.8;
        java.util.List<Vec3> cells = new java.util.ArrayList<>();
        double rowH = SHARD_SPACING * Math.sqrt(3) / 2;
        for (int row = (int) -Math.ceil(reach / rowH); row <= Math.ceil(reach / rowH); row++) {
            double off = (row & 1) == 0 ? 0 : SHARD_SPACING / 2;
            for (double dx = -reach - off; dx <= reach + SHARD_SPACING; dx += SHARD_SPACING) {
                double x = dx + off, z = row * rowH;
                if (x * x + z * z <= reach * reach) cells.add(new Vec3(c.x + x, c.y, c.z + z));
            }
        }
        java.util.Collections.shuffle(cells, new java.util.Random(random.nextLong()));
        int count = (int) Math.round(cells.size() * SHARD_COVERAGE);
        java.util.List<Vec3> chosen = new java.util.ArrayList<>();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(BlockPos.containing(c)).inflate(reach + 4, 12, reach + 4))) {
            cells.stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(p.getX(), c.y, p.getZ())))
                    .ifPresent(v -> {
                        cells.remove(v);
                        chosen.add(v);
                    });
        }
        for (int i = 0; chosen.size() < count && i < cells.size(); i++) chosen.add(cells.get(i));
        for (Vec3 v : chosen) {
            BlockPos ground = com.soulless.ability.CrackManager.findSurface(level, BlockPos.containing(v.x, c.y + 1, v.z));
            IceShardEntity shard = new IceShardEntity(ModEntities.ICE_SHARD.get(), level);
            shard.setup(getId(), random.nextInt(15));
            shard.moveTo(v.x, ground != null ? ground.getY() + 1 : c.y, v.z);
            level.addFreshEntity(shard);
        }
        level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 4.0F, 0.5F);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 3.0F, 0.6F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 4, getZ(), 150, 2.5, 2, 2.5, 0.25);
    }

    /** Where the right hand is during a throw: up and to the side of the body, facing the target. */
    private Vec3 handPosition() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return position().add(0, 5.2, 0).add(fwd.scale(1.2)).add(right.scale(1.4));
    }

    // ------------------------------------------------------------------ movement: active flight inside the arena

    /** Horizontal drift velocity chosen by the AI this tick; applied directly in {@link #hover()}. */
    private Vec3 drift = Vec3.ZERO;

    /**
     * Moves the Reaper ourselves (vanilla travel() + ground friction barely moves a floating mob): drift
     * horizontally, bob above the ground, never leave the arena.
     */
    private void hover() {
        double ground = groundY();
        altitude = Mth.lerp(0.06, altitude, altitudeWant);
        double wantY = ground + altitude + Math.sin(tickCount * 0.08) * 0.15;
        Vec3 step = new Vec3(drift.x, Mth.clamp((wantY - getY()) * 0.2, -0.4, 0.4), drift.z);
        if (home != null) {
            Vec3 h = Vec3.atBottomCenterOf(home);
            Vec3 off = new Vec3(getX() + step.x - h.x, 0, getZ() + step.z - h.z);
            double max = SoulAltarBlockEntity.ARENA_RADIUS - 3;
            if (off.length() > max) {
                step = new Vec3(0, step.y, 0).add(off.normalize().scale(-0.15));
                if (move == Move.SWOOP) endSwoop();
            }
        }
        move(net.minecraft.world.entity.MoverType.SELF, step);
        setDeltaMovement(Vec3.ZERO);
        // every wing beat is heard (the beat in the model is 1.6 s = 32 ticks)
        if (tickCount % 32 == 0 && !isPreview()) playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.65F + random.nextFloat() * 0.1F);
    }

    // --- active flight: circling the target, changing height and direction, and swooping past it

    private enum Move { ORBIT, SWOOP_WINDUP, SWOOP }

    private static final double ORBIT_RADIUS = 10;
    private static final float SWOOP_DAMAGE = 10;
    private Move move = Move.ORBIT;
    private int moveTicks;
    private int orbitDir = 1;
    private double altitude = 0.5, altitudeWant = 0.5;
    @Nullable private Vec3 swoopTo;
    private boolean swoopHit;

    private void activeMove() {
        if (target == null) {
            stop();
            altitudeWant = 0.5;
            return;
        }
        moveTicks++;
        Vec3 to = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        double d = Math.max(0.01, to.length());
        Vec3 radial = to.scale(1 / d);
        switch (move) {
            case ORBIT -> {
                // circle at a fighting distance, sliding in and out, rising and sinking
                Vec3 tangent = new Vec3(-radial.z, 0, radial.x).scale(orbitDir);
                Vec3 want = tangent.scale(0.26).add(radial.scale(Mth.clamp((d - ORBIT_RADIUS) * 0.05, -0.22, 0.22)));
                steer(want, 0.1);
                altitudeWant = 2.2 + Math.sin(tickCount * 0.045) * 1.8;
                if (moveTicks > 45 + random.nextInt(40)) {
                    moveTicks = 0;
                    if (random.nextInt(3) == 0) orbitDir = -orbitDir;
                    if (d < 22 && random.nextInt(5) < 2) startSwoop(radial);
                }
            }
            case SWOOP_WINDUP -> {
                // rear back and up: the tell
                steer(radial.scale(-0.12), 0.2);
                altitudeWant = 4.5;
                if (moveTicks >= 12) {
                    move = Move.SWOOP;
                    moveTicks = 0;
                    playSound(SoundEvents.PHANTOM_SWOOP, 3.0F, 0.5F);
                }
            }
            case SWOOP -> {
                Vec3 left = swoopTo == null ? Vec3.ZERO : new Vec3(swoopTo.x - getX(), 0, swoopTo.z - getZ());
                if (swoopTo == null || left.length() < 1.5 || moveTicks > 40) {
                    endSwoop();
                    return;
                }
                steer(left.normalize().scale(0.62), 0.35);
                altitudeWant = 0.3;
                if (!swoopHit && getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
                    swoopHit = true;
                    target.hurt(damageSources().mobAttack(this), SWOOP_DAMAGE);
                    Vec3 push = left.normalize().scale(1.4);
                    target.push(push.x, 0.5, push.z);
                    target.hurtMarked = true;
                }
            }
        }
    }

    /** Pass close beside the target and on beyond it. */
    private void startSwoop(Vec3 radial) {
        Vec3 side = new Vec3(-radial.z, 0, radial.x).scale(random.nextBoolean() ? 1.8 : -1.8);
        swoopTo = target.position().add(side).add(radial.scale(9));
        swoopHit = false;
        move = Move.SWOOP_WINDUP;
        moveTicks = 0;
    }

    private void endSwoop() {
        move = Move.ORBIT;
        moveTicks = 0;
        swoopTo = null;
        orbitDir = random.nextBoolean() ? 1 : -1;
    }

    private void steer(Vec3 want, double rate) {
        drift = new Vec3(Mth.lerp(rate, drift.x, want.x), 0, Mth.lerp(rate, drift.z, want.z));
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
        if (source.getEntity() == this || source.getDirectEntity() instanceof SoulBoltEntity || source.getDirectEntity() instanceof AbyssHeadEntity
                || source.getDirectEntity() instanceof IceShardEntity) {
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
        if (!isPreview()) bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    // ------------------------------------------------------------------ death: the reward chest

    /** Experience for the kill, as much as the Ender Dragon's first death. */
    private static final int XP_REWARD = 12000;

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + random.nextDouble() * 6, getZ(), 6, 0.8, 0.5, 0.8, 0.1);
            if (deathTime % 10 == 0) level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 3, getZ(), 2, 1, 2, 1, 0);
            // like the dragon: bursts of experience while it dies, the rest when it is gone
            if (deathTime > 15 && deathTime < 60 && deathTime % 5 == 0) {
                net.minecraft.world.entity.ExperienceOrb.award(level, position().add(0, 3, 0), Mth.floor(XP_REWARD * 0.08F));
            }
            if (deathTime == 60) {
                net.minecraft.world.entity.ExperienceOrb.award(level, position().add(0, 2, 0), XP_REWARD - Mth.floor(XP_REWARD * 0.08F) * 8);
                if (home != null && level.getBlockEntity(home) instanceof SoulAltarBlockEntity altar) {
                    altar.onBossDefeated();   // the arena crumbles, then the reward chest appears on the altar
                } else {
                    placeRewardChest(level);
                }
                level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 3, getZ(), 200, 1.5, 3, 1.5, 0.3);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public int getExperienceReward() {
        return 0;   // paid out in tickDeath
    }

    @Nullable
    public BlockPos home() {
        return home;
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
        if (isPreview()) tag.putString("preview", entityData.get(PREVIEW));
        tag.putInt("ults", ultsDone);
        tag.putInt("cycle", cycle);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("home")) home = NbtUtils.readBlockPos(tag.getCompound("home"));
        if (tag.contains("preview")) entityData.set(PREVIEW, tag.getString("preview"));
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

    private static final RawAnimation AMBIENT = RawAnimation.begin().thenLoop("animation.wailing_reaper.ambient");

    /** Preview statues ({@code /soulless preview}) play this animation on loop and do nothing else. */
    private static final EntityDataAccessor<String> PREVIEW = SynchedEntityData.defineId(WailingReaperEntity.class, EntityDataSerializers.STRING);

    /** Lengths (ticks) of the one-shot animations, so a preview can replay them with a pause in between. */
    private static final java.util.Map<String, Integer> PREVIEW_TICKS = java.util.Map.of(
            "bolt", BOLT_TOTAL, "erupt", ERUPT_TOTAL, "ult", ULT_TOTAL, "emerge", EMERGE_TOTAL, "death", 60);
    private int previewCycle = -1;

    public void makePreview(String animation) {
        entityData.set(PREVIEW, animation);
        setNoAi(true);
        setInvulnerable(true);
        setSilent(true);
        getPersistentData().putBoolean("soulless_preview", true);
    }

    public boolean isPreview() {
        return !entityData.get(PREVIEW).isEmpty();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // always on: flame tail, flickering tongues, orbiting souls, cape
        controllers.add(new AnimationController<>(this, "ambient", 0, state -> state.setAndContinue(AMBIENT)));
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isPreview()) {
                String anim = entityData.get(PREVIEW);
                if (anim.equals("idle") || anim.equals("stunned")) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.wailing_reaper." + anim));
                }
                // one-shot attacks: play, hold the last pose a moment, then blend back to the start (no loop snap)
                int period = PREVIEW_TICKS.getOrDefault(anim, 40) + 25;
                if (tickCount / period != previewCycle) {
                    previewCycle = tickCount / period;
                    state.getController().forceAnimationReset();
                }
                return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.wailing_reaper." + anim));
            }
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
