package com.soulless.ability;

import com.soulless.item.SoulBonus;
import com.soulless.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The crescent of soul fire Soul Rend sends flying: travels {@link #RANGE} blocks along the wielder's line of
 * sight (up and down too) through blocks and creatures, hitting each creature once for up to {@link #MAX_DAMAGE} (scaled by charge).
 * Drawn on the client by {@code client.SoulSlashRenderer} in the same shape as the Soul Rend trail.
 */
public class SoulSlashEntity extends Entity {
    public static final float MAX_DAMAGE = 45.0F;
    public static final double RANGE = 30;
    public static final double SPEED = 1.5;
    /** Half-width of the crescent across its flight, and its height. */
    public static final double HALF_WIDTH = 2.8, HEIGHT = 3.2;

    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(SoulSlashEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private final Set<Integer> struck = new HashSet<>();

    public SoulSlashEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** The entity's position is the centre of the crescent; it flies along {@code dir}. */
    public void setup(ServerPlayer owner, Vec3 dir, float power) {
        this.owner = owner.getUUID();
        entityData.set(POWER, power);
        setDeltaMovement(dir.normalize().scale(SPEED));
    }

    /** Horizontal unit vector across the flight (the crescent's width). */
    private static Vec3 across(Vec3 dir) {
        Vec3 a = new Vec3(-dir.z, 0, dir.x);
        return a.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : a.normalize();
    }

    public float power() {
        return entityData.get(POWER);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(POWER, 1F);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                double side = (random.nextDouble() - 0.5) * 2 * HALF_WIDTH;
                Vec3 a = across(v);
                Vec3 up = a.cross(v.normalize());
                Vec3 p = position().add(a.scale(side)).add(up.scale((random.nextDouble() - 0.5) * HEIGHT));
                level().addParticle(ModParticles.SOUL_EMBER.get(), p.x, p.y, p.z, 0, 0.02, 0);
            }
            return;
        }
        if (tickCount * SPEED > RANGE) {
            discard();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (!(level.getPlayerByUUID(owner) instanceof ServerPlayer player)) {
            discard();
            return;
        }
        Vec3 dir = v.normalize();
        Vec3 across = across(dir);
        Vec3 up = across.cross(dir);
        AABB box = getBoundingBox().inflate(HALF_WIDTH + 1, HEIGHT, HALF_WIDTH + 1).expandTowards(v.scale(-1));
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (struck.contains(t.getId()) || t.isAlliedTo(player) || (t instanceof TamableAnimal pet && pet.isOwnedBy(player))) continue;
            Vec3 rel = t.position().add(0, t.getBbHeight() / 2, 0).subtract(position());
            double side = rel.dot(across), along = rel.dot(dir), rise = rel.dot(up);
            if (Math.abs(side) > HALF_WIDTH + t.getBbWidth() / 2 || along < -SPEED - 0.5 - t.getBbWidth() / 2 || along > 0.8 + t.getBbWidth() / 2) continue;
            if (Math.abs(rise) > HEIGHT / 2 + t.getBbHeight() / 2) continue;
            struck.add(t.getId());
            float damage = MAX_DAMAGE * power();
            t.invulnerableTime = 0;
            SoulBonus.abilityDamage(() -> t.hurt(level.damageSources().playerAttack(player), damage));
            level.sendParticles(ModParticles.SOUL_EMBER.get(), t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 15, 0.3, 0.4, 0.3, 0.12);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Yaw used by the renderer (the crescent faces its flight). */
    public float flightYaw() {
        Vec3 v = getDeltaMovement();
        return (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90F;
    }

    /** Pitch used by the renderer: positive when flying downward. */
    public float flightPitch() {
        Vec3 v = getDeltaMovement();
        return (float) (Math.asin(Mth.clamp(-v.y / Math.max(1.0E-6, v.length()), -1, 1)) * Mth.RAD_TO_DEG);
    }
}
