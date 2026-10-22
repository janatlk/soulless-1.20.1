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
 * only swims - slow, wide loops round where it was spawned, turning smoothly - so its model and motion can be seen:
 * the tail and body always in a dragon-like wave, the head tentacles swaying, whiskers drifting, breathing and
 * blinking (client.LeviathanModel does the procedural part). The fight comes later.
 */
public class LeviathanEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.leviathan.idle");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.leviathan.roar");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos home;
    /** Where it is swimming to, and how long until it picks somewhere else. */
    private Vec3 goal;
    private int goalTicks;
    /** Client: how fast it is turning (degrees per tick), smoothed - the body bends into its turns. */
    public float turn, turnO;

    public LeviathanEntity(EntityType<? extends LeviathanEntity> type, Level level) {
        super(type, level);
        noCulling = true;
        setNoGravity(true);
        xpReward = 500;
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
        float before = getYRot();
        if (!level().isClientSide) swim();
        if (level().isClientSide) {
            turnO = turn;
            turn = Mth.lerp(0.15F, turn, Mth.wrapDegrees(getYRot() - yRotO));
        }
    }

    /** Wide, slow loops: steer toward a point round home, never turning sharply. */
    private void swim() {
        if (goal == null || --goalTicks <= 0 || position().distanceToSqr(goal) < 16) {
            double a = random.nextDouble() * Math.PI * 2, r = 10 + random.nextDouble() * 18;
            goal = Vec3.atCenterOf(home).add(Math.cos(a) * r, (random.nextDouble() - 0.5) * 14, Math.sin(a) * r);
            goalTicks = 200;
        }
        Vec3 to = goal.subtract(position());
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        float wantPitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        setYRot(Mth.approachDegrees(getYRot(), wantYaw, 2.2F));
        setXRot(Mth.approach(getXRot(), Mth.clamp(wantPitch, -30, 30), 1.2F));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        float speed = isInWater() ? 0.32F : 0.05F;
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot()).scale(speed);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(dir.scale(0.4)));
        if (!isInWater()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        move(MoverType.SELF, getDeltaMovement());
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
