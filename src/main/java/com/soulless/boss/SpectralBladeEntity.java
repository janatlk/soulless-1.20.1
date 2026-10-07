package com.soulless.boss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One of the Renegade's red blades of dark magic: it hangs over his head (slot 0..2, a fan), then, launched, flies
 * straight at where the foe was. A projectile - a parry knocks it aside like an arrow. Drawn as a glowing red sword
 * pointing along its flight (client.RenegadeRenderer.Blade).
 */
public class SpectralBladeEntity extends Projectile {
    private static final float DAMAGE = 3;
    private static final double SPEED = 1.05;
    private int slot;
    private boolean launched;
    private int flight;

    public SpectralBladeEntity(EntityType<? extends SpectralBladeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void hover(RenegadeEntity owner, int slot) {
        setOwner(owner);
        this.slot = slot;
        Vec3 at = hoverSpot(owner);
        moveTo(at.x, at.y, at.z, owner.getYRot(), -80);
    }

    private Vec3 hoverSpot(Entity owner) {
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        double side = (slot - 1) * 1.1;
        return owner.position().add(Math.cos(yaw) * side, 3.0 - Math.abs(slot - 1) * 0.35, Math.sin(yaw) * side);
    }

    public boolean launched() {
        return launched;
    }

    public void launch(Player target) {
        launched = true;
        Vec3 to = target.getEyePosition().add(0, -0.3, 0).subtract(position()).normalize();
        setDeltaMovement(to.scale(SPEED));
        setYRot((float) (Mth.atan2(to.x, to.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (!launched) {
            Entity o = getOwner();
            if (o == null || !o.isAlive()) {
                if (!level().isClientSide) discard();
                return;
            }
            Vec3 at = hoverSpot(o);
            setPos(at.x, at.y + Math.sin((tickCount + slot * 7) * 0.2) * 0.08, at.z);
            if (level() instanceof ServerLevel s && tickCount % 3 == 0) s.sendParticles(com.soulless.registry.ModParticles.BLOOD_EMBER.get(), getX(), getY(), getZ(), 1, 0.1, 0.3, 0.1, 0);
            return;
        }
        Vec3 v = getDeltaMovement();
        HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
            onHit(hit);
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (level() instanceof ServerLevel s) s.sendParticles(com.soulless.registry.ModParticles.BLOOD_EMBER.get(), getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0);
        if (++flight > 60 && !level().isClientSide) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return e instanceof Player && super.canHitEntity(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity o = getOwner();
        result.getEntity().hurt(damageSources().mobProjectile(this, o instanceof RenegadeEntity r ? r : null), DAMAGE);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 0.8F, 1.3F);
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
