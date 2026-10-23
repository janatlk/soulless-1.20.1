package com.soulless.client.anvil;

import com.soulless.forge.SoulAnvilRecipes;
import com.soulless.forge.SoulAnvilRecipes.Category;
import com.soulless.forge.SoulAnvilRecipes.Recipe;
import com.soulless.network.AnvilActionPacket;
import com.soulless.network.ModNetwork;
import com.soulless.registry.ModItems;
import com.soulless.upgrade.SoulVessels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * The Soul Anvil's forge: the categories as a chain of diamonds, then every artifact of the picked one (all of them,
 * forgeable or not), then the card of one - its name, stats, what it costs and its whole description - with Forge,
 * which lays its ghost on the anvil to be filled by hand. The Souls category melts the player's artifacts down.
 */
public class ForgeScreen extends Screen {
    private static final float BIG = 26;

    private final BlockPos pos;
    private Category category;
    private Recipe picked;
    /** Souls category: the inventory slot picked for melting. */
    private int meltSlot = -1;
    /** The card's description, scrolled by the wheel. */
    private int descScroll, descMax;

    public ForgeScreen(BlockPos pos) {
        super(Component.translatable("container.soulless.soul_anvil"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack icon(Category c) {
        return switch (c) {
            case MELEE -> new ItemStack(ModItems.SOUL_KATANA.get());
            case RANGED -> new ItemStack(Items.BOW);
            case ARMOR -> new ItemStack(ModItems.LEVIATHAN_CHESTPLATE.get());
            case ACCESSORY -> new ItemStack(ModItems.SOUL_RING.get());
            case SUMMON -> new ItemStack(ModItems.RENEGADE_SIGIL.get());
            case SOULS -> new ItemStack(ModItems.SOUL_VESSEL.get());
        };
    }

    /** The chain's left edge: a column on the left, the card taking the rest. */
    private float chainX() {
        return Math.max(40, width / 2F - 215);
    }

    /** Where the i-th diamond of a chain sits: down the left, swinging left and right like links. */
    private float cx(int i, int n) {
        return chainX() + (i % 2 == 0 ? 0 : BIG);
    }

    /** Neighbours share an edge: each link is half a diagonal down and across from the last. */
    private float cy(int i, int n) {
        float top = Math.max(64, height / 2F - (n - 1) * BIG / 2);
        return top + i * BIG;
    }

    /** The back button sits right over the chain's first link. */
    private int backX() {
        return (int) (chainX() - BIG);
    }

    private int backY() {
        int n = category == Category.SOULS ? meltable().size() : category == null ? 0 : SoulAnvilRecipes.in(category).size();
        return Math.max(30, (int) (cy(0, Math.max(1, n)) - BIG - 22));
    }

    /** Labels stand in one column right of the chain. */
    private int labelX() {
        return (int) (chainX() + BIG * 2 + 8);
    }

    private List<Integer> meltable() {
        List<Integer> out = new ArrayList<>();
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) if (SoulAnvilRecipes.salvageable(inv.items.get(i))) out.add(i);
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xD8000000);
        var p = minecraft.player;
        Component head = category == null ? title : Component.translatable("anvil.soulless.category." + category.name().toLowerCase());
        g.drawString(font, head, (int) (chainX() - BIG), 14, AnvilStyle.SOUL, false);
        Component souls = Component.translatable("anvil.soulless.souls", SoulVessels.total(p));
        g.drawString(font, souls, width - 12 - font.width(souls), 14, AnvilStyle.SOUL, false);
        g.fill(12, 27, width - 12, 28, 0x406FE6F5);
        Component keys = Component.translatable(category == null ? "anvil.soulless.keys_top" : "anvil.soulless.keys");
        g.drawString(font, keys, 12, height - 12, AnvilStyle.DIM, false);
        if (category != null) AnvilStyle.button(g, font, Component.translatable("anvil.soulless.back"), backX(), backY(), 60, mx, my, true);

        Component hoverName = null;
        List<Component> hoverLines = null;
        if (category == null) {
            Category[] cs = Category.values();
            for (int i = 0; i < cs.length; i++) {
                float x = cx(i, cs.length), y = cy(i, cs.length);
                boolean hover = AnvilStyle.inDiamond(mx, my, x, y, BIG);
                AnvilStyle.diamond(g, x, y, BIG, hover ? AnvilStyle.FILL_HOVER : AnvilStyle.FILL, hover ? AnvilStyle.SOUL : AnvilStyle.RIM,
                        hover ? AnvilStyle.SOUL_DIM : 0);
                AnvilStyle.item(g, icon(cs[i]), x, y, 1.4F);
                int count = cs[i] == Category.SOULS ? meltable().size() : SoulAnvilRecipes.in(cs[i]).size();
                int ready = 0;
                if (cs[i] != Category.SOULS) for (Recipe r : SoulAnvilRecipes.in(cs[i])) if (r.affordable(p)) ready++;
                label(g, Component.translatable("anvil.soulless.category." + cs[i].name().toLowerCase()),
                        count == 0 ? Component.translatable("anvil.soulless.category_empty") : ready > 0 ? Component.translatable("anvil.soulless.count_ready", count, ready) : Component.translatable("anvil.soulless.count", count),
                        y, hover ? AnvilStyle.SOUL : count == 0 ? AnvilStyle.DIM : AnvilStyle.TEXT, ready > 0 ? AnvilStyle.OK : AnvilStyle.DIM);
            }
        } else if (category == Category.SOULS) {
            List<Integer> slots = meltable();
            if (slots.isEmpty()) g.drawString(font, Component.translatable("anvil.soulless.melt_none"), (int) (chainX() - BIG), height / 2, AnvilStyle.DIM, false);
            for (int i = 0; i < slots.size(); i++) {
                ItemStack s = p.getInventory().items.get(slots.get(i));
                float x = cx(i, slots.size()), y = cy(i, slots.size());
                boolean hover = AnvilStyle.inDiamond(mx, my, x, y, BIG), on = slots.get(i) == meltSlot;
                AnvilStyle.diamond(g, x, y, BIG, hover || on ? AnvilStyle.FILL_HOVER : AnvilStyle.FILL, on ? AnvilStyle.SOUL : AnvilStyle.RIM,
                        on ? AnvilStyle.SOUL_DIM : 0);
                AnvilStyle.item(g, s, x, y, 1.4F);
                label(g, s.getHoverName(), Component.translatable("anvil.soulless.melt_value", SoulAnvilRecipes.salvageValue(s)), y,
                        on || hover ? AnvilStyle.SOUL : AnvilStyle.TEXT, AnvilStyle.DIM);
                if (hover) {
                    hoverName = s.getHoverName();
                    hoverLines = List.of(Component.translatable("anvil.soulless.melt_value", SoulAnvilRecipes.salvageValue(s)).withStyle(ChatFormatting.AQUA));
                }
            }
            if (meltSlot >= 0 && meltSlot < p.getInventory().items.size() && SoulAnvilRecipes.salvageable(p.getInventory().items.get(meltSlot))) {
                ItemStack s = p.getInventory().items.get(meltSlot);
                int x0 = cardX(), y0 = height / 2 - 50;
                AnvilStyle.panel(g, x0, y0, x0 + 240, y0 + 100, AnvilStyle.RIM);
                g.drawString(font, s.getHoverName(), x0 + 12, y0 + 12, AnvilStyle.TEXT, false);
                g.drawWordWrap(font, Component.translatable("anvil.soulless.melt_explain", SoulAnvilRecipes.salvageValue(s)), x0 + 12, y0 + 30, 216, AnvilStyle.DIM);
                AnvilStyle.button(g, font, Component.translatable("anvil.soulless.melt"), x0 + 12, y0 + 72, 216, mx, my, true);
            }
        } else {
            List<Recipe> rs = SoulAnvilRecipes.in(category);
            if (rs.isEmpty()) g.drawString(font, Component.translatable("anvil.soulless.empty"), (int) (chainX() - BIG), height / 2, AnvilStyle.DIM, false);
            for (int i = 0; i < rs.size(); i++) {
                Recipe r = rs.get(i);
                float x = cx(i, rs.size()), y = cy(i, rs.size());
                boolean hover = AnvilStyle.inDiamond(mx, my, x, y, BIG), on = r == picked, can = r.affordable(p);
                AnvilStyle.diamond(g, x, y, BIG, hover || on ? AnvilStyle.FILL_HOVER : AnvilStyle.FILL,
                        on ? AnvilStyle.SOUL : can ? AnvilStyle.RIM : AnvilStyle.RIM_DIM, on ? AnvilStyle.SOUL_DIM : 0);
                AnvilStyle.item(g, new ItemStack(r.item()), x, y, 1.4F);
                label(g, new ItemStack(r.item()).getHoverName(), Component.translatable(can ? "anvil.soulless.ready" : "anvil.soulless.not_ready"), y,
                        on || hover ? AnvilStyle.SOUL : AnvilStyle.TEXT, can ? AnvilStyle.OK : AnvilStyle.DIM);
                if (hover) {
                    ItemStack s = new ItemStack(r.item());
                    hoverName = s.getHoverName();
                    hoverLines = stats(s);
                }
            }
            if (picked != null) card(g, mx, my);
        }
        if (hoverName != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(hoverName);
            if (hoverLines != null) lines.addAll(hoverLines);
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    /** Damage and attack speed, or armour and toughness, when the item has them. */
    private List<Component> stats(ItemStack s) {
        List<Component> out = new ArrayList<>();
        if (s.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
            out.add(Component.translatable("anvil.soulless.armor_stats", armor.getDefense(), fmt(armor.getToughness())).withStyle(ChatFormatting.GRAY));
            return out;
        }
        var mods = s.getAttributeModifiers(EquipmentSlot.MAINHAND);
        double dmg = 1, speed = 4;
        boolean any = false;
        for (var m : mods.get(Attributes.ATTACK_DAMAGE)) {
            dmg += m.getAmount();
            any = true;
        }
        for (var m : mods.get(Attributes.ATTACK_SPEED)) speed += m.getAmount();
        if (any) out.add(Component.translatable("anvil.soulless.stats", fmt(dmg), fmt(speed)).withStyle(ChatFormatting.GRAY));
        return out;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** The card stands right of the chain's labels. */
    private int cardX() {
        return labelX() + LABEL_W + 10;
    }

    private static final int LABEL_W = 116;

    /** A label beside a link: its name (cut to fit) and a line under it. */
    private void label(GuiGraphics g, Component name, Component sub, float y, int nameColor, int subColor) {
        g.drawString(font, fit(name.getString(), LABEL_W), labelX(), (int) y - 8, nameColor, false);
        if (sub != null) g.drawString(font, fit(sub.getString(), LABEL_W), labelX(), (int) y + 3, subColor, false);
    }

    /** The text cut to a width, an ellipsis where it was cut. */
    private String fit(String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, w - font.width("...")).stripTrailing() + "...";
    }

    /** The picked artifact's card: big icon, name, stats, costs, description, Forge. */
    private void card(GuiGraphics g, int mx, int my) {
        var p = minecraft.player;
        ItemStack s = new ItemStack(picked.item());
        int x0 = cardX(), y0 = 36, x1 = Math.min(width - 12, x0 + 300), y1 = height - 20;
        AnvilStyle.panel(g, x0, y0, x1, y1, AnvilStyle.RIM);
        AnvilStyle.diamond(g, x0 + 40, y0 + 40, 30, AnvilStyle.FILL_HOVER, AnvilStyle.SOUL, AnvilStyle.SOUL_DIM);
        AnvilStyle.item(g, s, x0 + 40, y0 + 40, 2.2F);
        g.drawString(font, s.getHoverName().copy().withStyle(s.getRarity().getStyleModifier()), x0 + 80, y0 + 22, AnvilStyle.TEXT, false);
        int ly = y0 + 34;
        for (Component c : stats(s)) {
            g.drawString(font, c, x0 + 80, ly, AnvilStyle.DIM, false);
            ly += 11;
        }
        // the price
        int y = y0 + 78;
        g.drawString(font, Component.translatable("anvil.soulless.price"), x0 + 12, y, AnvilStyle.GOLD, false);
        y += 12;
        List<Object[]> costs = new ArrayList<>();
        for (var c : picked.costs()) costs.add(new Object[]{new ItemStack(c.item().get()), SoulAnvilRecipes.count(p, c.item().get()), c.count()});
        if (picked.souls() > 0) costs.add(new Object[]{new ItemStack(ModItems.SOUL_VESSEL.get()), SoulVessels.total(p), picked.souls()});
        ItemStack hovered = null;
        for (Object[] c : costs) {
            int colW = x1 - x0 - 24;
            int cx = x0 + 12;
            ItemStack is = (ItemStack) c[0];
            int have = (int) c[1], need = (int) c[2];
            g.renderItem(is, cx, y);
            String name = is.is(ModItems.SOUL_VESSEL.get()) ? Component.translatable("anvil.soulless.souls_word").getString() : is.getHoverName().getString();
            g.drawString(font, fit(Math.min(have, 9999) + "/" + need + "  " + name, colW - 22), cx + 19, y + 4,
                    have >= need || p.getAbilities().instabuild ? AnvilStyle.OK : AnvilStyle.SHORT, false);
            if (mx >= cx && mx < cx + 16 && my >= y && my < y + 16) hovered = is;
            y += 18;
        }

        // the description, as the item itself tells it in full (no Shift needed), its sections set apart; the wheel scrolls it
        y += 4;
        int top = y, bottom = y1 - 34;
        ItemStack shown = s.copy();
        shown.hideTooltipPart(ItemStack.TooltipPart.MODIFIERS);
        com.soulless.client.ClientKeys.forceDetails = true;
        List<Component> lines;
        try {
            lines = shown.getTooltipLines(p, TooltipFlag.NORMAL);
        } finally {
            com.soulless.client.ClientKeys.forceDetails = false;
        }
        g.enableScissor(x0 + 4, top, x1 - 4, bottom);
        int ly2 = top - descScroll, total = 0;
        for (int i = 1; i < lines.size(); i++) {
            String raw = lines.get(i).getString();
            if (raw.startsWith(com.soulless.item.SoulForgedItem.SECTION_MARK)) {
                ly2 += 5;
                total += 5;
                g.drawString(font, Component.literal(raw.substring(com.soulless.item.SoulForgedItem.SECTION_MARK.length())), x0 + 12, ly2, AnvilStyle.GOLD, false);
                g.fill(x0 + 12, ly2 + 10, x1 - 12, ly2 + 11, 0x60D9B45A);
                ly2 += 14;
                total += 14;
                continue;
            }
            if (raw.isEmpty()) {
                ly2 += 5;
                total += 5;
                continue;
            }
            for (var seq : font.split(lines.get(i), x1 - x0 - 24)) {
                g.drawString(font, seq, x0 + 12, ly2, AnvilStyle.TEXT, false);
                ly2 += 10;
                total += 10;
            }
        }
        g.disableScissor();
        descMax = Math.max(0, total - (bottom - top));
        if (descMax > 0) {   // a thin bar showing where in the text we are
            int bh = Math.max(10, (bottom - top) * (bottom - top) / (total));
            int by = top + (int) ((bottom - top - bh) * (descScroll / (float) descMax));
            g.fill(x1 - 7, by, x1 - 5, by + bh, 0x806FE6F5);
        }
        boolean can = picked.affordable(p);
        AnvilStyle.button(g, font, Component.translatable(can ? "anvil.soulless.forge" : "anvil.soulless.forge_lay"), x0 + 12, y1 - 28, x1 - x0 - 24, mx, my, true);
        if (hovered != null) g.renderTooltip(font, hovered, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        var p = minecraft.player;
        // back: the button over the chain, or a right click anywhere
        if (category != null && (button == 1 || mx >= backX() && mx < backX() + 60 && my >= backY() && my < backY() + 18)) {
            AnvilStyle.click();
            if (picked != null || meltSlot >= 0) {
                picked = null;
                meltSlot = -1;
            } else {
                category = null;
            }
            return true;
        }
        if (category == null) {
            Category[] cs = Category.values();
            for (int i = 0; i < cs.length; i++) {
                if (AnvilStyle.inDiamond(mx, my, cx(i, cs.length), cy(i, cs.length), BIG)) {
                    AnvilStyle.click();
                    category = cs[i];
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }
        if (category == Category.SOULS) {
            List<Integer> slots = meltable();
            for (int i = 0; i < slots.size(); i++) {
                if (AnvilStyle.inDiamond(mx, my, cx(i, slots.size()), cy(i, slots.size()), BIG)) {
                    AnvilStyle.click();
                    meltSlot = slots.get(i);
                    return true;
                }
            }
            int x0 = cardX(), y0 = height / 2 - 50;
            if (meltSlot >= 0 && mx >= x0 + 12 && mx < x0 + 228 && my >= y0 + 72 && my < y0 + 90) {
                AnvilStyle.click();
                ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.SALVAGE_SLOT, "", meltSlot));
                meltSlot = -1;
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        List<Recipe> rs = SoulAnvilRecipes.in(category);
        for (int i = 0; i < rs.size(); i++) {
            if (AnvilStyle.inDiamond(mx, my, cx(i, rs.size()), cy(i, rs.size()), BIG)) {
                AnvilStyle.click();
                picked = rs.get(i);
                descScroll = 0;
                return true;
            }
        }
        if (picked != null) {
            int x0 = cardX(), x1 = Math.min(width - 12, x0 + 300), y1 = height - 20;
            if (mx >= x0 + 12 && mx < x1 - 12 && my >= y1 - 28 && my < y1 - 10) {
                AnvilStyle.click();
                ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.CHOOSE, picked.id(), 0));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Dev (autotest): show a category and maybe one of its cards, as if clicked. */
    public void devPick(Category c, Recipe r) {
        category = c;
        picked = r;
        descScroll = 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (picked != null && descMax > 0) {
            descScroll = net.minecraft.util.Mth.clamp(descScroll - (int) (delta * 20), 0, descMax);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && category != null) {
            if (picked != null || meltSlot >= 0) {
                picked = null;
                meltSlot = -1;
            } else {
                category = null;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
