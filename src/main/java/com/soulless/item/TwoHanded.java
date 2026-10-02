package com.soulless.item;

import com.soulless.Soulless;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Soul Forged takes both hands: while it is in the main hand, the off-hand item cannot be used at all
 * (no shield blocking, no placing, eating or interacting with it). It is also hidden when rendering.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class TwoHanded {
    private static boolean blocked(Player player, InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof SoulForgedItem;
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity(), event.getHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity(), event.getHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseOnEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blocked(event.getEntity(), event.getHand())) event.setCanceled(true);
    }

    private TwoHanded() {}
}
