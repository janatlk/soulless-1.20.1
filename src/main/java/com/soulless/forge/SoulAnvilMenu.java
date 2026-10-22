package com.soulless.forge;

import com.soulless.registry.ModBlocks;
import com.soulless.registry.ModMenus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The Soul Anvil's screen (client.SoulAnvilScreen): no slots - the list of recipes reads the player's own
 * inventory. Forging is a menu button click (vanilla's container button packet): the button id is the recipe.
 */
public class SoulAnvilMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final Player owner;

    public SoulAnvilMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public SoulAnvilMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModMenus.SOUL_ANVIL.get(), id);
        this.access = access;
        this.owner = inv.player;
    }

    /** With no slots of its own the menu would not sync the player's inventory: forged items and spent ingots would
     * not show until it closes. Keep the inventory's own menu in step while this one is open. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof net.minecraft.server.level.ServerPlayer) owner.inventoryMenu.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!SoulAnvilRecipes.forge(player, id)) return false;
        access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.0F, 0.7F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.05,
                        pos.getZ() + 0.5, 18, 0.25, 0.05, 0.25, 0.03);
            }
        });
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SOUL_ANVIL.get());
    }
}
