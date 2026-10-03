package com.soulless.boss;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * One ice shard of the Reaper's ultimate. A frost rune glows on the ground where it will land (its inner fill
 * grows until it touches the rim at the moment of impact), then a long ice spike plunges from the sky,
 * shatters on whatever stands inside, stays stuck in the ground for a moment and melts away.
 * Purely a timeline: nothing moves, the renderer draws the fall from the entity's age.
 */
public class IceShardEntity extends Entity {
    /** Warning time before the shard starts to fall (plus this shard's own delay). */
    public static final int WARN = 40;
    public static final int FALL = 7;
    public static final int STUCK = 26;
    public static final double RADIUS = 2.0;
    public static final double DROP = 24;
    public static final float DAMAGE = 20.0F;

    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(IceShardEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;

    public IceShardEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(int ownerId, int delay) {
        this.ownerId = ownerId;
        entityData.set(DELAY, delay);
    }

    /** Tick at which the spike starts falling. */
    public int fallStart() {
        return WARN + entityData.get(DELAY);
    }

    public int impactTick() {
        return fallStart() + FALL;
    }

    @Override
    public void tick() {
        super.tick();
        int impact = impactTick();
        if (level().isClientSide) {
            // frost rising from the rune while it warns
            if (tickCount < impact && random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * RADIUS;
                level().addParticle(ParticleTypes.SNOWFLAKE, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r,
                        0, 0.03 + random.nextDouble() * 0.03, 0);
            }
            return;
        }
        ServerLevel s = (ServerLevel) level();
        if (tickCount == impact) shatter(s);
        if (tickCount > impact + STUCK) discard();
    }

    private void shatter(ServerLevel s) {
        Entity owner = s.getEntity(ownerId);
        AABB box = new AABB(getX() - RADIUS, getY() - 1, getZ() - RADIUS, getX() + RADIUS, getY() + 3.5, getZ() + RADIUS);
        for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof WailingReaperEntity))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            e.hurt(s.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), DAMAGE);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 120));
        }
        s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                getX(), getY() + 0.3, getZ(), 45, RADIUS * 0.35, 0.3, RADIUS * 0.35, 0.25);
        s.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.5, getZ(), 25, RADIUS * 0.4, 0.4, RADIUS * 0.4, 0.12);
        s.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 6, RADIUS * 0.3, 0.05, RADIUS * 0.3, 0.02);
        float pitch = 0.6F + random.nextFloat() * 0.3F;
        s.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, pitch);
        s.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0F, pitch * 0.8F);
    }

    /** 0..1 progress of the warning (1 at impact). */
    public float warnProgress(float pt) {
        return Mth.clamp((tickCount + pt) / impactTick(), 0, 1);
    }

    /** Height of the spike's tip above the ground, or -1 before it starts falling. Accelerates as it drops. */
    public float tipHeight(float pt) {
        float t = tickCount + pt - fallStart();
        if (t < 0) return -1;
        float p = Math.min(1, t / FALL);
        return (float) (DROP * (1 - p * p));
    }

    /** 1 while stuck in the ground, shrinking to 0 as it melts. */
    public float meltScale(float pt) {
        float t = tickCount + pt - impactTick();
        return t < STUCK - 8 ? 1 : Mth.clamp((STUCK - t) / 8F, 0, 1);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;   // a few seconds of effect; never worth saving
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DELAY, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
