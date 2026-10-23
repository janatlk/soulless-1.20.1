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
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The pose the fight shows (0 none, 1 rising at its prey with the jaw wide, 2 shaking it, 3 the dragon's breath). */
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(LeviathanEntity.class, EntityDataSerializers.INT);

    private enum Phase { ROAM, CIRCLE, COIL, CHARGE, RECOVER, REAR, BREATHE, GRAB_RUSH, GRAB_DIVE, GRAB_RISE, GRAB_HOLD }

    private static final float BITE_DAMAGE = 12, RAM_DAMAGE = 6, GRAB_DAMAGE = 18, BREATH_DAMAGE = 4;
    /** The breath: how long the bubbles come first, how long it lasts in all, how far it reaches (ticks, blocks). */
    private static final int BUBBLES = 30, BREATH_TICKS = 140, BREATH_RANGE = 30;
    private final ServerBossEvent bar = new ServerBossEvent(net.minecraft.network.chat.Component.translatable("entity.soulless.leviathan"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Phase phase = Phase.ROAM;
    private int phaseTicks, grabsDone, ramCooldown, cycles;
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
            body[i] = new LeviathanPart(this, "body_" + i, Math.max(w, h), h);
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
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1200).add(Attributes.MOVEMENT_SPEED, 0.3)
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
        if (!level().isClientSide) {
            swim();
            bar.setProgress(getHealth() / getMaxHealth());
            if (tickCount % 10 == 0) updateBar();
        }
        layBody();
        if (!level().isClientSide) attackWithBody();
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
        boolean free = phase == Phase.GRAB_RISE || phase == Phase.GRAB_HOLD || phase == Phase.CHARGE
                || phase == Phase.REAR || phase == Phase.BREATHE;   // nothing turns it aside
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
            case RECOVER -> 0.5F;
            case REAR -> 0.4F;
            case BREATHE -> 0.02F;
            case GRAB_RUSH -> 0.8F;
            case GRAB_DIVE -> 0.85F;
            case GRAB_RISE -> 1.15F;
            case GRAB_HOLD -> 0.35F;
        };
        float turnRate = switch (phase) {
            case COIL -> 6F;
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
        // twice a fight: the grab from below
        float hp = getHealth() / getMaxHealth();
        if (!grabbing && (phase == Phase.CIRCLE || phase == Phase.RECOVER) && (grabsDone == 0 && hp < 2 / 3F || grabsDone == 1 && hp < 1 / 3F)) {
            grabsDone++;
            setPhase(Phase.GRAB_RUSH);
            roar();
            level().playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 4F, 0.5F);
        }
        switch (phase) {
            case CIRCLE -> {
                circle(at, 13 + 4 * Math.sin(tickCount * 0.02), 0.016F);
                if (--roarCooldown <= 0 && distanceToSqr(prey) < 24 * 24) {
                    roar();
                    roarCooldown = 260 + random.nextInt(200);
                }
                if (phaseTicks > 90 + random.nextInt(60)) {
                    // every third time round: rear up and breathe, if there is room above
                    boolean room = openWater(position().add(0, 10, 0), 2) && openWater(position().add(0, 18, 0), 2);
                    setPhase(++cycles % 3 == 0 && room ? Phase.REAR : Phase.COIL);
                }
            }
            case COIL -> {   // slows and swings its head round onto its prey
                goal = at;
                if (phaseTicks >= 16) {
                    chargeAim = at.add(prey.getDeltaMovement().scale(10));
                    setPhase(Phase.CHARGE);
                    level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3F, 0.5F);
                }
            }
            case CHARGE -> {
                // straight through where its prey was, a little past
                Vec3 fwd = Vec3.directionFromRotation(getXRot(), getYRot());
                goal = chargeAim.add(fwd.scale(12));
                double d = mouth().distanceToSqr(at);
                if (d < 7 * 7 && phaseTicks % 20 == 1) triggerAnim("roar", "bite");
                if (d < 3.2 * 3.2 && prey.hurtTime == 0) {
                    prey.hurt(damageSources().mobAttack(this), BITE_DAMAGE);
                    prey.knockback(1.2, -fwd.x, -fwd.z);
                    level().playSound(null, prey.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2F, 0.6F);
                }
                if (phaseTicks > 45 || position().subtract(chargeAim).dot(fwd) > 10) {
                    setPhase(Phase.RECOVER);
                    goal = pickGoal(at, 18, 26, 2, 8);
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
                breathAim = breathAim.lerp(at, phaseTicks < BUBBLES ? 0.3 : 0.06);
                breathe(phaseTicks >= BUBBLES);
                if (phaseTicks >= BREATH_TICKS) {
                    setPhase(Phase.RECOVER);
                    goal = pickGoal(at, 16, 24, 0, 8);
                }
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

    private void circle(Vec3 at, double r, float rate) {
        circleAngle += rate * circleDir;
        Vec3 c = at.add(Math.cos(circleAngle) * r, 2 + 2 * Math.sin(tickCount * 0.03), Math.sin(circleAngle) * r);
        goal = openWater(c, 2) ? c : goal != null ? goal : c;
    }

    /** From its mouth toward {@code breathAim}: first a stream of bubbles, then the violet breath, which burns. */
    private void breathe(boolean fire) {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 from = mouth();
        Vec3 dir = breathAim.subtract(from);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        int reach = fire ? BREATH_RANGE : 8;
        for (double d = 1; d < reach; d += fire ? 0.8 : 1.2) {
            Vec3 p = from.add(dir.scale(d));
            double spread = 0.15 + d * 0.04;
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
            if (along < 0 || along > BREATH_RANGE) continue;
            double off = c.subtract(dir.scale(along)).length();
            if (off < 1.4 + along * 0.05) {
                p.invulnerableTime = 0;
                p.hurt(damageSources().dragonBreath(), BREATH_DAMAGE);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
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
            Vec3 fling = fwd.scale(1.6).add(side.scale(Math.signum(Math.sin(phaseTicks * 0.13)) * 0.6)).add(0, 0.6, 0);
            p.setDeltaMovement(fling);
            p.hurtMarked = true;
            level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2F, 0.5F);
            setPhase(Phase.RECOVER);
            goal = pickGoal(position(), 18, 26, 0, 8);
        }
    }

    /** Whoever its body passes through while it charges or rises is rammed. */
    private void attackWithBody() {
        if (ramCooldown > 0 || !(phase == Phase.CHARGE || phase == Phase.GRAB_RISE || phase == Phase.GRAB_RUSH)) return;
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        grabsDone = tag.getInt("Grabs");
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "roar", 8, state -> software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("roar", ROAR).triggerableAnim("bite", BITE));
        // added last, so the fight's poses win over the others
        controllers.add(new AnimationController<>(this, "pose", 12, state -> switch (entityData.get(POSE)) {
            case 1 -> state.setAndContinue(LUNGE);
            case 2 -> state.setAndContinue(THRASH);
            case 3 -> state.setAndContinue(BREATH);
            default -> software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
