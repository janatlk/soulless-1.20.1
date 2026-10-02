package com.soulless.boss;

import com.soulless.registry.ModParticles;
import com.soulless.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Souls erupting from under a player: a glowing ring warns for {@link #WARN} ticks, then a column of soul
 * fire bursts up. Anything still inside takes heavy damage and is slowed.
 */
public class SoulEruptionEntity extends Entity {
    public static final int WARN = 22;
    public static final double RADIUS = 1.8;
    public static final float DAMAGE = 14.0F;
    private int ownerId = -1;

    public SoulEruptionEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setOwnerId(int id) {
        ownerId = id;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) return;
        if (tickCount < WARN) {
            if (tickCount % 2 == 0) {
                int n = 14;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + tickCount * 0.1;
                    s.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * RADIUS, getY() + 0.1, getZ() + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
                }
                s.sendParticles(ModParticles.SOUL_EMBER.get(), getX(), getY() + 0.1, getZ(), 3, RADIUS * 0.5, 0, RADIUS * 0.5, 0.01);
            }
            if (tickCount == 2) s.playSound(null, getX(), getY(), getZ(), ModSounds.ERUPTION.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
        } else if (tickCount == WARN) {
            Entity owner = s.getEntity(ownerId);
            AABB box = new AABB(getX() - RADIUS, getY() - 0.5, getZ() - RADIUS, getX() + RADIUS, getY() + 3.5, getZ() + RADIUS);
            for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, box, x -> x instanceof Player && x.isAlive())) {
                double dx = e.getX() - getX(), dz = e.getZ() - getZ();
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                e.hurt(s.damageSources().indirectMagic(this, owner instanceof LivingEntity l ? l : null), DAMAGE);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                e.hurtMarked = true;
            }
            for (int y = 0; y < 12; y++) {
                s.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + y * 0.4, getZ(), 8, RADIUS * 0.4, 0.2, RADIUS * 0.4, 0.05);
                s.sendParticles(ParticleTypes.SOUL, getX(), getY() + y * 0.4, getZ(), 2, RADIUS * 0.3, 0.2, RADIUS * 0.3, 0.08);
            }
            s.sendParticles(ModParticles.SOUL_EMBER.get(), getX(), getY() + 0.5, getZ(), 40, RADIUS * 0.5, 1.5, RADIUS * 0.5, 0.2);
        } else if (tickCount > WARN + 20) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
