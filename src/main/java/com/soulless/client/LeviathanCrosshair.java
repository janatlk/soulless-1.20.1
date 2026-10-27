package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.boss.LeviathanPart;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Aiming at any piece of the Leviathan's body counts as aiming at the Leviathan: the attack indicator under the crosshair shows. */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class LeviathanCrosshair {
    @SubscribeEvent
    public static void onCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.crosshairPickEntity instanceof LeviathanPart part) mc.crosshairPickEntity = part.getParent();
    }

    private LeviathanCrosshair() {}
}
