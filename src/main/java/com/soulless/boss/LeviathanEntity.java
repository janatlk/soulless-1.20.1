package com.soulless.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
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
 * The Leviathan of the Abyss, the boss of the City of Drowned Souls: a 40-block eel crowned with tentacles. For now it
 * only swims (the fight comes later):
 * <ul>
 *   <li>it keeps to open water - every place it heads for has water all round it, it looks ahead and turns away
 *       from walls and the sea floor, and bubble columns do not drag it;</li>
 *   <li>alone, it roams in slow wide loops round its home; a player in the water nearby draws it - it circles them,
 *       closing in and drawing off, and roars now and then;</li>
 *   <li>its body is hit along its whole length: a head part and twelve body parts (LeviathanPart) laid along the
 *       path its head has swum, as a serpent's body follows its head.</li>
 * </ul>
 * The model's motion is client.LeviathanModel.
 */
public class LeviathanEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.leviathan.idle");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.leviathan.roar");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Body segments: how far behind the neck each one's centre lies, and its size (blocks). */
    private static final int SEGMENTS = 12;
    private static final float SEG_LEN = 2.75F;
    private final LeviathanPart head;
    private final LeviathanPart[] body = new LeviathanPart[SEGMENTS];
    private final LeviathanPart[] parts;
    /** Where the neck has been, newest first, a point every tick (the body is laid along it). */
    private final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();
    private Player prey;
    private float circleAngle;
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
    public void tick() {
        super.tick();
        if (home == null) home = blockPosition();
        setNoGravity(isInWater());
        if (!level().isClientSide) swim();
        layBody();
        if (level().isClientSide) {
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
        // a player in the water close by draws it: it circles them, now nearer, now further off
        if (prey == null || !prey.isAlive() || prey.isSpectator() || prey.distanceToSqr(this) > 48 * 48 || !prey.isInWater()) {
            prey = level().getNearestPlayer(this, 32);
            if (prey != null && (!prey.isInWater() || prey.isSpectator())) prey = null;
        }
        boolean blocked = !openWater(position().add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(6)), 2);
        if (prey != null) {
            circleAngle += 0.012F;
            double r = 12 + 5 * Math.sin(tickCount * 0.01);
            Vec3 c = prey.position().add(Math.cos(circleAngle) * r, 3 + 2 * Math.sin(tickCount * 0.017), Math.sin(circleAngle) * r);
            goal = openWater(c, 2) ? c : goal;
            if (--roarCooldown <= 0 && distanceToSqr(prey) < 22 * 22) {
                roar();
                roarCooldown = 300 + random.nextInt(300);
            }
        }
        if (goal == null || blocked || --goalTicks <= 0 || position().distanceToSqr(goal) < 16) {
            if (prey == null || blocked) {
                Vec3 g = pickGoal(home == null ? position() : Vec3.atCenterOf(home), 8, 26, 6, 10);
                if (g == null) g = pickGoal(position(), 4, 14, 4, 8);   // home is no good: anywhere open close by
                goal = g != null ? g : position().add(0, 4, 0);
                goalTicks = 240;
            }
        }
        Vec3 to = goal.subtract(position());
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        float wantPitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        // the sea floor close below: lift
        if (!level().getFluidState(blockPosition().below(3)).is(net.minecraft.tags.FluidTags.WATER)) wantPitch = Math.min(wantPitch, -18);
        setYRot(Mth.approachDegrees(getYRot(), wantYaw, blocked ? 4F : 2.2F));
        setXRot(Mth.approach(getXRot(), Mth.clamp(wantPitch, -35, 35), 1.4F));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        float speed = prey != null ? 0.38F : 0.28F;
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot()).scale(speed);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(dir.scale(0.4)));
        move(MoverType.SELF, getDeltaMovement());
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "roar", 4, state -> software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("roar", ROAR));
    }

    public void roar() {
        triggerAnim("roar", "roar");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
