package com.soulless.boss;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Leviathan of the Abyss, the boss of the City of Drowned Souls: a 40-block eel crowned with tentacles.
 * <ul>
 *   <li>it keeps to open water - every place it heads for has water all round it, it looks ahead and turns away
 *       from walls and the sea floor, and bubble columns do not drag it;</li>
 *   <li>alone, it roams in slow wide loops round its home. A player in the water nearby is hunted, in a cycle:
 *       it circles them (roaring now and then - the roar slows and darkens everyone near), coils, charges through
 *       them biting (its body rams whoever it passes), and swims off to come round again;</li>
 *   <li>every third time round it rears up like a dragon instead - body upright, tail writhing below, crown spread -
 *       and breathes at its prey: bubbles first, then a beam of violet breath that follows them, a little behind;</li>
 *   <li>twice a fight (at two thirds and one third of its health) it takes its prey from below: it speeds up and
 *       circles them, dives under them, rises at them with its jaw wide - this cannot be dodged - seizes them,
 *       shakes them in its jaws, and only then bites down and flings them away;</li>
 *   <li>its body is hit along its whole length: a head part and twelve body parts (LeviathanPart) laid along the
 *       path its head has swum, as a serpent's body follows its head.</li>
 * </ul>
 * The model's motion is client.LeviathanModel.
 */
