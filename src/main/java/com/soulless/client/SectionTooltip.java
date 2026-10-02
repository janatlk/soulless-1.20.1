package com.soulless.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** A tooltip section header: "── Label ──" with violet rules fading outward and a small diamond on each side. */
public record SectionTooltip(String label) implements TooltipComponent, ClientTooltipComponent {
    private static final int TEXT = 0xFFE3B8FF;

    @Override
    public int getHeight() {
        return 13;
    }

    @Override
    public int getWidth(Font font) {
        return font.width(label) + 40;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        // rules span the whole tooltip width; the tooltip is at least as wide as this component
        int total = Math.max(getWidth(font), currentWidth);
        int tw = font.width(label);
        int cx = x + total / 2;
        int ly = y + 6;
        int leftEnd = cx - tw / 2 - 6, rightStart = cx + tw / 2 + 6;
        rule(g, x, leftEnd, ly, true);
        rule(g, rightStart, x + total, ly, false);
        diamond(g, leftEnd - 1, ly);
        diamond(g, rightStart, ly);
    }

    @Override
    public void renderText(Font font, int x, int y, org.joml.Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
        int total = Math.max(getWidth(font), currentWidth);
        int tx = x + total / 2 - font.width(label) / 2;
        font.drawInBatch(label, tx, y + 2, TEXT, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    /** Tooltip content width for the current draw, set by {@link SoulTooltip} before rendering. */
    static int currentWidth;

    private static void rule(GuiGraphics g, int from, int to, int y, boolean fadeLeft) {
        int len = Math.max(1, to - from);
        for (int i = 0; i < len; i++) {
            float k = fadeLeft ? i / (float) len : 1 - i / (float) len;   // 1 near the label
            int a = (int) (40 + 215 * k);
            g.fill(from + i, y, from + i + 1, y + 1, a << 24 | 0xB54CFF);
        }
    }

    private static void diamond(GuiGraphics g, int x, int y) {
        g.fill(x, y - 1, x + 1, y + 2, 0xFFF1D2FF);
        g.fill(x - 1, y, x + 2, y + 1, 0xFFF1D2FF);
    }
}
