package com.soulless.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Lampkeeper: a drowned soul tending the lamps of the City of Drowned Souls' catacombs - a looter's nuisance more
 * than a danger. It drifts about its room, lantern lit. When it sees a player it blows the lantern out: it all but
 * vanishes and the water round goes dark (Darkness), and it steals up behind its prey and rakes it (a light hit and a
 * moment's slowness) - then lights its lantern again and drifts off before it tries once more. Struck while dark, the
 * light flares back and it is seen.
 */
public class LampkeeperEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> LIT = SynchedEntityData.defineId(LampkeeperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lampkeeper.idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.lampkeeper.swim");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.lampkeeper.death");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private enum Phase { DRIFT, BLOW, STALK, STRIKE, RETREAT }

    private Phase phase = Phase.DRIFT;
    private int phaseTicks, rest;
    private Vec3 wander;

    public LampkeeperEntity(EntityType<? extends LampkeeperEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 12, true);
        setNoGravity(true);
        setPathfindingMalus(BlockPathTypes.WATER, 0);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LIT, true);
    }

    /** Its lantern burns (false: blown out - it is all but invisible). */
    public boolean lit() {
        return entityData.get(LIT);
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (lit() && random.nextInt(4) == 0) {
                // a wisp of soul fire from its lantern (low at its right hand)
                Vec3 side = Vec3.directionFromRotation(0, yBodyRot + 90).scale(-0.4);
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + side.x, getY() + 0.25, getZ() + side.z, 0, 0.01, 0);
            }
            if (!lit() && random.nextInt(6) == 0)
                level().addParticle(ParticleTypes.BUBBLE, getX(), getY() + 1.6, getZ(), 0, 0.03, 0);
            return;
        }
        phaseTicks++;
        if (rest > 0) rest--;
        var target = getTarget();
        boolean hunting = target != null && target.isAlive() && distanceToSqr(target) < 14 * 14 && hasLineOfSight(target);
        switch (phase) {
            case DRIFT -> {
                if (hunting && rest == 0) {
                    setPhase(Phase.BLOW);
                    getNavigation().stop();
                    triggerAnim("act", "blow");
                    break;
                }
                // drift slowly about its room
                if (wander == null || phaseTicks % 80 == 0 || getNavigation().isDone()) {
                    wander = position().add(random.nextInt(9) - 4, random.nextInt(5) - 2, random.nextInt(9) - 4);
                    getNavigation().moveTo(wander.x, wander.y, wander.z, 0.6);
                }
            }
            case BLOW -> {
                if (target != null) getLookControl().setLookAt(target);
                if (phaseTicks == 12) {
                    entityData.set(LIT, false);
                    level().playSound(null, blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
                    level().playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.5F);
                    if (level() instanceof ServerLevel sl)
                        sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 12, 0.2, 0.2, 0.2, 0.01);
                    for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(9)))
                        if (!p.isCreative() && !p.isSpectator()) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                }
                if (phaseTicks >= 26) setPhase(Phase.STALK);
            }
            case STALK -> {
                if (!hunting || phaseTicks > 200) {
                    relight(false);
                    break;
                }
                // round behind its prey, then in
                Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(phaseTicks < 50 ? 3 : 1))
                        .add(0, target.getBbHeight() * 0.4, 0);
                getNavigation().moveTo(behind.x, behind.y, behind.z, phaseTicks < 50 ? 0.9 : 1.4);
                getLookControl().setLookAt(target);
                if (distanceToSqr(target) < 2.2 * 2.2 && phaseTicks > 30) {
                    setPhase(Phase.STRIKE);
                    triggerAnim("act", "attack");
                }
            }
            case STRIKE -> {
                if (target != null) getLookControl().setLookAt(target);
                if (phaseTicks == 8 && target != null && distanceToSqr(target) < 3 * 3) {
                    doHurtTarget(target);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    level().playSound(null, blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1F, 0.6F);
                }
                if (phaseTicks >= 16) relight(true);
            }
            case RETREAT -> {
                if (target != null && phaseTicks % 20 == 1) {
                    Vec3 away = position().subtract(target.position()).normalize().scale(6).add(position());
                    getNavigation().moveTo(away.x, away.y, away.z, 1.0);
                }
                if (phaseTicks > 70) setPhase(Phase.DRIFT);
            }
        }
    }

    /** The lantern lit again; it draws off (after a strike) and waits before the next try. */
    private void relight(boolean struck) {
        entityData.set(LIT, true);
        level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.6F, 1.6F);
        rest = struck ? 160 : 100;
        setPhase(Phase.RETREAT);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && !lit() && isAlive()) relight(false);   // struck in the dark: revealed
        return hit;
    }

    @Override
    public boolean isInvisible() {
        return false;   // the renderer fades it instead (it is never wholly gone)
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + random.nextFloat() * 0.2F;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {}

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected void tickDeath() {
        if (++deathTime >= 40 && !level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(state.isMoving() ? SWIM : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "act", 3, state -> PlayState.STOP)
                .triggerableAnim("blow", RawAnimation.begin().thenPlay("animation.lampkeeper.blow"))
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("animation.lampkeeper.attack")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static boolean checkSpawn(EntityType<? extends Mob> type, net.minecraft.world.level.LevelAccessor level) {
        return true;
    }
}
