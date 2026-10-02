package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.item.SoulForgedItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Custom tooltip frame for Soulless items: a deep violet panel with a lilac-to-violet border, glowing corner
 * brackets, a soul gem on the top edge and a fading rule under the title. For the sword the bulky vanilla
 * "When in Main Hand" attribute block is dropped (the item prints a one-line stat summary instead).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class SoulTooltip {
    private static final int BG_TOP = 0xF2160B22, BG_BOTTOM = 0xF208040F;
    private static final int BORDER_TOP = 0xFFC46CFF, BORDER_BOTTOM = 0xFF3B0A66;
    private static final int ACCENT = 0xFFF1D2FF, ACCENT_DIM = 0xFFB54CFF;

    private static boolean ours(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && Soulless.MODID.equals(id.getNamespace());
    }

    @SubscribeEvent
    public static void onTooltipLines(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof SoulForgedItem)) return;
        List<Component> lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getContents() instanceof TranslatableContents t && t.getKey().startsWith("item.modifiers.")) {
                int from = i > 0 && lines.get(i - 1).getString().isEmpty() ? i - 1 : i;
                int to = i + 1;
                while (to < lines.size() && (lines.get(to).getString().startsWith(" ") || isModifierLine(lines.get(to)))) to++;
                lines.subList(from, to).clear();
                i = from - 1;
            } else if (isModifierLine(lines.get(i))) {
                // Forge prints non-base attributes (reach, speed) without the vanilla indent
                lines.remove(i--);
            }
        }
    }

    private static boolean isModifierLine(Component line) {
        if (line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("attribute.modifier.")) return true;
        for (Component sib : line.getSiblings()) {
            if (sib.getContents() instanceof TranslatableContents t && t.getKey().startsWith("attribute.modifier.")) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!ours(event.getItemStack())) return;
        event.setBackgroundStart(BG_TOP);
        event.setBackgroundEnd(BG_BOTTOM);
        event.setBorderStart(BORDER_TOP);
        event.setBorderEnd(BORDER_BOTTOM);
    }

    @SubscribeEvent
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        if (!ours(event.getItemStack())) return;
        List<ClientTooltipComponent> comps = event.getComponents();
        int w = 0, h = comps.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent c : comps) {
            w = Math.max(w, c.getWidth(event.getFont()));
            h += c.getHeight();
        }
        // same placement as vanilla's DefaultTooltipPositioner
        int x = event.getX() + 12, y = event.getY() - 12;
        if (x + w > event.getScreenWidth()) x = Math.max(x - 24 - w, 4);
        if (y + h + 3 > event.getScreenHeight()) y = event.getScreenHeight() - h - 3;

        GuiGraphics g = event.getGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 405);   // above the panel (z 400), level with the text
        int l = x - 4, r = x + w + 3, t = y - 4, b = y + h + 3;
        corner(g, l, t, 1, 1);
        corner(g, r, t, -1, 1);
        corner(g, l, b, 1, -1);
        corner(g, r, b, -1, -1);
        // soul gem on the top edge
        int cx = x + w / 2;
        g.fill(cx - 1, t - 2, cx + 1, t + 3, ACCENT_DIM);
        g.fill(cx - 2, t - 1, cx + 2, t + 2, ACCENT_DIM);
        g.fill(cx - 1, t - 1, cx + 1, t + 2, ACCENT);
        // fading rule under the title line
        if (comps.size() > 1) {
            int ry = y + comps.get(0).getHeight();
            for (int i = 0; i < w; i++) {
                float fade = 1 - Math.abs(i - w / 2F) / (w / 2F);
                int a = (int) (0xC0 * fade);
                g.fill(x + i, ry, x + i + 1, ry + 1, a << 24 | 0xB54CFF);
            }
        }
        g.pose().popPose();
    }

    /** A small glowing L-bracket sitting on a corner of the border, pointing inward along (dx, dy). */
    private static void corner(GuiGraphics g, int x, int y, int dx, int dy) {
        int len = 6;
        int x0 = Math.min(x, x + dx * len), x1 = Math.max(x, x + dx * len) + 1;
        int y0 = Math.min(y, y + dy * len), y1 = Math.max(y, y + dy * len) + 1;
        g.fill(x0, y, x1, y + 1, ACCENT);
        g.fill(x, y0, x + 1, y1, ACCENT);
        g.fill(x + dx * 2, y + dy * 2, x + dx * 2 + 1, y + dy * 2 + 1, ACCENT_DIM);
    }

    private SoulTooltip() {}
}
