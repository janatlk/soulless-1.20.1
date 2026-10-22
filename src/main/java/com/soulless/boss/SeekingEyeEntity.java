package com.soulless.boss;

import com.soulless.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown seeking eye (item.SeekingEyeItem): the Eye of Ender's flight - rising toward the target, hovering, then
 * dropping - but in soul fire, and it always falls back as an item, never shatters.
 */
public class SeekingEyeEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(SeekingEyeEntity.class, EntityDataSerializers.ITEM_STACK);
    private double tx, ty, tz;
    private int life;

    public SeekingEyeEntity(EntityType<? extends SeekingEyeEntity> type, Level level) {
        super(type, level);
    }

    public SeekingEyeEntity(Level level, double x, double y, double z) {
        this(ModEntities.SEEKING_EYE.get(), level);
        setPos(x, y, z);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copyWithCount(1));
    }

    @Override
    public ItemStack getItem() {
        ItemStack s = entityData.get(ITEM);
        return s.isEmpty() ? new ItemStack(Items.ENDER_EYE) : s;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ITEM, ItemStack.EMPTY);
    }

    /** As the Eye of Ender: aim at most 12 blocks along the way, a little above where it stands. */
    public void signalTo(BlockPos pos) {
        double dx = pos.getX() - getX(), dz = pos.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 12) {
            tx = getX() + dx / d * 12;
            tz = getZ() + dz / d * 12;
            ty = getY() + 8;
        } else {
            tx = pos.getX();
            ty = pos.getY();
            tz = pos.getZ();
        }
        life = 0;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        double x = getX() + v.x, y = getY() + v.y, z = getZ() + v.z;
        double h = v.horizontalDistance();
        setXRot(lerpRotation(xRotO, (float) (Mth.atan2(v.y, h) * 180 / Math.PI)));
        setYRot(lerpRotation(yRotO, (float) (Mth.atan2(v.x, v.z) * 180 / Math.PI)));
        if (!level().isClientSide) {
            double dx = tx - x, dz = tz - z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            float ang = (float) Mth.atan2(dz, dx);
            double sp = Mth.lerp(0.0025, h, dist);
            double vy = v.y;
            if (dist < 1) {
                sp *= 0.8;
                vy *= 0.8;
            }
            int up = getY() < ty ? 1 : -1;
            v = new Vec3(Math.cos(ang) * sp, vy + (up - vy) * 0.015, Math.sin(ang) * sp);
            setDeltaMovement(v);
        }
        if (isInWater()) {
            for (int i = 0; i < 4; i++) level().addParticle(ParticleTypes.BUBBLE, x - v.x * 0.25, y - v.y * 0.25, z - v.z * 0.25, v.x, v.y, v.z);
        } else {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, x - v.x * 0.25 + random.nextDouble() * 0.6 - 0.3, y - v.y * 0.25 - 0.5,
                    z - v.z * 0.25 + random.nextDouble() * 0.6 - 0.3, v.x, v.y, v.z);
        }
        if (!level().isClientSide) {
            setPos(x, y, z);
            if (++life > 80) {
                playSound(SoundEvents.ENDER_EYE_DEATH, 1.0F, 0.7F);
                discard();
                ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), getItem());
                drop.setGlowingTag(true);
                level().addFreshEntity(drop);
            }
        } else {
            setPosRaw(x, y, z);
        }
    }

    private static float lerpRotation(float from, float to) {
        while (to - from < -180) from -= 360;
        while (to - from >= 180) from += 360;
        return Mth.lerp(0.2F, from, to);
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Item", getItem().save(new CompoundTag()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setItem(ItemStack.of(tag.getCompound("Item")));
    }
}
