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
 * The Reaper's basic attack: a soul bolt that bends gently toward its target. The turn rate is low enough
 * that sprinting sideways gets out of its way.
 */
public class SoulBoltEntity extends Projectile {
    public static final double SPEED = 0.7;
    public static final float DAMAGE = 9.0F;
    private static final double TURN_PER_TICK = Math.toRadians(2.5);
    private static final int LIFE = 100;

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
            for (int i = 0; i < 3; i++) {
                level().addParticle(ModParticles.SOUL_EMBER.get(), getX() - v.x * i / 3, getY() + 0.3 - v.y * i / 3, getZ() - v.z * i / 3,
                        (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
            }
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.3, getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        if (!level().isClientSide) {
            e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), DAMAGE);
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
