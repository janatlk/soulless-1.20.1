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
    private static final int ULT_RELEASE = 30, ULT_TOTAL = 70, ULT_STUN = 120;
    /** Ticks after the cast until the ice rain's last spike lands (set by castShardRain). */
    private int ultRainTicks = 40;
    private static final int EMERGE_TOTAL = 50;
    /** The intro: at this tick of rising, the Reaper freezes for INTRO_TICKS while the camera of everyone on the arena turns on it. */
    private static final int INTRO_AT = 44, INTRO_TICKS = 72;
    private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(WailingReaperEntity.class, EntityDataSerializers.INT);
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
        entityData.define(INTRO, 0);
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

    /** Frozen by its intro: the server holds it still (its rise plays out and holds its last pose). */
    public boolean frozen() {
        return entityData.get(INTRO) > 0;
    }

    private void startIntro(ServerLevel level) {
        entityData.set(INTRO, INTRO_TICKS);
        BlockPos at = home != null ? home : blockPosition();
        for (var p : level.players()) {
            if (p.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 40 * 40) {
                com.soulless.network.ModNetwork.sendToPlayer(p, new com.soulless.network.BossIntroPacket(getId(), INTRO_TICKS, "boss.soulless.wailing_reaper"));
            }
        }
        level.playSound(null, blockPosition(), ModSounds.REAPER_SCREAM.get(), SoundSource.HOSTILE, 3.0F, 0.55F);
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
        if (frozen()) {   // the intro: held still mid-rise
            entityData.set(INTRO, entityData.get(INTRO) - 1);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (state() == State.EMERGE && stateTicks == INTRO_AT - 1 && !isPreview()) startIntro(level);
        stateTicks++;
        if (tickCount % 20 == 0 || target == null || !validTarget(target)) target = findTarget();
        if (target != null) getLookControl().setLookAt(target, 10, 10);

        hover();
        faceTarget();
        trackTarget();
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
                    Vec3 at = leadTarget(2.0);
                    e.moveTo(at.x, at.y, at.z);
                    level.addFreshEntity(e);
                }
                if (stateTicks >= ERUPT_TOTAL) stun(ERUPT_STUN, false);
            }
            case ULT -> {
                stop();
                if (stateTicks == 1) {
                    level.playSound(null, blockPosition(), ModSounds.REAPER_SCREAM.get(), SoundSource.HOSTILE, 4.0F, 0.7F);
                }
                // arms raised to the sky the whole time: frost streams up from its hands, an icy howl rings out
                if (stateTicks % 2 == 0) {
                    for (int side = -1; side <= 1; side += 2) {
                        float yaw = yBodyRot * Mth.DEG_TO_RAD;
                        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(-side);
                        Vec3 hand = position().add(0, 7.2, 0).add(right.scale(1.6));
                        level.sendParticles(ParticleTypes.SNOWFLAKE, hand.x, hand.y, hand.z, 2, 0.2, 0.2, 0.2, 0.05);
                        level.sendParticles(ModParticles.SOUL_EMBER.get(), hand.x, hand.y, hand.z, 0, 0, 0.6, 0, 1);
                    }
                }
                if (stateTicks % 16 == 2) level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.5F, 0.5F);
                if (stateTicks % 40 == 10) level.playSound(null, blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 2.5F, 0.5F);
                if (stateTicks == ULT_RELEASE) castShardRain(level);
                if (stateTicks >= ULT_RELEASE + ultRainTicks) stun(ULT_STUN, true);
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

    /**
     * Knocked reeling by a player (Soul Crush): drops whatever it was doing and is stunned. Rising from the
     * altar and the ultimate cannot be interrupted; an existing stun is only lengthened.
     */
    public void stagger(int ticks) {
        if (isDeadOrDying() || state() == State.EMERGE) return;
        if (state() == State.ULT) {
            Stun.apply(this, ticks, null);
            return;
        }
        if (state() == State.STUNNED) {
            stunLeft = Math.max(stunLeft, ticks);
            Stun.apply(this, stunLeft, null);
            return;
        }
        stun(ticks, false);
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

    /** The first spikes start falling this long after the runes appear. */
    private static final int RAIN_WARN = 30;
    /** Spikes fall four at a time, a group every this many ticks. */
    private static final int RAIN_GROUP = 3, RAIN_EVERY = 5;

    /**
     * The ultimate: a frost rune on every cell of a hexagonal grid over the whole arena; then ice spikes fall four
     * at a time in random order, in two passes - every cell is struck twice, and its rune only goes out after the
     * second. Nowhere is safe for long; the player has to keep moving onto cells that were already struck.
     */
    private void castShardRain(ServerLevel level) {
        Vec3 c = home != null ? Vec3.atBottomCenterOf(home) : position();
        int lastFall = iceRain(level, c, SoulAltarBlockEntity.ARENA_RADIUS - 0.8, getId(), random);
        ultRainTicks = lastFall + IceShardEntity.FALL + 10;
        level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 4.0F, 0.5F);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 3.0F, 0.6F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 4, getZ(), 150, 2.5, 2, 2.5, 0.25);
    }

    /**
     * The ice rain itself, around {@code c} out to {@code reach}: a rune on every cell of a hexagonal grid, spikes
     * falling four at a time over two passes. Also cast by {@code /soulless ult} to practise dodging it.
     * Returns the tick (after casting) of the last spike's fall.
     */
    public static int iceRain(ServerLevel level, Vec3 c, double reach, int ownerId, net.minecraft.util.RandomSource random) {
        double spacing = IceShardEntity.SPACING;
        java.util.List<Vec3> cells = new java.util.ArrayList<>();
        double rowH = spacing * Math.sqrt(3) / 2;
        for (int row = (int) -Math.ceil(reach / rowH); row <= Math.ceil(reach / rowH); row++) {
            double off = (row & 1) == 0 ? 0 : spacing / 2;
            for (double dx = -reach - off; dx <= reach + spacing; dx += spacing) {
                double x = dx + off, z = row * rowH;
                if (x * x + z * z <= reach * reach) cells.add(new Vec3(c.x + x, c.y, c.z + z));
            }
        }
        java.util.Random rnd = new java.util.Random(random.nextLong());
        int n = cells.size();
        java.util.List<Integer> pass1 = new java.util.ArrayList<>(), pass2 = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            pass1.add(i);
            pass2.add(i);
        }
        java.util.Collections.shuffle(pass1, rnd);
        java.util.Collections.shuffle(pass2, rnd);
        int[] first = new int[n], second = new int[n];
        for (int k = 0; k < n; k++) first[pass1.get(k)] = RAIN_WARN + (k / RAIN_GROUP) * RAIN_EVERY;
        int pass2Start = RAIN_WARN + ((n + RAIN_GROUP - 1) / RAIN_GROUP) * RAIN_EVERY;
        for (int k = 0; k < n; k++) second[pass2.get(k)] = pass2Start + (k / RAIN_GROUP) * RAIN_EVERY;
        int lastFall = 0;
        for (int i = 0; i < n; i++) {
            Vec3 v = cells.get(i);
            BlockPos ground = com.soulless.ability.CrackManager.findSurface(level, BlockPos.containing(v.x, c.y + 1, v.z));
            IceShardEntity shard = new IceShardEntity(ModEntities.ICE_SHARD.get(), level);
            shard.setup(ownerId, first[i], Math.max(second[i], first[i] + 20));
            shard.moveTo(v.x, ground != null ? ground.getY() + 1 : c.y, v.z);
            level.addFreshEntity(shard);
            lastFall = Math.max(lastFall, Math.max(second[i], first[i] + 20));
        }
        return lastFall;
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
        // never above 5 blocks over the floor, and come down when the target is far below
        if (target != null && target.getY() < wantY - 3) wantY = Math.max(ground, target.getY() - 0.5);
        wantY = Math.min(wantY, ground + 5);
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
                altitudeWant = 0.8 + Math.sin(tickCount * 0.045) * 0.7;   // low enough to reach with a sword
                if (moveTicks > 45 + random.nextInt(40)) {
                    moveTicks = 0;
                    if (random.nextInt(3) == 0) orbitDir = -orbitDir;
                    if (d < 22 && random.nextInt(5) < 2) startSwoop(radial);
                }
            }
            case SWOOP_WINDUP -> {
                // rear back and up: the tell
                steer(radial.scale(-0.12), 0.2);
                altitudeWant = 2.5;
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

    /**
     * Turns the whole Reaper toward its target (the model turns with the body, so the look control alone would
     * leave it staring wherever it rose); while swooping it faces where it flies.
     */
    private void faceTarget() {
        if (target == null || state() == State.EMERGE) return;
        float want;
        float rate = 8;
        if (move == Move.SWOOP && drift.lengthSqr() > 0.01) {
            want = (float) (Mth.atan2(drift.z, drift.x) * Mth.RAD_TO_DEG) - 90;
            rate = 14;
        } else {
            want = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
        }
        float yaw = Mth.approachDegrees(getYRot(), want, rate);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void steer(Vec3 want, double rate) {
        drift = new Vec3(Mth.lerp(rate, drift.x, want.x), 0, Mth.lerp(rate, drift.z, want.z));
    }

    /**
     * The surface it hovers over. In its arena that is always the arena floor (scanning upward would take the
     * bone arches, chains and pillar capitals for ground and ratchet it up under the dome, out of reach).
     * Elsewhere: the first solid block below it.
     */
    private double groundY() {
        if (home != null) return home.getY() - 1;
        BlockPos p = blockPosition();
        for (int dy = 0; dy >= -8; dy--) {
            BlockPos q = p.offset(0, dy, 0);
            if (!level().getBlockState(q).isAir() && level().getBlockState(q.above()).isAir()) return q.getY() + 1;
        }
        return getY();
    }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        if (level().isClientSide) swingSpine();
        else {
            if (isPreview() && entityData.get(PREVIEW).equals("fly")) previewFly();
            // mid power stroke of every wing beat (32 ticks): embers shaken off the wings, a downdraft below
            if (tickCount % 32 == 12 && !isDeadOrDying()) wingGust((ServerLevel) level());
        }
    }

    // ------------------------------------------------------------------ the dangling lower spine (client)

    /** Swing of each free spine link (degrees; x: backward, z: toward the model's right), now and last tick. */
    public final float[] spineX = new float[SPINE_LINKS], spineZ = new float[SPINE_LINKS];
    public final float[] spineXo = new float[SPINE_LINKS], spineZo = new float[SPINE_LINKS];
    public static final int SPINE_LINKS = 4;
    private float spinePrevYaw = Float.NaN;

    /**
     * Follow-through on the hanging lower spine: the top link swings against the Reaper's motion (flying
     * forward drags it back, sliding sideways or turning flings it to the other side) and every link below
     * chases the one above a tick late, so a wave runs down to the tip and it keeps swaying after a stop.
     */
    private void swingSpine() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 v = new Vec3(getX() - xo, getY() - yo, getZ() - zo);
        float turn = Float.isNaN(spinePrevYaw) ? 0 : Mth.wrapDegrees(yBodyRot - spinePrevYaw);
        spinePrevYaw = yBodyRot;
        float wantX = Mth.clamp((float) (v.dot(fwd) * 110 + v.y * 40), -40, 40);
        float wantZ = Mth.clamp((float) (-v.dot(right) * 110) - turn * 1.6F, -35, 35);
        for (int i = 0; i < SPINE_LINKS; i++) {
            spineXo[i] = spineX[i];
            spineZo[i] = spineZ[i];
        }
        for (int i = 0; i < SPINE_LINKS; i++) {
            float sway = Mth.sin(tickCount * 0.09F - i * 0.9F) * 2.5F;
            float tx = (i == 0 ? wantX * 0.45F : spineXo[i - 1] * 0.9F) + sway * 0.4F;
            float tz = (i == 0 ? wantZ * 0.45F : spineZo[i - 1] * 0.9F) + sway;
            spineX[i] += (tx - spineX[i]) * 0.3F;
            spineZ[i] += (tz - spineZ[i]) * 0.3F;
        }
    }

    /** Soul embers shed along both wings with each beat. */
    private void wingGust(ServerLevel level) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 6; k++) {
                double d = 0.6 + k * 0.75;
                Vec3 p = position().add(0, 4.6 + k * 0.2, 0).add(right.scale(side * d)).subtract(fwd.scale(0.6));
                for (int n = 0; n < 2; n++) {
                    level.sendParticles(ModParticles.SOUL_EMBER.get(), p.x + random.nextGaussian() * 0.3, p.y + random.nextGaussian() * 0.3,
                            p.z + random.nextGaussian() * 0.3, 0, side * right.x * 0.05, -0.12 - random.nextDouble() * 0.08, side * right.z * 0.05, 1);
                }
            }
        }
    }

    @Nullable private Vec3 previewOrigin;

    /** {@code /soulless preview reaper fly}: the statue circles slowly, so the spine's swing can be watched. */
    private void previewFly() {
        if (previewOrigin == null) previewOrigin = position();
        double a = tickCount * 0.045;
        double x = previewOrigin.x + Math.cos(a) * 6, z = previewOrigin.z + Math.sin(a) * 6;
        double y = previewOrigin.y + 0.5 + Math.sin(tickCount * 0.1) * 0.4;
        float facing = (float) (Mth.atan2(Math.cos(a), -Math.sin(a)) * Mth.RAD_TO_DEG) - 90;
        moveTo(x, y, z, facing, 0);
        setYBodyRot(facing);
        setYHeadRot(facing);
    }

    /** The target's recent positions (one per tick), to read where it is heading. */
    private final java.util.ArrayDeque<Vec3> targetTrail = new java.util.ArrayDeque<>();

    private void trackTarget() {
        if (target == null) {
            targetTrail.clear();
            return;
        }
        targetTrail.addLast(target.position());
        while (targetTrail.size() > 6) targetTrail.removeFirst();
    }

    /** {@code blocks} ahead of the target along its movement (on the ground), or right under it if it stands still. */
    private Vec3 leadTarget(double blocks) {
        Vec3 now = target.position();
        Vec3 then = targetTrail.isEmpty() ? now : targetTrail.peekFirst();
        Vec3 move = new Vec3(now.x - then.x, 0, now.z - then.z);
        Vec3 at = move.length() > 0.25 ? now.add(move.normalize().scale(blocks)) : now;
        BlockPos ground = com.soulless.ability.CrackManager.findSurface((ServerLevel) level(), BlockPos.containing(at.x, now.y, at.z));
        return new Vec3(at.x, ground != null ? ground.getY() + 1 : now.y, at.z);
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
            chest.setItem(13, new ItemStack(ModItems.REAPER_SOUL.get()));
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
    private static final RawAnimation ULT = RawAnimation.begin().thenPlay("animation.wailing_reaper.ult")
            .thenLoop("animation.wailing_reaper.ult_hold");
    private static final RawAnimation STUNNED = RawAnimation.begin().thenLoop("animation.wailing_reaper.stunned");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlayAndHold("animation.wailing_reaper.emerge");
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
                if (anim.equals("idle") || anim.equals("stunned") || anim.equals("fly")) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.wailing_reaper." + (anim.equals("fly") ? "idle" : anim)));
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
