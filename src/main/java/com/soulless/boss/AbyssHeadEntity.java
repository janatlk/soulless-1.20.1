package com.soulless.boss;

import com.soulless.effect.SoulCurse;
import com.soulless.registry.ModEntities;
import com.soulless.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One of the ten heads of the abyss the Reaper looses in its ultimate: flies straight out, no block damage.
 * Hits deal heavy damage and a short Soul Curse. Rendered as a purple wither-style skull.
 */
public class AbyssHeadEntity extends WitherSkull {
    public static final float DAMAGE = 12.0F;
    private static final int LIFE = 60;

    public AbyssHeadEntity(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
    }

    public AbyssHeadEntity(Level level, LivingEntity owner, Vec3 dir) {
        super(ModEntities.ABYSS_HEAD.get(), level);
        setOwner(owner);
        setNoGravity(true);
        setDeltaMovement(dir.scale(0.55));
        xPower = dir.x * 0.02;
        yPower = 0;
        zPower = dir.z * 0.02;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) {
            burst();
            discard();
        }
        if (level().isClientSide) {
            level().addParticle(ModParticles.SOUL_EMBER.get(), getX(), getY() + 0.25, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected net.minecraft.core.particles.ParticleOptions getTrailParticle() {
        return ParticleTypes.SOUL;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity e = result.getEntity();
        if (e instanceof WailingReaperEntity || e instanceof AbyssHeadEntity) return;
        e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), DAMAGE);
        if (e instanceof LivingEntity living) SoulCurse.apply(living, 80, 0, getOwner());
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) result).getEntity();
            if (e instanceof WailingReaperEntity || e instanceof AbyssHeadEntity) return;
            onHitEntity((EntityHitResult) result);
        }
        if (!level().isClientSide) {
            burst();
            discard();
        }
    }

    private void burst() {
        if (level() instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 15, 0.3, 0.3, 0.3, 0.08);
            s.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
            s.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 1.4F);
        }
    }
}
