package com.soulless.client.anvil;

import com.soulless.forge.SoulAnvilBlockEntity;
import com.soulless.forge.SoulAnvilRecipes;
import com.soulless.network.AnvilActionPacket;
import com.soulless.network.ModNetwork;
import com.soulless.upgrade.SoulVessels;
import com.soulless.upgrade.UpgradeTree;
import com.soulless.upgrade.Upgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The artifact laid on the anvil and its path: the item in a big diamond on the left, its tiers climbing on the
 * right from the first (bottom) to the last, each a diamond - or two or three side by side where the player picks a
 * style and the others close for good. Lit: bought; pulsing: can be bought now; grey: later or passed over.
 * Below: the souls in the vessels, reset (half back), melt down, take back.
 */
public class UpgradeScreen extends Screen {
    private static final float NODE = 18, ROW = 46, COL = 46;

    private final BlockPos pos;

    public UpgradeScreen(BlockPos pos) {
        super(Component.translatable("anvil.soulless.upgrade"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack held() {
        return minecraft.level.getBlockEntity(pos) instanceof SoulAnvilBlockEntity be ? be.held() : ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        if (held().isEmpty()) onClose();
    }

    private float treeX() {
        return width / 2F + 60;
    }

    private float freeX(int option, int options) {
        return treeX() + (option - (options - 1) / 2F) * (COL + 6);
    }

    private float nodeX(int option, int options) {
        return treeX() + (option - (options - 1) / 2F) * COL;
    }

    private float nodeY(int tier, int tiers) {
        return height / 2F + (tiers - 1) / 2F * ROW - tier * ROW;
    }

    private int buttonsY() {
        return height - 34;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xD8000000);
        var p = minecraft.player;
        ItemStack s = held();
        if (s.isEmpty()) return;
        UpgradeTree tree = Upgrades.tree(s);
        List<String> picked = Upgrades.picked(s);
        int bought = picked.size(), souls = SoulVessels.total(p);

        // the artifact
        float ax = width / 2F - 150, ay = Math.min(height / 2F - 10, 110);
        AnvilStyle.diamond(g, ax, ay, 54, AnvilStyle.FILL_HOVER, AnvilStyle.SOUL, AnvilStyle.SOUL_DIM);
        AnvilStyle.item(g, s, ax, ay, 3.2F);
        g.drawCenteredString(font, s.getHoverName().copy().withStyle(s.getRarity().getStyleModifier()), (int) ax, (int) ay + 64, AnvilStyle.TEXT);
        g.drawCenteredString(font, Component.translatable("anvil.soulless.souls", souls), (int) ax, (int) ay + 78, AnvilStyle.SOUL);
        if (tree != null) {
            g.drawCenteredString(font, Component.translatable("tooltip.soulless.upgrade.tier", bought, tree.size()), (int) ax, (int) ay - 68, AnvilStyle.GOLD);
        }

        // what the anvil has opened on it so far
        int oy = (int) ay + 96, ox = (int) ax - 110;
        g.drawString(font, Component.translatable("anvil.soulless.opened"), ox, oy, AnvilStyle.GOLD, false);
        oy += 12;
        if (picked.isEmpty()) g.drawString(font, Component.translatable("anvil.soulless.opened_none"), ox, oy, AnvilStyle.DIM, false);
        for (String id : picked) {
            if (oy > buttonsY() - 24) break;
            g.drawString(font, Component.literal("\u2714 ").append(Component.translatable("upgrade.soulless." + id)), ox, oy, AnvilStyle.SOUL, false);
            oy += 10;
            for (var seq : font.split(Component.translatable("upgrade.soulless." + id + ".desc"), 210)) {
                if (oy > buttonsY() - 14) break;
                g.drawString(font, seq, ox + 8, oy, AnvilStyle.DIM, false);
                oy += 9;
            }
            oy += 3;
        }

        List<Component> tip = null;
        var free = Upgrades.free(s);
        if (free != null) {
            g.drawCenteredString(font, Component.translatable("anvil.soulless.free_title"), (int) treeX(), height / 2 - 56, AnvilStyle.GOLD);
            for (int o = 0; o < free.size(); o++) {
                var f = free.get(o);
                float x = freeX(o, free.size()), y = height / 2F;
                boolean has = Upgrades.has(s, f.id()), can = !has && (souls >= f.souls() || p.getAbilities().instabuild);
                boolean hover = AnvilStyle.inDiamond(mx, my, x, y, NODE);
                int rim = has ? AnvilStyle.SOUL : can ? AnvilStyle.alpha(AnvilStyle.SOUL, 0.5F + 0.5F * AnvilStyle.pulse()) : AnvilStyle.RIM;
                AnvilStyle.diamond(g, x, y, NODE, hover ? AnvilStyle.FILL_HOVER : AnvilStyle.FILL, rim, has ? AnvilStyle.SOUL_DIM : 0);
                g.blit(com.soulless.Soulless.id("textures/gui/upgrade/" + f.id() + ".png"), (int) x - 8, (int) y - 8, 0, 0, 16, 16, 16, 16);
                g.drawCenteredString(font, has ? "\u2714" : String.valueOf(f.souls()), (int) x, (int) y + 24,
                        has ? AnvilStyle.SOUL : can ? AnvilStyle.SOUL : AnvilStyle.SHORT);
                if (hover) {
                    tip = new ArrayList<>();
                    tip.add(Component.translatable("upgrade.soulless." + f.id()).withStyle(has ? ChatFormatting.AQUA : ChatFormatting.WHITE));
                    for (var line : font.getSplitter().splitLines(Component.translatable("upgrade.soulless." + f.id() + ".desc"), 220,
                            net.minecraft.network.chat.Style.EMPTY))
                        tip.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
                    tip.add(has ? Component.translatable("anvil.soulless.owned").withStyle(ChatFormatting.AQUA)
                            : Component.translatable(can ? "anvil.soulless.buy" : "anvil.soulless.need", f.souls()).withStyle(can ? ChatFormatting.AQUA : ChatFormatting.RED));
                }
            }
            g.drawCenteredString(font, Component.translatable("anvil.soulless.free_note"), (int) treeX(), height / 2 + 44, AnvilStyle.DIM);
        } else if (tree == null) {
            g.drawString(font, Component.translatable("anvil.soulless.no_tree"), (int) treeX() - 80, height / 2 - 4, AnvilStyle.DIM, false);
        } else {
            int n = tree.size();
            // links first, under the diamonds
            for (int t = 1; t < n; t++) {
                var below = tree.tiers().get(t - 1).options();
                var here = tree.tiers().get(t).options();
                for (int a = 0; a < below.size(); a++) {
                    for (int b = 0; b < here.size(); b++) {
                        boolean lit = t < bought && picked.get(t - 1).equals(below.get(a)) && picked.get(t).equals(here.get(b));
                        AnvilStyle.dots(g, nodeX(a, below.size()), nodeY(t - 1, n) - NODE, nodeX(b, here.size()), nodeY(t, n) + NODE,
                                lit ? AnvilStyle.SOUL : AnvilStyle.RIM_DIM);
                    }
                }
            }
            for (int t = 0; t < n; t++) {
                UpgradeTree.Tier tier = tree.tiers().get(t);
                for (int o = 0; o < tier.options().size(); o++) {
                    String id = tier.options().get(o);
                    float x = nodeX(o, tier.options().size()), y = nodeY(t, n);
                    boolean has = t < bought && picked.get(t).equals(id), passed = t < bought && !has, next = t == bought;
                    boolean can = next && (souls >= tier.souls() || p.getAbilities().instabuild);
                    boolean hover = AnvilStyle.inDiamond(mx, my, x, y, NODE);
                    int rim = has ? AnvilStyle.SOUL : next ? (can ? AnvilStyle.alpha(AnvilStyle.SOUL, 0.5F + 0.5F * AnvilStyle.pulse()) : AnvilStyle.RIM)
                            : AnvilStyle.RIM_DIM;
                    AnvilStyle.diamond(g, x, y, NODE, hover ? AnvilStyle.FILL_HOVER : AnvilStyle.FILL, rim, has ? AnvilStyle.SOUL_DIM : 0);
                    g.blit(com.soulless.Soulless.id("textures/gui/upgrade/" + id + ".png"), (int) x - 8, (int) y - 8, 0, 0, 16, 16, 16, 16);
                    if (passed || t > bought) g.fill((int) x - 9, (int) y - 9, (int) x + 9, (int) y + 9, passed ? 0xB0000000 : 0x80000000);
                    if (o == 0) g.drawString(font, String.valueOf(tier.souls()), (int) (nodeX(tier.options().size() - 1, tier.options().size()) + NODE + 6),
                            (int) y - 4, t < bought ? AnvilStyle.DIM : can ? AnvilStyle.SOUL : AnvilStyle.SHORT, false);
                    if (hover) {
                        tip = new ArrayList<>();
                        tip.add(Component.translatable("upgrade.soulless." + id).withStyle(has ? ChatFormatting.AQUA : ChatFormatting.WHITE));
                        for (var line : font.getSplitter().splitLines(Component.translatable("upgrade.soulless." + id + ".desc"), 220,
                                net.minecraft.network.chat.Style.EMPTY)) {
                            tip.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
                        }
                        if (has) tip.add(Component.translatable("anvil.soulless.owned").withStyle(ChatFormatting.AQUA));
                        else if (passed) tip.add(Component.translatable("anvil.soulless.passed").withStyle(ChatFormatting.DARK_GRAY));
                        else if (next) tip.add(Component.translatable(can ? "anvil.soulless.buy" : "anvil.soulless.need", tier.souls())
                                .withStyle(can ? ChatFormatting.AQUA : ChatFormatting.RED));
                        else tip.add(Component.translatable("anvil.soulless.later", tier.souls()).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
        }

        // the bottom row
        int by = buttonsY(), bx = width / 2 - 225;
        int refund = 0;
        if (tree != null) for (int t = 0; t < bought; t++) refund += tree.tiers().get(t).souls() / 2;
        if (free != null) for (var f : free) if (Upgrades.has(s, f.id())) refund += f.souls() / 2;
        AnvilStyle.button(g, font, Component.translatable("anvil.soulless.reset", refund), bx, by, 140, mx, my, bought > 0);
        AnvilStyle.button(g, font, Component.translatable("anvil.soulless.melt_here", SoulAnvilRecipes.salvageValue(s)), bx + 155, by, 140, mx, my,
                SoulAnvilRecipes.salvageable(s));
        AnvilStyle.button(g, font, Component.translatable("anvil.soulless.take"), bx + 310, by, 140, mx, my, true);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ItemStack s = held();
        if (s.isEmpty()) return super.mouseClicked(mx, my, button);
        UpgradeTree tree = Upgrades.tree(s);
        int bought = Upgrades.tier(s);
        var free = Upgrades.free(s);
        if (free != null) {
            for (int o = 0; o < free.size(); o++) {
                if (AnvilStyle.inDiamond(mx, my, freeX(o, free.size()), height / 2F, NODE)) {
                    AnvilStyle.click();
                    ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.BUY, "", o));
                    return true;
                }
            }
        }
        if (tree != null && bought < tree.size()) {
            UpgradeTree.Tier tier = tree.tiers().get(bought);
            for (int o = 0; o < tier.options().size(); o++) {
                if (AnvilStyle.inDiamond(mx, my, nodeX(o, tier.options().size()), nodeY(bought, tree.size()), NODE)) {
                    AnvilStyle.click();
                    ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.BUY, "", o));
                    return true;
                }
            }
        }
        int by = buttonsY(), bx = width / 2 - 225;
        if (my >= by && my < by + 18) {
            if (mx >= bx && mx < bx + 140 && bought > 0) {
                AnvilStyle.click();
                ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.RESET, "", 0));
                return true;
            }
            if (mx >= bx + 155 && mx < bx + 295 && SoulAnvilRecipes.salvageable(s)) {
                AnvilStyle.click();
                ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.SALVAGE_HELD, "", 0));
                return true;
            }
            if (mx >= bx + 310 && mx < bx + 450) {
                AnvilStyle.click();
                ModNetwork.sendToServer(new AnvilActionPacket(pos, AnvilActionPacket.TAKE, "", 0));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
