package com.soulless.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/** One piece of the Leviathan's long body (its head or a body segment): hit it and the Leviathan is hit. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.soulless.Soulless.MODID)
public class LeviathanPart extends PartEntity<LeviathanEntity> {
    public final String name;
    private final EntityDimensions size;

    public LeviathanPart(LeviathanEntity parent, String name, float width, float height) {
        super(parent);
        this.name = name;
        this.size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || getParent() == e;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /**
     * A player's blow at a piece is struck at the Leviathan itself, as at any creature: weapon enchantments, crits,
     * the sweep, its red flash and its cry.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LeviathanPart part)) return;
        event.setCanceled(true);
        event.getEntity().attack(part.getParent());
    }
}
