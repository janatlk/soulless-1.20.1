package com.soulless.client.anvil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The look shared by the Soul Anvil screens: black diamonds with a dotted rim, the item inside, a soul-blue glow for
 * what is lit and grey for what is closed - drawn with fills only, no textures.
 */
public final class AnvilStyle {
    public static final int FILL = 0xF2070A0F, FILL_HOVER = 0xF2101822, RIM = 0xFFE8F4F8, RIM_DIM = 0xFF4A5560, SOUL = 0xFF6FE6F5,
            SOUL_DIM = 0x806FE6F5, GOLD = 0xFFD9B45A, TEXT = 0xFFE6F2F6, DIM = 0xFF7D8C96, OK = 0xFF8CEBA8, SHORT = 0xFFF07A7A, SHADE = 0xC0000000;

    /** A diamond centred at (cx, cy) with half-diagonal r; rim: dotted border colour (0: none); glow: an outer halo (0: none). */
    public static void diamond(GuiGraphics g, float cx, float cy, float r, int fill, int rim, int glow) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
        float s = r / Mth.SQRT_OF_TWO;
        int h = Math.round(s);
        if (glow != 0) {
            for (int i = 3; i >= 1; i--) {
                int a = ((glow >>> 24) & 0xFF) * (4 - i) / 6;
                g.fill(-h - i * 2, -h - i * 2, h + i * 2, h + i * 2, (a << 24) | (glow & 0xFFFFFF));
            }
        }
        g.fill(-h, -h, h, h, fill);
        if (rim != 0) {
            int in = 2;
            for (int x = -h + in; x < h - in; x += 2) {   // dotted, a pixel in from the edge
                g.fill(x, -h + in, x + 1, -h + in + 1, rim);
                g.fill(x, h - in - 1, x + 1, h - in, rim);
                g.fill(-h + in, x, -h + in + 1, x + 1, rim);
                g.fill(h - in - 1, x, h - in, x + 1, rim);
            }
        }
        pose.popPose();
    }

    public static boolean inDiamond(double mx, double my, float cx, float cy, float r) {
        return Math.abs(mx - cx) + Math.abs(my - cy) <= r;
    }

    /** An item drawn at a size (16 px times scale) centred at (cx, cy). */
    public static void item(GuiGraphics g, ItemStack stack, float cx, float cy, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx - 8 * scale, cy - 8 * scale, 0);
        pose.scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /** A flat panel with a dotted rim. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int rim) {
        g.fill(x0, y0, x1, y1, FILL);
        for (int x = x0 + 2; x < x1 - 2; x += 2) {
            g.fill(x, y0 + 2, x + 1, y0 + 3, rim);
            g.fill(x, y1 - 3, x + 1, y1 - 2, rim);
        }
        for (int y = y0 + 2; y < y1 - 2; y += 2) {
            g.fill(x0 + 2, y, x0 + 3, y + 1, rim);
            g.fill(x1 - 3, y, x1 - 2, y + 1, rim);
        }
    }

    /** A button in the same style; returns whether the mouse is on it. */
    public static boolean button(GuiGraphics g, Font font, Component label, int x, int y, int w, int mx, int my, boolean active) {
        boolean hover = active && mx >= x && mx < x + w && my >= y && my < y + 18;
        panel(g, x, y, x + w, y + 18, active ? (hover ? SOUL : RIM) : RIM_DIM);
        if (hover) g.fill(x + 3, y + 3, x + w - 3, y + 15, 0x306FE6F5);
        g.drawCenteredString(font, label, x + w / 2, y + 5, active ? (hover ? SOUL : TEXT) : DIM);
        return hover;
    }

    /** A dotted line between two points. */
    public static void dots(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        float d = (float) Math.hypot(x1 - x0, y1 - y0);
        for (float t = 0; t <= d; t += 3) {
            int x = Math.round(Mth.lerp(t / d, x0, x1)), y = Math.round(Mth.lerp(t / d, y0, y1));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 1.0F));
    }

    /** A gentle pulse 0..1 for what can be done now. */
    public static float pulse() {
        return 0.5F + 0.5F * Mth.sin((System.currentTimeMillis() % 100000) / 300F);
    }

    public static int alpha(int color, float a) {
        return ((int) (((color >>> 24) & 0xFF) * a) << 24) | (color & 0xFFFFFF);
    }

    private AnvilStyle() {}
}
