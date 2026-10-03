package com.soulless.client;

import com.soulless.ability.RendTimeline;
import com.soulless.ability.SlamManager;
import com.soulless.item.SoulForgedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Two small ability slots right of the hotbar while the sword is held (Soul Crush key, Soul Rend RMB) with a
 * cooldown shade and seconds left, plus a charge bar under the crosshair during the Soul Rend wind-up.
 */
public final class AbilityHud implements IGuiOverlay {
    private static final int SLOT = 20;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !(player.getMainHandItem().getItem() instanceof SoulForgedItem item)) return;
        Font font = mc.font;

        int x = width / 2 + 91 + 8, y = height - SLOT - 2;
        float slam = player.getCooldowns().getCooldownPercent(item, partialTick);
        slot(g, font, x, y, Component.literal(ClientKeys.slamKeyName()), slam, Mth.ceil(slam * SlamManager.COOLDOWN_TICKS / 20F), 0xFF9A2CF0);
        // one cooldown shared by both abilities (the sword's)
        float rend = slam;
        long left = (long) Math.ceil(slam * SlamManager.COOLDOWN_TICKS);
        slot(g, font, x + SLOT + 4, y, Component.translatable("hud.soulless.rmb"), rend, Mth.ceil(left / 20F), 0xFFE8D25A);

        float t = ClientState.rendTicks(player);
        if (t >= 0 && t < RendTimeline.WINDUP) {
            int w = 80, bx = width / 2 - w / 2, by = height / 2 + 14;
            g.fill(bx - 1, by - 1, bx + w + 1, by + 4, 0xAA000000);
            g.fill(bx, by, bx + (int) (w * t / RendTimeline.WINDUP), by + 3, t > RendTimeline.WINDUP - 20 ? 0xFFF1D2FF : 0xFFB54CFF);
            String pct = Math.round(Math.max(RendTimeline.MIN_POWER, t / RendTimeline.WINDUP) * 100) + "%";
            g.drawString(font, pct, width / 2 - font.width(pct) / 2, by + 6, 0xFFE8C8FF, true);
        }
    }

    private static void slot(GuiGraphics g, Font font, int x, int y, Component label, float cooldown, int seconds, int accent) {
        g.fill(x, y, x + SLOT, y + SLOT, 0x99000000);
        g.fill(x, y + SLOT - 2, x + SLOT, y + SLOT, accent);
        if (cooldown > 0) {
            int h = (int) (SLOT * cooldown);
            g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, 0x55FFFFFF);
            String s = String.valueOf(seconds);
            g.drawString(font, s, x + (SLOT - font.width(s)) / 2, y + 6, 0xFFFFFFFF, true);
        } else {
            String s = label.getString();
            if (font.width(s) > SLOT - 2) s = s.substring(0, Math.min(3, s.length()));
            g.drawString(font, s, x + (SLOT - font.width(s)) / 2, y + 6, accent, true);
        }
    }
}
