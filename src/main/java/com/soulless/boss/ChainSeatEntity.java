package com.soulless.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Renegade's chains (boss.RenegadeEncounter, when the player has lost): an unseen seat on the ground the player is
 * forced down onto - sitting, as in a boat - with dark chains drawn from four anchors in the floor to their chest
 * (client.ChainSeatRenderer). The player cannot get up while it is {@link #locked()}.
 */
@Mod.EventBusSubscriber(modid = com.soulless.Soulless.MODID)
public class ChainSeatEntity extends Entity {
    private boolean locked = true;

    public ChainSeatEntity(EntityType<? extends ChainSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public boolean locked() {
        return locked;
    }

    /** Lets the player go and vanishes. */
    public void release() {
        locked = false;
        ejectPassengers();
        discard();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.12;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 20 && getPassengers().isEmpty()) discard();
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** No getting up out of the chains. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof ChainSeatEntity seat && seat.locked && seat.isAlive()) {
            event.setCanceled(true);
        }
    }
}
