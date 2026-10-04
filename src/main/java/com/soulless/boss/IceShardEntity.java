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
 * One cell of the Reaper's ice rain. A frost rune glows on the ground from the moment it is cast; two ice spikes
 * plunge onto it, one in each pass of the rain, and the rune goes out after the second. Everything inside the
 * rune (its whole area - the rim is not safe) is hit by each spike. Purely a timeline: nothing moves, the
 * renderer draws the falls from the entity's age.
 */
public class IceShardEntity extends Entity {
    /** Grid spacing of the cells; the rune radius covers a whole hexagonal cell (spacing / sqrt 3). */
    public static final double SPACING = 5.0;
    public static final double RADIUS = SPACING / Math.sqrt(3) + 0.05;
    public static final int FALL = 7;
    public static final int STUCK = 22;
    public static final double DROP = 24;
    public static final float DAMAGE = 14.0F;

    private static final EntityDataAccessor<Integer> DROP_A = SynchedEntityData.defineId(IceShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DROP_B = SynchedEntityData.defineId(IceShardEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;

    public IceShardEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** The two spikes start falling {@code first} and {@code second} ticks after the rune appears. */
    public void setup(int ownerId, int first, int second) {
        this.ownerId = ownerId;
        entityData.set(DROP_A, first);
        entityData.set(DROP_B, second);
    }

    public int fallStart(int drop) {
        return entityData.get(drop == 0 ? DROP_A : DROP_B);
    }

    public int impactTick(int drop) {
        return fallStart(drop) + FALL;
    }

    public int lastImpact() {
        return impactTick(1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // frost rising from the rune while it is armed
            if (tickCount < lastImpact() && random.nextInt(4) == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * RADIUS;
                level().addParticle(ParticleTypes.SNOWFLAKE, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r,
                        0, 0.03 + random.nextDouble() * 0.03, 0);
            }
            return;
        }
        ServerLevel s = (ServerLevel) level();
        if (tickCount == impactTick(0) || tickCount == impactTick(1)) shatter(s);
        if (tickCount > lastImpact() + STUCK) discard();
    }

    private void shatter(ServerLevel s) {
        Entity owner = s.getEntity(ownerId);
        AABB box = new AABB(getX() - RADIUS, getY() - 1, getZ() - RADIUS, getX() + RADIUS, getY() + 3.5, getZ() + RADIUS);
        for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof WailingReaperEntity))) {
            // anything whose footprint touches the rune is hit
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double reach = RADIUS + e.getBbWidth() / 2;
            if (dx * dx + dz * dz > reach * reach) continue;
            e.invulnerableTime = 0;
            e.hurt(s.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), DAMAGE);
            // a short, light slow: being hit should hurt, not pin the player under the next spikes
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 0));
        }
        s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                getX(), getY() + 0.3, getZ(), 40, RADIUS * 0.35, 0.3, RADIUS * 0.35, 0.25);
        s.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.5, getZ(), 20, RADIUS * 0.4, 0.4, RADIUS * 0.4, 0.12);
        s.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 5, RADIUS * 0.3, 0.05, RADIUS * 0.3, 0.02);
        float pitch = 0.6F + random.nextFloat() * 0.3F;
        s.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.1F, pitch);
        if (random.nextInt(2) == 0) s.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.9F, pitch * 0.8F);
    }

    /** 0..1 progress toward the next spike (the rune's fill grows to the rim at each impact). */
    public float warnProgress(float pt) {
        float t = tickCount + pt;
        int a = impactTick(0), b = impactTick(1);
        if (t < a) return Mth.clamp(t / a, 0, 1);
        return Mth.clamp((t - a) / Math.max(1, b - a), 0, 1);
    }

    /** Height of a spike's tip above the ground, or -1 before it starts falling. Accelerates as it drops. */
    public float tipHeight(int drop, float pt) {
        float t = tickCount + pt - fallStart(drop);
        if (t < 0) return -1;
        float p = Math.min(1, t / FALL);
        return (float) (DROP * (1 - p * p));
    }

    /** 1 while a landed spike is stuck in the ground, shrinking to 0 as it melts. */
    public float meltScale(int drop, float pt) {
        float t = tickCount + pt - impactTick(drop);
        return t < STUCK - 8 ? 1 : Mth.clamp((STUCK - t) / 8F, 0, 1);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;   // a few seconds of effect; never worth saving
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DROP_A, 20);
        entityData.define(DROP_B, 40);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
