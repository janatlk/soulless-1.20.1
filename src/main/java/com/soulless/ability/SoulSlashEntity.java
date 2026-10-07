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
 * The beam of Soul Rend: a crescent of soul fire flying {@link #RANGE} blocks along the wielder's line of sight
 * (up and down too); it passes through creatures but is stopped by blocks, hitting each creature once for up to {@link #MAX_DAMAGE} (scaled by charge).
 * Drawn on the client by {@code client.SoulSlashRenderer} in the same shape as the Soul Rend trail.
 */
public class SoulSlashEntity extends Entity {
    /** Damage of an instant release and of a full charge. */
    public static final float MIN_DAMAGE = 30.0F, MAX_DAMAGE = 60.0F;
    /** Lifesteal: this share of the damage dealt, at least half a heart, at most one heart per beam. */
    public static final float LIFESTEAL = 0.05F, MIN_HEAL = 1.0F, MAX_HEAL = 2.0F;
    public static final double RANGE = 92;
    public static final double SPEED = 1.5;
    /** Half-width of the crescent across its flight, and its height. */
    public static final double HALF_WIDTH = 2.8, HEIGHT = 3.2;

    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(SoulSlashEntity.class, EntityDataSerializers.FLOAT);
    /** Thrown by a wielder wearing the Soulsmith's Ring: drawn and burning in gold. */
    private static final EntityDataAccessor<Boolean> GOLD = SynchedEntityData.defineId(SoulSlashEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID owner;
    private final Set<Integer> struck = new HashSet<>();
    private float dealt, healed;

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

    /** Charge 0..1 (0 = an instant release). */
    public float power() {
        return entityData.get(POWER);
    }

    public void setGold(boolean gold) {
        entityData.set(GOLD, gold);
    }

    public boolean gold() {
        return entityData.get(GOLD);
    }

    public static float damageFor(float charge) {
        return MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * Mth.clamp(charge, 0, 1);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(POWER, 1F);
        entityData.define(GOLD, false);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        // blocks stop the beam
        var hit = level().clip(new net.minecraft.world.level.ClipContext(position(), position().add(v),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        boolean blocked = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
        Vec3 next = blocked ? hit.getLocation() : position().add(v);
        setPos(next.x, next.y, next.z);
        if (blocked && level() instanceof ServerLevel sl) {
            sl.sendParticles(com.soulless.item.SoulRing.ember(gold()), next.x, next.y, next.z, 30, 0.4, 0.4, 0.4, 0.12);
            sl.sendParticles(com.soulless.item.SoulRing.soul(gold()), next.x, next.y, next.z, 8, 0.3, 0.3, 0.3, 0.05);
            sl.playSound(null, next.x, next.y, next.z, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
        }
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                double side = (random.nextDouble() - 0.5) * 2 * HALF_WIDTH;
                Vec3 a = across(v);
                Vec3 up = a.cross(v.normalize());
                Vec3 p = position().add(a.scale(side)).add(up.scale((random.nextDouble() - 0.5) * HEIGHT));
                level().addParticle(com.soulless.item.SoulRing.ember(gold()), p.x, p.y, p.z, 0, 0.02, 0);
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
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator() && !com.soulless.pet.TamedFox.isPetOf(e, player))) {
            if (struck.contains(t.getId()) || t.isAlliedTo(player) || (t instanceof TamableAnimal pet && pet.isOwnedBy(player))) continue;
            Vec3 rel = t.position().add(0, t.getBbHeight() / 2, 0).subtract(position());
            double side = rel.dot(across), along = rel.dot(dir), rise = rel.dot(up);
            if (Math.abs(side) > HALF_WIDTH + t.getBbWidth() / 2 || along < -SPEED - 0.5 - t.getBbWidth() / 2 || along > 0.8 + t.getBbWidth() / 2) continue;
            if (Math.abs(rise) > HEIGHT / 2 + t.getBbHeight() / 2) continue;
            struck.add(t.getId());
            float damage = damageFor(power());
            t.invulnerableTime = 0;
            float before = t.getHealth();
            Vec3 keep = t.getDeltaMovement();
            SoulBonus.abilityDamage(() -> t.hurt(level.damageSources().playerAttack(player), damage));
            t.setDeltaMovement(keep);   // no knockback
            t.hurtMarked = true;
            dealt += Math.max(0, before - t.getHealth());
            // lifesteal: 5% of the damage dealt, between half a heart and a heart per beam
            float target = dealt > 0 ? Mth.clamp(dealt * LIFESTEAL, MIN_HEAL, MAX_HEAL) : 0;
            if (target > healed) {
                player.heal(target - healed);
                if (healed == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, player.getX(), player.getY() + 2.1, player.getZ(), 1, 0.2, 0.1, 0.2, 0);
                healed = target;
            }
            level.sendParticles(com.soulless.item.SoulRing.ember(gold()), t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 15, 0.3, 0.4, 0.3, 0.12);
        }
        if (blocked) discard();
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
