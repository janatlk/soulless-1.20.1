package com.soulless.client;

import com.soulless.Soulless;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Wailing Reaper's boss bar (the fight and the summoning ritual): a dark-metal frame with spiked, red-gemmed
 * end caps and a crown of spikes, a violet fill notched every 10% with a shimmer sliding along it, and the
 * name above in violet.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class ReaperBossBar {
    private static final ResourceLocation TEX = Soulless.id("textures/gui/reaper_boss_bar.png");
    private static final String KEY = "entity.soulless.wailing_reaper";
    private static final int FRAME_W = 256, FRAME_H = 20, SLOT_X = 26, SLOT_Y = 7, FILL_W = 204, FILL_H = 6;

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        var boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !tc.getKey().equals(KEY)) return;
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int cx = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getY();
        int x0 = cx - FRAME_W / 2, top = y + 2;

        // name above, in violet with a pair of stars
        Component name = Component.literal("✦ ").append(boss.getName()).append(" ✦");
        g.drawString(mc.font, name, cx - mc.font.width(name) / 2, y - 7, 0xD7B4FF, true);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();   // the frame's spikes and the shimmer are see-through
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(TEX, x0, top, 0, 0, FRAME_W, FRAME_H, 256, 64);
        int fill = Math.round(FILL_W * Mth.clamp(boss.getProgress(), 0, 1));
        int fx = x0 + SLOT_X, fy = top + SLOT_Y;
        if (fill > 0) {
            g.blit(TEX, fx, fy, 0, 24, fill, FILL_H, 256, 64);
            // a shimmer sweeping along the filled part
            float time = (mc.level != null ? mc.level.getGameTime() : 0) + event.getPartialTick();
            int sweep = FILL_W + 48;
            int sx = (int) ((time * 3) % sweep) - 24;
            int from = Math.max(0, sx), to = Math.min(fill, sx + 24);
            if (to > from) g.blit(TEX, fx + from, fy, from - sx, 32, to - from, FILL_H, 256, 64);
        }
        event.setIncrement(30);
    }

    private ReaperBossBar() {}
}