public class LeviathanEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.leviathan.idle");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.leviathan.bite");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.leviathan.roar");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenLoop("animation.leviathan.grab_lunge");
    private static final RawAnimation THRASH = RawAnimation.begin().thenLoop("animation.leviathan.grab_thrash");
    private static final RawAnimation BREATH = RawAnimation.begin().thenLoop("animation.leviathan.breath");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.leviathan.death");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The pose the fight shows (0 none, 1 rising at its prey with the jaw wide, 2 shaking it, 3 the dragon's breath,
     * 4 dying). */
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(LeviathanEntity.class, EntityDataSerializers.INT);

    private enum Phase { ROAM, CIRCLE, COIL, CHARGE, TURN, TAIL, RECOVER, EXHAUSTED, REAR, BREATHE, GRAB_RUSH, GRAB_DIVE, GRAB_RISE, GRAB_HOLD }

    /*
     * The fight in three stages (by health), each telegraphed attack warned 1-2 s ahead, and a window to strike back
     * after each great one:
     *   I   (100-60%): the charge (a coil and a roar first) and the tail swipe for whoever lingers behind it; it never
     *                  rests, short circles between.
     *  II   (60-25%):  the grab opens it; faster; charges come two in a row; the breath; whirlpools under its prey
     *                  (a ring of bubbles marks them); drowned rise to fight beside it.
     *  III  (<25%):    the second grab opens it; frenzy - faster still, three charges in a row, its body hurts on any
     *                  touch, whirlpools and breath come often.
     * Windows: after the breath, a grab or a run of charges it is spent (EXHAUSTED) for 2-3 s, slow and sinking, and
     * takes more damage.
     */

    /** Balanced for after the Reaper: its prey wields the Soul Forged and wears diamond or netherite (on full
     * netherite with Protection IV a bite still takes two hearts, the grab four). */
    private static final float BITE_DAMAGE = 17, RAM_DAMAGE = 7, GRAB_DAMAGE = 22, BREATH_DAMAGE = 4, TAIL_DAMAGE = 10, VORTEX_DAMAGE = 6;
    /** Spent, it takes this much more damage. */
    private static final float EXHAUSTED_TAKEN = 1.35F;
    /** The breath: how long the bubbles come first (the warning - the aim follows its prey closely), how long it
     * lasts in all, how far it reaches (ticks, blocks); the beam then turns at most BREATH_TURN degrees a tick, so a
     * player swimming across it gets out of it, and blocks stop it - a pillar is cover. */
    private static final int BUBBLES = 36, BREATH_TICKS = 126, BREATH_RANGE = 30;
    private static final float BREATH_TURN = 0.9F;
    /** How long it takes to die (the death animation's length), ticks. */
    private static final int DEATH_TICKS = 110;
    private final ServerBossEvent bar = new ServerBossEvent(net.minecraft.network.chat.Component.translatable("entity.soulless.leviathan"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Phase phase = Phase.ROAM;
    private int phaseTicks, grabsDone, ramCooldown, cycles, chargesLeft, tailCooldown = 60, vortexCooldown = 100, vortexTicks, stage = 1;
    @Nullable private Vec3 vortexAt;
    private int floorTicks;
    private Vec3 chargeAim, breathAim;
    @Nullable private ServerPlayer held;

    /** Body segments: how far behind the neck each one's centre lies, and its size (blocks). */
    private static final int SEGMENTS = 12;
    private static final float SEG_LEN = 2.75F;
    private final LeviathanPart head;
    private final LeviathanPart[] body = new LeviathanPart[SEGMENTS];
    private final LeviathanPart[] parts;
    /** Where the neck has been, newest first, a point every tick (the body is laid along it). */
    private final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();
    private Player prey;
    private float circleAngle, circleDir = 1;
    private int roarCooldown = 200;

    @Nullable private BlockPos home;
    /** Where it is swimming to, and how long until it picks somewhere else. */
    private Vec3 goal;
    private int goalTicks;
    /** Client: how fast it is turning (degrees per tick), smoothed - the body bends into its turns. */
    public float turn, turnO;
    /** Client: the whiskers' inertia - per whisker and link, its lag (pitch, yaw) and how fast that changes; the head's
     * last pose and when it was seen (client.LeviathanModel). */
    public final float[][] whisker = new float[6][16];
    public float lastHeadYaw = Float.NaN, lastHeadPitch, lastWhiskerT;
    /** Client: each body bone's bend (pitch, yaw), eased toward the path's so the body never snaps. */
    public final float[] bend = new float[24];
    public float bendT = Float.NaN;
    /** Client: the swimming wave's phase (runs faster the faster it swims), its smoothed speed, and the head's gaze. */
    public float swimPhase, swimSpeed, lastLiveT = Float.NaN, gazeYaw, gazePitch, roarStart = Float.NaN;

    public LeviathanEntity(EntityType<? extends LeviathanEntity> type, Level level) {
        super(type, level);
        noCulling = true;
        setNoGravity(true);
        xpReward = 500;
        head = new LeviathanPart(this, "head", 3.2F, 2.6F);
        for (int i = 0; i < SEGMENTS; i++) {
            float f = i / (SEGMENTS - 1F);
            float w = (46 - f * 34) / 16F, h = (40 - f * 30) / 16F;
            float sway = 0.6F + 1.4F * f;   // wider than the body: the boxes cover the animation's sway round the path
            body[i] = new LeviathanPart(this, "body_" + i, Math.max(w, h) + sway, h + sway * 0.5F);
        }
        parts = new LeviathanPart[SEGMENTS + 1];
        parts[0] = head;
        System.arraycopy(body, 0, parts, 1, SEGMENTS);
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    /** The body parts, neck to tail (client.LeviathanModel bends the model along them). */
    public LeviathanPart[] bodyParts() {
        return body;
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.minecraftforge.entity.PartEntity<?>[] getParts() {
        return parts;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1400).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, 10);
    }

    @Override
    protected void registerGoals() {}

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSE, 0);
    }

    /** The pose the fight shows (client.LeviathanModel: 3 is the dragon's breath - the head looks far up or down). */
    public int fightPose() {
        return entityData.get(POSE);
    }

    @Override
    public void tick() {
        super.tick();
        if (home == null) home = blockPosition();
        setNoGravity(isInWater());
        if (!level().isClientSide && !isDeadOrDying()) {
            swim();
            bar.setProgress(getHealth() / getMaxHealth());
            if (tickCount % 10 == 0) updateBar();
        }
        layBody();
        if (!level().isClientSide && !isDeadOrDying()) attackWithBody();
        if (level().isClientSide) {
            // the body always faces where it swims (no mob body lag on the client)
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            turnO = turn;
            turn = Mth.lerp(0.15F, turn, Mth.wrapDegrees(getYRot() - yRotO));
        }
    }

    // ------------------------------------------------------------------ swimming

    /** Open water round a point: the point and the six points this far from it are all water. */
    private boolean openWater(Vec3 p, double r) {
        for (double[] o : new double[][]{{0, 0, 0}, {r, 0, 0}, {-r, 0, 0}, {0, r, 0}, {0, -r, 0}, {0, 0, r}, {0, 0, -r}}) {
            BlockPos b = BlockPos.containing(p.x + o[0], p.y + o[1], p.z + o[2]);
            if (!level().getFluidState(b).is(net.minecraft.tags.FluidTags.WATER)) return false;
        }
        return true;
    }

    /** A place to swim to in open water round {@code around}: tries a few, keeps the first with water all round. */
    @Nullable
    private Vec3 pickGoal(Vec3 around, double rMin, double rMax, double up, double spread) {
        for (int n = 0; n < 12; n++) {
            double a = random.nextDouble() * Math.PI * 2, r = rMin + random.nextDouble() * (rMax - rMin);
            Vec3 p = around.add(Math.cos(a) * r, up + (random.nextDouble() - 0.5) * spread, Math.sin(a) * r);
            if (openWater(p, 3)) return p;
        }
        return null;
    }

    private void swim() {
        if (!isInWater()) {   // stranded: thrash and slide back toward the water, it cannot swim on land
            setDeltaMovement(getDeltaMovement().multiply(0.9, 1, 0.9).add(0, -0.06, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        hunt();
        boolean free = floorHunt() || phase == Phase.GRAB_RISE || phase == Phase.GRAB_HOLD || phase == Phase.CHARGE
                || phase == Phase.REAR || phase == Phase.BREATHE || phase == Phase.EXHAUSTED;   // nothing turns it aside
        boolean blocked = !free && !openWater(position().add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(6)), 2);
        if (phase == Phase.ROAM && (goal == null || blocked || --goalTicks <= 0 || position().distanceToSqr(goal) < 16)) {
            Vec3 g = pickGoal(home == null ? position() : Vec3.atCenterOf(home), 8, 26, 6, 10);
            if (g == null) g = pickGoal(position(), 4, 14, 4, 8);   // home is no good: anywhere open close by
            goal = g != null ? g : position().add(0, 4, 0);
            goalTicks = 240;
        }
        if (blocked && phase != Phase.ROAM) {   // a wall ahead: swing round toward open water, then carry on
            Vec3 g = pickGoal(position(), 6, 14, 0, 8);
            if (g != null) goal = g;
        }
        if (goal == null) goal = position();
        float speed = switch (phase) {
            case ROAM -> 0.28F;
            case CIRCLE -> 0.45F;
            case COIL -> 0.12F;
            case CHARGE -> 0.95F;
            case TURN -> 0.55F;
            case TAIL -> 0.3F;
            case RECOVER -> 0.5F;
            case EXHAUSTED -> 0.06F;
            case REAR -> 0.4F;
            case BREATHE -> 0.02F;
            case GRAB_RUSH -> 0.8F;
            case GRAB_DIVE -> 0.85F;
            case GRAB_RISE -> 1.15F;
            case GRAB_HOLD -> 0.35F;
        };
        speed *= tempo();
        float turnRate = switch (phase) {
            case COIL -> 6F;
            case TURN -> 9F;
            case TAIL -> phaseTicks > TAIL_WARN ? 16F : 3F;
            case EXHAUSTED -> 0.8F;
            case GRAB_RISE -> 14F;
            case CHARGE -> 1.5F;
            case REAR -> 5F;
            case BREATHE -> 1.2F;
            case GRAB_RUSH, GRAB_DIVE -> 4F;
            default -> blocked ? 4F : 2.6F;
        };
        Vec3 to = goal.subtract(position());
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        float wantPitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        // the sea floor close below: lift (unless it is going for its prey anyway)
        if (!free && !level().getFluidState(blockPosition().below(3)).is(net.minecraft.tags.FluidTags.WATER)) wantPitch = Math.min(wantPitch, -18);
        float maxPitch = phase == Phase.GRAB_RISE || phase == Phase.GRAB_DIVE || phase == Phase.REAR || phase == Phase.BREATHE ? 80 : 35;
        setYRot(Mth.approachDegrees(getYRot(), wantYaw, phase == Phase.BREATHE ? 3F : turnRate));
        setXRot(Mth.approach(getXRot(), Mth.clamp(wantPitch, -maxPitch, maxPitch), turnRate * 0.7F + (phase == Phase.REAR ? 2 : 0)));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot()).scale(speed);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(dir.scale(0.4)));
        move(MoverType.SELF, getDeltaMovement());
    }

    // ------------------------------------------------------------------ the fight

    /** Where its jaws close: just before the head. */
    public Vec3 mouth() {
        return head.position().add(0, head.getBbHeight() / 2, 0).add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(1.6));
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
        entityData.set(POSE, p == Phase.GRAB_RISE ? 1 : p == Phase.GRAB_HOLD ? 2 : p == Phase.REAR || p == Phase.BREATHE ? 3 : 0);
    }

    private boolean huntable(@Nullable Player p) {
        return p != null && p.isAlive() && !p.isSpectator() && !p.isCreative() && p.isInWater() && p.distanceToSqr(this) < 56 * 56;
    }

    /** The fight's cycle; sets where it swims to ({@code goal}). */
    private void hunt() {
        phaseTicks++;
        if (ramCooldown > 0) ramCooldown--;
        if (phase == Phase.GRAB_HOLD) {
            hold();
            return;
        }
        boolean grabbing = phase == Phase.GRAB_RUSH || phase == Phase.GRAB_DIVE || phase == Phase.GRAB_RISE;
        // a grab, once begun, follows its prey anywhere; otherwise only into the water
        if (grabbing ? prey == null || !prey.isAlive() || prey.isSpectator() : !huntable(prey)) {
            Player next = level().getNearestPlayer(this, 40);
            prey = huntable(next) ? next : null;
            if (prey == null) {
                if (phase != Phase.ROAM) setPhase(Phase.ROAM);
                return;
            }
            if (phase == Phase.ROAM) setPhase(Phase.CIRCLE);
        }
        Vec3 at = prey.position().add(0, prey.getBbHeight() / 2, 0);
        float hp = getHealth() / getMaxHealth();
        int nowStage = hp > 0.6F ? 1 : hp > 0.25F ? 2 : 3;
        if (nowStage > stage) enterStage(nowStage);
        if (tailCooldown > 0) tailCooldown--;
        if (vortexCooldown > 0) vortexCooldown--;
        tickVortex();
        // staying on the sea floor out of its way does not work: after a few seconds there a whirlpool comes for them
        floorTicks = onFloor(prey) ? floorTicks + 1 : 0;
        if (floorTicks > 70 && vortexAt == null && !grabbing) {
            startVortex(at);
            floorTicks = 0;
        }
        // twice a fight, opening stages II and III: the grab from below (it waits for a free moment to begin it)
        boolean free = phase == Phase.CIRCLE || phase == Phase.RECOVER || phase == Phase.TURN || phase == Phase.EXHAUSTED;
        if (!grabbing && free && grabsDone < stage - 1) {
            grabsDone++;
            setPhase(Phase.GRAB_RUSH);
            roar();
            level().playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 4F, 0.5F);
        }
        switch (phase) {
            case CIRCLE -> {
                // short: a few seconds to pick its angle, never a rest
                circle(at, 12 + 3 * Math.sin(tickCount * 0.02), 0.02F * tempo());
                if (--roarCooldown <= 0 && distanceToSqr(prey) < 24 * 24) {
                    roar();
                    roarCooldown = 300 + random.nextInt(200);
                }
                if (tailCooldown == 0 && nearTail(prey)) {
                    setPhase(Phase.TAIL);
                    break;
                }
                if (stage >= 2 && vortexCooldown == 0 && vortexAt == null) startVortex(at);
                int circleFor = stage == 1 ? 50 : stage == 2 ? 36 : 22;
                if (phaseTicks > circleFor + random.nextInt(20)) {
                    // the breath: from stage II, every other time round (every time in III), if there is room above
                    boolean room = openWater(position().add(0, 10, 0), 2) && openWater(position().add(0, 18, 0), 2);
                    cycles++;
                    if (stage >= 2 && room && (stage == 3 ? cycles % 2 == 0 : cycles % 3 == 0)) setPhase(Phase.REAR);
                    else {
                        chargesLeft = stage;   // I: one charge, II: two in a row, III: three
                        setPhase(Phase.COIL);
                    }
                }
            }
            case COIL -> {   // the warning: it slows, swings its head round onto its prey and roars, bubbles at its jaws
                goal = at;
                if (phaseTicks == 1) level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3F, 0.5F);
                if (level() instanceof ServerLevel sl) {
                    Vec3 m = mouth();
                    sl.sendParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 4, 0.6, 0.6, 0.6, 0.1);
                }
                int warn = chargesLeft < stage ? 10 : stage == 3 ? 16 : 22;   // the first of a run warns longest
                if (phaseTicks >= warn) {
                    chargeAim = onFloor(prey) ? at : at.add(prey.getDeltaMovement().scale(10));
                    setPhase(Phase.CHARGE);
                    level().playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.5F, 0.8F);
                }
            }
            case TURN -> {   // a tight turn back onto its prey, then the next charge of the run
                goal = at;
                Vec3 to = at.subtract(position()).normalize();
                if (phaseTicks > 30 || Vec3.directionFromRotation(getXRot(), getYRot()).dot(to) > 0.8) setPhase(Phase.COIL);
            }
            case TAIL -> {
                // the warning: the tail curls aside, a hiss and a stir of the water at it; then it whips round
                LeviathanPart tip = body[SEGMENTS - 1];
                if (phaseTicks == 1) level().playSound(null, tip.blockPosition(), SoundEvents.ELDER_GUARDIAN_FLOP, SoundSource.HOSTILE, 4F, 0.5F);
                if (phaseTicks <= TAIL_WARN) {
                    if (level() instanceof ServerLevel sl) {
                        for (int i = SEGMENTS - 4; i < SEGMENTS; i++) {
                            LeviathanPart b = body[i];
                            sl.sendParticles(ParticleTypes.BUBBLE_POP, b.getX(), b.getY() + 1, b.getZ(), 3, 0.8, 0.8, 0.8, 0.05);
                        }
                    }
                    goal = position().add(Vec3.directionFromRotation(0, getYRot()).scale(4));
                } else {
                    // the whip: the head swings hard to one side, so the tail sweeps the other way through its prey
                    Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                    Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                    Vec3 rel = prey.position().subtract(position());
                    goal = position().add(side.scale(rel.dot(side) > 0 ? -8 : 8)).add(fwd.scale(2));
                    if (phaseTicks == TAIL_WARN + 1) level().playSound(null, tip.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 4F, 0.4F);
                    tailHit();
                }
                if (phaseTicks > TAIL_WARN + 16) {
                    tailCooldown = stage == 3 ? 60 : 110;
                    setPhase(Phase.CIRCLE);
                }
            }
            case CHARGE -> {
                // straight through where its prey was, a little past
                Vec3 fwd = Vec3.directionFromRotation(getXRot(), getYRot());
                goal = chargeAim.add(fwd.scale(12));
                // at prey on the floor it skims along the bottom rather than ploughing into it
                if (onFloor(prey)) goal = new Vec3(goal.x, chargeAim.y + 0.5, goal.z);
                double d = mouth().distanceToSqr(at);
                if (d < 7 * 7 && phaseTicks % 20 == 1) triggerAnim("roar", "bite");
                if (d < 3.2 * 3.2 && prey.hurtTime == 0) {
                    prey.hurt(damageSources().mobAttack(this), BITE_DAMAGE);
                    prey.knockback(1.2, -fwd.x, -fwd.z);
                    level().playSound(null, prey.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2F, 0.6F);
                }
                if (phaseTicks > 45 || position().subtract(chargeAim).dot(fwd) > 10) {
                    if (--chargesLeft > 0) setPhase(Phase.TURN);
                    else if (stage >= 2) setPhase(Phase.EXHAUSTED);   // a run of charges spends it
                    else {
                        circleDir = random.nextBoolean() ? 1 : -1;
                        circleAngle = (float) Math.atan2(getZ() - at.z, getX() - at.x);
                        setPhase(Phase.CIRCLE);
                    }
                }
            }
            case EXHAUSTED -> {   // spent: it drifts, sinking a little, gills streaming - the moment to strike
                goal = position().add(Vec3.directionFromRotation(0, getYRot()).scale(2)).add(0, -1, 0);
                if (level() instanceof ServerLevel sl && phaseTicks % 3 == 0) {
                    Vec3 h = head.position();
                    sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, h.x, h.y + 1, h.z, 5, 1.2, 0.6, 1.2, 0.02);
                }
                if (phaseTicks == 1) level().playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.HOSTILE, 3F, 0.4F);
                if (phaseTicks > (stage == 3 ? 45 : 60)) {
                    circleDir = random.nextBoolean() ? 1 : -1;
                    circleAngle = (float) Math.atan2(getZ() - at.z, getX() - at.x);
                    setPhase(Phase.CIRCLE);
                }
            }
            case RECOVER -> {
                if (goal == null || phaseTicks > 50 || position().distanceToSqr(goal) < 16) {
                    circleDir = random.nextBoolean() ? 1 : -1;
                    circleAngle = (float) Math.atan2(getZ() - at.z, getX() - at.x);
                    setPhase(Phase.CIRCLE);
                }
            }
            case REAR -> {   // straight up, so its body hangs below it
                Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ()).normalize();
                goal = position().add(flat.scale(1.5)).add(0, 12, 0);
                if (phaseTicks >= 26) {
                    breathAim = at;
                    setPhase(Phase.BREATHE);
                    level().playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5F, 0.4F);
                }
            }
            case BREATHE -> {
                // upright, turned toward its prey; the breath's aim follows them, a little behind
                Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ()).normalize();
                goal = position().add(flat.scale(1.5)).add(0, 12, 0);
                if (breathAim == null) breathAim = at;   // a breath begun without its aim (a world loaded mid-breath)
                if (phaseTicks < BUBBLES) breathAim = breathAim.lerp(at, 0.3);
                else breathAim = turnToward(mouth(), breathAim, at, BREATH_TURN);
                breathe(phaseTicks >= BUBBLES);
                if (phaseTicks >= BREATH_TICKS) setPhase(Phase.EXHAUSTED);
            }
            case GRAB_RUSH -> {   // faster and tighter round its prey
                circle(at, 10, 0.05F);
                if (phaseTicks > 60) setPhase(Phase.GRAB_DIVE);
            }
            case GRAB_DIVE -> {   // under it
                Vec3 below = null;
                for (int depth = 16; depth >= 6 && below == null; depth -= 2) {
                    Vec3 p = at.add(0, -depth, 0);
                    if (openWater(p, 2)) below = p;
                }
                goal = below != null ? below : at.add(0, -6, 0);
                if (position().distanceToSqr(goal) < 5 * 5 || phaseTicks > 70) {
                    setPhase(Phase.GRAB_RISE);
                    level().playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3F, 0.6F);
                }
            }
            case GRAB_RISE -> {   // up at it, homing: it cannot be dodged
                goal = at;
                if (mouth().distanceToSqr(at) < 3.5 * 3.5 || phaseTicks > 34) seize();
            }
            default -> {}
        }
    }

    /** How long the tail swipe warns before it whips (ticks). */
    private static final int TAIL_WARN = 18;

    /** Standing on the sea floor (or anything solid right under them). */
    private boolean onFloor(@Nullable Player p) {
        return p != null && (p.onGround() || !level().getFluidState(p.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER));
    }

    /** Going for prey on the floor: it may swim down to the bottom (the lift off the floor is off). */
    private boolean floorHunt() {
        return onFloor(prey) && (phase == Phase.COIL || phase == Phase.CHARGE || phase == Phase.TURN);
    }

    /** How fast it moves in this stage. */
    private float tempo() {
        return stage == 1 ? 1F : stage == 2 ? 1.15F : 1.3F;
    }

    /** A new stage: a roar, the bar's colour, and from II drowned rising to fight beside it. */
    private void enterStage(int s) {
        stage = s;
        bar.setColor(s == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        roar();
        level().playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 5F, 0.4F);
        if (level() instanceof ServerLevel sl && prey != null) {
            for (int i = 0; i < (s == 2 ? 2 : 3); i++) {
                var d = EntityType.DROWNED.create(sl);
                if (d == null) continue;
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 p = prey.position().add(Math.cos(a) * 7, -2, Math.sin(a) * 7);
                if (!openWater(p, 1)) p = prey.position().add(0, -2, 0);
                d.moveTo(p.x, p.y, p.z, random.nextFloat() * 360, 0);
                d.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
                d.setTarget(prey);
                sl.addFreshEntity(d);
                sl.sendParticles(ParticleTypes.SOUL, p.x, p.y + 1, p.z, 12, 0.5, 0.8, 0.5, 0.02);
            }
        }
    }

    /** Its prey is near its tail (the back third of the body) and not before its head. */
    private boolean nearTail(Player p) {
        for (int i = SEGMENTS * 2 / 3; i < SEGMENTS; i++) if (body[i].distanceToSqr(p) < 6 * 6) return mouth().distanceToSqr(p.position()) > 8 * 8;
        return false;
    }

    /** The tail's whip: whoever the last segments sweep through is struck hard and thrown. */
    private void tailHit() {
        for (int i = SEGMENTS - 4; i < SEGMENTS; i++) {
            LeviathanPart b = body[i];
            for (Player p : level().getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(1.5))) {
                if (p.isSpectator() || p.isCreative() || p.hurtTime > 0) continue;
                if (p.hurt(damageSources().mobAttack(this), TAIL_DAMAGE)) {
                    Vec3 away = p.position().subtract(b.position()).normalize();
                    p.setDeltaMovement(away.scale(1.6).add(0, 0.4, 0));
                    p.hurtMarked = true;
                }
            }
        }
    }

    /** A whirlpool under its prey: a ring of bubbles marks it for two seconds, then it pulls in and down and hurts. */
    private void startVortex(Vec3 at) {
        BlockPos below = BlockPos.containing(at);
        for (int i = 0; i < 6 && level().getFluidState(below.below()).is(net.minecraft.tags.FluidTags.WATER); i++) below = below.below();
        vortexAt = Vec3.atBottomCenterOf(below);
        vortexTicks = 40;
        level().playSound(null, below, SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 3F, 0.5F);
    }

    private void tickVortex() {
        if (vortexAt == null || !(level() instanceof ServerLevel sl)) return;
        vortexTicks--;
        double r = 3.5;
        if (vortexTicks > 0) {
            // the warning ring, closing in and rising as it nears
            double k = vortexTicks / 40.0;
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2 + vortexTicks * 0.2;
                sl.sendParticles(ParticleTypes.BUBBLE, vortexAt.x + Math.cos(a) * r, vortexAt.y + 0.3 + (1 - k) * 3, vortexAt.z + Math.sin(a) * r, 1, 0, 0.2, 0, 0);
            }
            if (vortexTicks % 4 == 0) sl.sendParticles(ParticleTypes.SCULK_SOUL, vortexAt.x, vortexAt.y + 0.5, vortexAt.z, 2, r * 0.5, 0.2, r * 0.5, 0.01);
            return;
        }
        // the pull
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, vortexAt.x, vortexAt.y + 1, vortexAt.z, 60, r * 0.6, 2, r * 0.6, 0.2);
        level().playSound(null, BlockPos.containing(vortexAt), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2F, 0.6F);
        for (Player p : level().getEntitiesOfClass(Player.class, new AABB(vortexAt, vortexAt).inflate(r, 6, r))) {
            if (p.isSpectator() || p.isCreative()) continue;
            Vec3 in = vortexAt.subtract(p.position());
            if (in.horizontalDistanceSqr() > r * r) continue;
            p.invulnerableTime = 0;
            p.hurt(damageSources().mobAttack(this), VORTEX_DAMAGE);
            p.setDeltaMovement(in.normalize().scale(0.6).add(0, -0.8, 0));
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        }
        vortexAt = null;
        vortexCooldown = stage == 3 ? 120 : 200;
    }

    /** Spent (after a great attack), it takes more damage - the window to strike. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return super.hurt(source, phase == Phase.EXHAUSTED ? amount * EXHAUSTED_TAKEN : amount);
    }

    private void circle(Vec3 at, double r, float rate) {
        circleAngle += rate * circleDir;
        Vec3 c = at.add(Math.cos(circleAngle) * r, 2 + 2 * Math.sin(tickCount * 0.03), Math.sin(circleAngle) * r);
        goal = openWater(c, 2) ? c : goal != null ? goal : c;
    }

    /** The aim turned from {@code aim} toward {@code to} (as seen from {@code from}) by at most {@code deg} degrees. */
    private static Vec3 turnToward(Vec3 from, Vec3 aim, Vec3 to, float deg) {
        Vec3 a = aim.subtract(from), b = to.subtract(from);
        double la = a.length(), lb = b.length();
        if (la < 1e-4 || lb < 1e-4) return to;
        Vec3 na = a.scale(1 / la), nb = b.scale(1 / lb);
        double angle = Math.acos(Mth.clamp(na.dot(nb), -1, 1));
        double max = deg * Mth.DEG_TO_RAD;
        if (angle <= max) return to;
        // slerp by max/angle
        double f = max / angle, s0 = Math.sin((1 - f) * angle) / Math.sin(angle), s1 = Math.sin(f * angle) / Math.sin(angle);
        return from.add(na.scale(s0).add(nb.scale(s1)).scale(lb));
    }

    /** From its mouth toward {@code breathAim}: first a stream of bubbles, then the violet breath, which burns. */
    private void breathe(boolean fire) {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 from = mouth();
        if (breathAim == null) return;
        Vec3 dir = breathAim.subtract(from);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        double reach = fire ? BREATH_RANGE : 8;
        // blocks stop it
        var hit = level().clip(new net.minecraft.world.level.ClipContext(from, from.add(dir.scale(reach)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) reach = hit.getLocation().distanceTo(from);
        for (double d = 1; d < reach; d += fire ? 0.8 : 1.2) {
            Vec3 p = from.add(dir.scale(d));
            double spread = 0.12 + d * 0.025;
            if (fire) {
                sl.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 2, spread, spread, spread, 0.01);
                if (random.nextInt(3) == 0) sl.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 1, spread, spread, spread, 0);
            } else {
                sl.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 3, spread, spread, spread, 0.15);
            }
        }
        if (!fire) {
            if (phaseTicks % 6 == 0) level().playSound(null, BlockPos.containing(from), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 2F, 0.6F);
            return;
        }
        if (phaseTicks % 8 == 0) level().playSound(null, BlockPos.containing(from), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 3F, 0.5F);
        if (phaseTicks % 10 != 0) return;
        // whoever is in the beam: close to the line from the mouth, within its reach
        for (Player p : level().getEntitiesOfClass(Player.class, new AABB(from, from).inflate(BREATH_RANGE))) {
            if (p.isSpectator() || p.isCreative()) continue;
            Vec3 c = p.position().add(0, p.getBbHeight() / 2, 0).subtract(from);
            double along = c.dot(dir);
            if (along < 0 || along > reach) continue;
            double off = c.subtract(dir.scale(along)).length();
            if (off < 1.1 + along * 0.03) {
                p.invulnerableTime = 0;
                p.hurt(damageSources().dragonBreath(), BREATH_DAMAGE);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 1));
            }
        }
    }

    private void seize() {
        if (!(prey instanceof ServerPlayer sp)) {
            setPhase(Phase.RECOVER);
            return;
        }
        held = sp;
        setPhase(Phase.GRAB_HOLD);
        level().playSound(null, sp.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 3F, 0.4F);
        level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.HOSTILE, 2F, 0.8F);
    }

    /** It holds its prey in its jaws, shaking it, swimming on - then bites down and lets it go, still flying. */
    private void hold() {
        if (held == null || !held.isAlive() || held.isSpectator() || held.level() != level()) {
            held = null;
            setPhase(Phase.RECOVER);
            return;
        }
        Vec3 fwd = Vec3.directionFromRotation(getXRot(), getYRot());
        // swims on upward and onward, weaving (the thrash on the client wrenches the head)
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x).normalize();
        goal = position().add(fwd.scale(10)).add(side.scale(Math.sin(phaseTicks * 0.13) * 6)).add(0, 2, 0);
        if (!openWater(goal, 2)) {
            Vec3 g = pickGoal(position(), 6, 12, 2, 6);
            if (g != null) goal = g;
        }
        Vec3 m = mouth();
        held.teleportTo(m.x, m.y - held.getBbHeight() / 2, m.z);
        held.setDeltaMovement(Vec3.ZERO);
        held.fallDistance = 0;
        if (phaseTicks % 10 == 0) level().playSound(null, held.blockPosition(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.HOSTILE, 1.5F, 0.7F + random.nextFloat() * 0.3F);
        if (phaseTicks >= 50) {
            ServerPlayer p = held;
            held = null;
            p.invulnerableTime = 0;
            p.hurt(damageSources().mobAttack(this), GRAB_DAMAGE);
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
            Vec3 fling = fwd.scale(1.6).add(side.scale(Math.signum(Math.sin(phaseTicks * 0.13)) * 0.6)).add(0, 0.6, 0);
            p.setDeltaMovement(fling);
            p.hurtMarked = true;
            level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2F, 0.5F);
            setPhase(Phase.EXHAUSTED);
        }
    }

    /** Whoever its body passes through while it charges or rises is rammed. */
    private void attackWithBody() {
        boolean frenzy = stage == 3 && phase != Phase.EXHAUSTED && phase != Phase.ROAM;
        if (ramCooldown > 0 || !(frenzy || phase == Phase.CHARGE || phase == Phase.GRAB_RISE || phase == Phase.GRAB_RUSH)) return;
        for (LeviathanPart part : parts) {
            for (Player p : level().getEntitiesOfClass(Player.class, part.getBoundingBox().inflate(0.3))) {
                if (p == held || p.isSpectator() || p.isCreative()) continue;
                if (p.hurt(damageSources().mobAttack(this), RAM_DAMAGE)) {
                    Vec3 away = p.position().subtract(part.position()).normalize();
                    p.setDeltaMovement(p.getDeltaMovement().add(away.scale(0.9)));
                    p.hurtMarked = true;
                    ramCooldown = 10;
                }
            }
        }
    }

    /** The roar: everyone near is slowed and the water goes dark round them. */
    public void roar() {
        triggerAnim("roar", "roar");
        if (level().isClientSide) return;
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5F, 0.5F);
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(24))) {
            if (p.isSpectator() || p.isCreative()) continue;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0));
        }
    }

    private void updateBar() {
        for (ServerPlayer p : new java.util.ArrayList<>(bar.getPlayers())) {
            if (!p.isAlive() || p.level() != level() || p.distanceToSqr(this) > 72 * 72) bar.removePlayer(p);
        }
        for (Player p : level().players()) {
            if (p instanceof ServerPlayer sp && p.distanceToSqr(this) < 56 * 56 && (phase != Phase.ROAM || p.isInWater())) bar.addPlayer(sp);
        }
    }

    // ------------------------------------------------------------------ death

    /** Dying: a last convulsion, then it goes slack, rolls over and sinks (the death animation), and is gone. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime == 1) {
            entityData.set(POSE, 4);
            bar.removeAllPlayers();
            if (held != null) held = null;
            level().playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_DEATH, SoundSource.HOSTILE, 5F, 0.4F);
            level().playSound(null, blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 4F, 0.5F);
        }
        // the body drifts down slowly
        setDeltaMovement(getDeltaMovement().scale(0.8).add(0, deathTime > 30 ? -0.025 : 0, 0));
        move(MoverType.SELF, getDeltaMovement());
        if (level() instanceof ServerLevel sl && deathTime > 20 && deathTime % 3 == 0) {
            LeviathanPart part = parts[random.nextInt(parts.length)];
            sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, part.getX(), part.getY() + 1, part.getZ(), 6, 1, 1, 1, 0.05);
            sl.sendParticles(ParticleTypes.SOUL, part.getX(), part.getY() + 1, part.getZ(), 2, 1, 1, 1, 0.02);
        }
        if (deathTime >= DEATH_TICKS && !level().isClientSide()) {
            if (level() instanceof ServerLevel sl) {
                for (LeviathanPart part : parts) sl.sendParticles(ParticleTypes.SCULK_SOUL, part.getX(), part.getY() + 1, part.getZ(), 8, 1.2, 1.2, 1.2, 0.04);
            }
            level().broadcastEntityEvent(this, (byte) 60);
            if (level() instanceof ServerLevel sl) dropChest(sl);
            remove(RemovalReason.KILLED);
        }
    }

    /** Its spoils: a chest in the middle of the arena's floor (or where it died, out of an arena). */
    private void dropChest(ServerLevel sl) {
        BlockPos at = blockPosition();
        var structure = sl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(com.soulless.Soulless.id("drowned_souls"));
        if (structure != null) {
            var start = sl.structureManager().getStructureWithPieceAt(at, structure);
            for (var piece : start.isValid() ? start.getPieces() : java.util.List.<net.minecraft.world.level.levelgen.structure.StructurePiece>of())
                if (piece instanceof com.soulless.world.DrownedSoulsStructure.Piece p)
                    at = p.centre().offset(0, com.soulless.world.DrownedSoulsStructure.ARENA_BOTTOM_Y, 0);
        }
        // up from the floor to the first block it can stand in
        for (int i = 0; i < 40 && !(sl.getBlockState(at).canBeReplaced() && !sl.getBlockState(at.below()).canBeReplaced()); i++)
            at = sl.getBlockState(at).canBeReplaced() ? at.below() : at.above();
        sl.setBlock(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED, sl.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)), 3);
        if (sl.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
            chest.setLootTable(com.soulless.Soulless.id("chests/leviathan"), random.nextLong());
        sl.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.05);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    /** Lays the head part before the neck and the body parts along the path the neck has swum. */
    private void layBody() {
        Vec3 now = position().add(0, 1.5, 0);
        // a new point only once the neck has really moved on: bumping a wall or the network's small corrections would
        // otherwise crowd the trail with jitter, and the body would twitch along it
        if (trail.isEmpty() || trail.peekFirst().distanceToSqr(now) > 0.3 * 0.3) trail.addFirst(now);
        while (trail.size() > 400) trail.removeLast();
        Vec3 fwd = Vec3.directionFromRotation(getXRot(), getYRot());
        place(head, now.add(fwd.scale(3.6)));
        // walk back along the trail; past its end, carry on straight behind
        java.util.Iterator<Vec3> it = trail.iterator();
        Vec3 prev = it.hasNext() ? it.next() : now;
        double walked = 0;
        int seg = 0;
        double want = SEG_LEN * 0.5;
        while (seg < SEGMENTS) {
            if (it.hasNext()) {
                Vec3 next = it.next();
                double step = prev.distanceTo(next);
                while (seg < SEGMENTS && walked + step >= want) {
                    double f = step < 1e-6 ? 0 : (want - walked) / step;
                    place(body[seg], prev.lerp(next, f));
                    seg++;
                    want += SEG_LEN;
                }
                walked += step;
                prev = next;
            } else {
                Vec3 back = prev.subtract(fwd.scale(want - walked));
                place(body[seg], back);
                seg++;
                want += SEG_LEN;
            }
        }
    }

    private static void place(LeviathanPart p, Vec3 c) {
        p.xo = p.getX();
        p.yo = p.getY();
        p.zo = p.getZ();
        p.xOld = p.getX();
        p.yOld = p.getY();
        p.zOld = p.getZ();
        p.setPos(c.x, c.y - p.getBbHeight() / 2, c.z);
    }

    @Override
    public void onAboveBubbleCol(boolean down) {}   // the sea's bubble columns do not move it

    @Override
    public void onInsideBubbleColumn(boolean down) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {}   // its swimming is its own (swim)

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(30);   // the model is far bigger than its box
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putInt("Grabs", grabsDone);
        tag.putInt("Stage", stage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        grabsDone = tag.getInt("Grabs");
        stage = Math.max(1, tag.getInt("Stage"));
        bar.setColor(stage == 1 ? BossEvent.BossBarColor.BLUE : stage == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> isDeadOrDying() ? software.bernie.geckolib.core.object.PlayState.STOP : state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "roar", 8, state -> software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("roar", ROAR).triggerableAnim("bite", BITE));
        // added last, so the fight's poses win over the others
        controllers.add(new AnimationController<>(this, "pose", 12, state -> switch (entityData.get(POSE)) {
            case 1 -> state.setAndContinue(LUNGE);
            case 2 -> state.setAndContinue(THRASH);
            case 3 -> state.setAndContinue(BREATH);
            case 4 -> state.setAndContinue(DEATH);
            default -> software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
