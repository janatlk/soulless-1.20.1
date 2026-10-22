package com.soulless.client;

import com.soulless.forge.SoulAnvilMenu;
import com.soulless.forge.SoulAnvilRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Soul Anvil: every weapon of the mod on the left, the one picked on the right with what it takes (how many
 * the player has of each, red when short) and the Forge button. Drawn with flat fills - no texture.
 */
public class SoulAnvilScreen extends AbstractContainerScreen<SoulAnvilMenu> {
    private static final int W = 248, H = 160, ROW = 24, LIST_W = 104;
    private static final int BG = 0xF0141820, EDGE = 0xFF5E8FA8, PANEL = 0xFF1C222C, ROW_ON = 0xFF2C3A4A, ROW_HOVER = 0xFF232C38;
    private static final int TEXT = 0xFFDDEEF4, DIM = 0xFF8496A4, OK = 0xFF7FE0A0, SHORT = 0xFFE07070;

    private int selected;
    private Button forge;

    public SoulAnvilScreen(SoulAnvilMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        forge = addRenderableWidget(Button.builder(Component.translatable("container.soulless.soul_anvil.forge"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected);
        }).bounds(leftPos + W - 88, topPos + H - 28, 80, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        var p = minecraft.player;
        forge.active = p != null && (p.getAbilities().instabuild || SoulAnvilRecipes.ALL.get(selected).affordable(p));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos + 6, y = topPos + 20;
        for (int i = 0; i < SoulAnvilRecipes.ALL.size(); i++) {
            if (mx >= x && mx < x + LIST_W && my >= y + i * ROW && my < y + i * ROW + ROW - 2) {
                selected = i;
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int l = leftPos, t = topPos;
        g.fill(l - 1, t - 1, l + W + 1, t + H + 1, EDGE);
        g.fill(l, t, l + W, t + H, BG);
        g.fill(l + 4, t + 18, l + 8 + LIST_W, t + H - 4, PANEL);
        g.fill(l + 12 + LIST_W, t + 18, l + W - 4, t + H - 4, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 6, 6, TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        var p = minecraft.player;
        int l = leftPos, t = topPos;
        // the list
        for (int i = 0; i < SoulAnvilRecipes.ALL.size(); i++) {
            var r = SoulAnvilRecipes.ALL.get(i);
            int x = l + 6, y = t + 20 + i * ROW;
            boolean hover = mx >= x && mx < x + LIST_W && my >= y && my < y + ROW - 2;
            if (i == selected || hover) g.fill(x, y, x + LIST_W, y + ROW - 2, i == selected ? ROW_ON : ROW_HOVER);
            ItemStack icon = new ItemStack(r.result().get());
            g.renderItem(icon, x + 3, y + 3);
            boolean can = p != null && r.affordable(p);
            g.drawString(font, font.plainSubstrByWidth(icon.getHoverName().getString(), LIST_W - 26), x + 23, y + 7, can ? TEXT : DIM, false);
        }
        // the picked one
        var r = SoulAnvilRecipes.ALL.get(selected);
        ItemStack out = new ItemStack(r.result().get());
        int x = l + 16 + LIST_W, y = t + 22;
        g.renderItem(out, x, y);
        g.drawString(font, out.getHoverName().copy().withStyle(out.getRarity().getStyleModifier()), x + 20, y + 4, TEXT, false);
        g.drawString(font, Component.translatable("container.soulless.soul_anvil.needs"), x, y + 24, DIM, false);
        ItemStack hovered = null;
        for (int i = 0; i < r.costs().size(); i++) {
            var c = r.costs().get(i);
            ItemStack s = new ItemStack(c.item().get());
            int cy = y + 36 + i * 18;
            g.renderItem(s, x, cy);
            int have = p == null ? 0 : SoulAnvilRecipes.count(p, c.item().get());
            String line = Math.min(have, 999) + " / " + c.count() + "  " + s.getHoverName().getString();
            g.drawString(font, font.plainSubstrByWidth(line, W - LIST_W - 44), x + 20, cy + 4, have >= c.count() ? OK : SHORT, false);
            if (mx >= x && mx < x + 16 && my >= cy && my < cy + 16) hovered = s;
        }
        if (hovered != null) {
            g.renderTooltip(font, hovered, mx, my);
        } else if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            g.renderTooltip(font, out, mx, my);
        } else {
            renderTooltip(g, mx, my);
        }
        if (!forge.active && forge.isHovered()) {
            g.renderTooltip(font, Component.translatable("container.soulless.soul_anvil.short").withStyle(ChatFormatting.RED), mx, my);
        }
    }
}
