package com.soulless.boss;

import com.soulless.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Reaper's basic attack: a burning skull (drawn in 3D, see BossRenderers.Bolt) that bends gently toward its
 * target - the turn rate is low enough that sprinting sideways gets out of its way. It sets whoever it hits on
 * fire and slows them for a second.
 */
public class SoulBoltEntity extends Projectile {
    public static final double SPEED = 0.7;
    public static final float DAMAGE = 9.0F;
    private static final double TURN_PER_TICK = Math.toRadians(2.5);
    private static final int LIFE = 100;
    public static final int IGNITE_SECONDS = 5;
    /** Slowness I for a second on whoever it hits. */
    private static final int SLOW_TICKS = 20;

    @Nullable private LivingEntity target;

    public SoulBoltEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount > LIFE || getOwner() == null)) {
            discard();
            return;
        }
        Vec3 v = getDeltaMovement();
        if (target != null && target.isAlive()) {
            Vec3 want = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(position()).normalize();
            Vec3 cur = v.normalize();
            double angle = Math.acos(Math.max(-1, Math.min(1, cur.dot(want))));
            if (angle > 1.0E-3) {
                double k = Math.min(1, TURN_PER_TICK / angle);
                cur = cur.scale(1 - k).add(want.scale(k)).normalize();
            }
            v = cur.scale(SPEED);
            setDeltaMovement(v);
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
            onHit(hit);
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);

        if (level().isClientSide) {
            // a tail of blue soul flame and embers behind the skull
            for (int i = 0; i < 4; i++) {
                double k = i / 4.0;
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() - v.x * k + (random.nextDouble() - 0.5) * 0.2, getY() + 0.3 - v.y * k + (random.nextDouble() - 0.5) * 0.2,
                        getZ() - v.z * k + (random.nextDouble() - 0.5) * 0.2, -v.x * 0.05, 0.01, -v.z * 0.05);
            }
            level().addParticle(ModParticles.SOUL_EMBER.get(), getX(), getY() + 0.3, getZ(),
                    (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        if (!level().isClientSide) {
            e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), DAMAGE);
            e.setSecondsOnFire(IGNITE_SECONDS);
            if (e instanceof LivingEntity l) l.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0));
            burst();
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide && result.getType() == HitResult.Type.BLOCK) {
            burst();
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof WailingReaperEntity) && !(e instanceof SoulBoltEntity);
    }

    private void burst() {
        if (level() instanceof ServerLevel s) {
            s.sendParticles(ModParticles.SOUL_EMBER.get(), getX(), getY() + 0.3, getZ(), 20, 0.3, 0.3, 0.3, 0.1);
            s.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.05);
            s.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.2F);
        }
    }

    @Override
    public boolean isOnFire() {
        return false;
    }
}
