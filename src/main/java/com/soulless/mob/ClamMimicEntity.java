package com.soulless.mob;

import com.soulless.Soulless;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Clam Mimic: a giant clam crusted with barnacles that lies among the catacombs' treasure, looking like stone.
 * Shut, its shell turns most blows aside. It springs open when someone comes close or opens a chest near it: its
 * tongue lashes out and drags them in, and it bites. With no one in reach for a while it shuts again. Not strong -
 * a trap for the greedy, and a fair one: its shell creaks and bubbles rise from it.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public class ClamMimicEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(ClamMimicEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation CLOSED = RawAnimation.begin().thenLoop("animation.clam_mimic.closed");
    private static final RawAnimation OPEN_IDLE = RawAnimation.begin().thenLoop("animation.clam_mimic.open_idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.clam_mimic.death");
    /** Shut, it takes this share of a blow. */
    private static final float SHUT_TAKEN = 0.2F;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int openTicks, idleOpen, attackCd;

    public ClamMimicEntity(EntityType<? extends ClamMimicEntity> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OPEN, false);
    }

    public boolean open() {
        return entityData.get(OPEN);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /** Wakes it: it springs open. */
    public void wake() {
        if (open() || isDeadOrDying()) {
            idleOpen = 0;
            return;
        }
        entityData.set(OPEN, true);
        openTicks = 0;
        idleOpen = 0;
        attackCd = 12;
        triggerAnim("act", "open");
        level().playSound(null, blockPosition(), SoundEvents.SHULKER_OPEN, SoundSource.HOSTILE, 1.2F, 0.6F);
        level().playSound(null, blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 1F, 0.8F);
    }

    private void shut() {
        entityData.set(OPEN, false);
        triggerAnim("act", "close");
        level().playSound(null, blockPosition(), SoundEvents.SHULKER_CLOSE, SoundSource.HOSTILE, 1F, 0.6F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (level().isClientSide) {
            if (!open() && random.nextInt(90) == 0) level().addParticle(ParticleTypes.BUBBLE, getX(), getY() + 0.6, getZ(), 0, 0.05, 0);
            return;
        }
        if (isDeadOrDying()) return;
        Player near = level().getNearestPlayer(this, open() ? 7 : 4);
        if (near != null && (near.isCreative() || near.isSpectator())) near = null;
        if (!open()) {
            if (near != null) wake();
            if (tickCount % 120 == 0 && random.nextInt(3) == 0)
                level().playSound(null, blockPosition(), SoundEvents.WOOD_STEP, SoundSource.HOSTILE, 0.4F, 0.5F);   // it creaks
            return;
        }
        openTicks++;
        if (attackCd > 0) attackCd--;
        if (near == null) {
            if (++idleOpen > 100) shut();
            return;
        }
        idleOpen = 0;
        getLookControl().setLookAt(near);
        double d = distanceTo(near);
        if (attackCd == 0 && d < 2.6) {
            triggerAnim("act", "bite");
            attackCd = 30;
            near.hurt(damageSources().mobAttack(this), 5);
            level().playSound(null, blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1F, 1.2F);
        } else if (attackCd == 0 && d < 7 && hasLineOfSight(near)) {
            // the tongue: out at them, then they are dragged in toward the jaws
            triggerAnim("act", "lash");
            attackCd = 45;
            Vec3 pull = position().add(0, 0.4, 0).subtract(near.position()).normalize().scale(0.9).add(0, 0.25, 0);
            near.setDeltaMovement(near.getDeltaMovement().add(pull));
            near.hurtMarked = true;
            level().playSound(null, blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.2F, 0.5F);
            if (level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.BUBBLE, near.getX(), near.getY() + 0.8, near.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!open() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= SHUT_TAKEN;
            if (!level().isClientSide) {
                level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 0.7F);
                wake();   // stung open
            }
        }
        return super.hurt(source, amount);
    }

    /** A chest opened near a shut mimic wakes it. */
    @SubscribeEvent
    public static void onChest(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (level.isClientSide || !(level.getBlockEntity(event.getPos()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)) return;
        for (ClamMimicEntity m : level.getEntitiesOfClass(ClamMimicEntity.class, new net.minecraft.world.phys.AABB(event.getPos()).inflate(6)))
            m.wake();
    }

    @Override
    protected void tickDeath() {
        if (++deathTime >= 30 && !level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SHULKER_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.SHULKER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Open", open());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OPEN, tag.getBoolean("Open"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(open() ? OPEN_IDLE : CLOSED);
        }));
        controllers.add(new AnimationController<>(this, "act", 2, state -> PlayState.STOP)
                .triggerableAnim("open", RawAnimation.begin().thenPlay("animation.clam_mimic.open"))
                .triggerableAnim("close", RawAnimation.begin().thenPlay("animation.clam_mimic.close"))
                .triggerableAnim("lash", RawAnimation.begin().thenPlay("animation.clam_mimic.lash"))
                .triggerableAnim("bite", RawAnimation.begin().thenPlay("animation.clam_mimic.bite")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
