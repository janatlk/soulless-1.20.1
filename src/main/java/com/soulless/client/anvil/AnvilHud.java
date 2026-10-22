package com.soulless.client.anvil;

import com.soulless.Soulless;
import com.soulless.forge.SoulAnvilBlock;
import com.soulless.forge.SoulAnvilBlockEntity;
import com.soulless.forge.SoulAnvilRecipes;
import com.soulless.registry.ModItems;
import com.soulless.upgrade.SoulVessels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Looking at a Soul Anvil: a panel right of the crosshair in the anvil's own style. While forging - the artifact's
 * name and each thing it still needs (icon, laid on / needed, how many the player carries), the souls, and what a
 * click does; otherwise a hint of what a click does.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class AnvilHud {
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof SoulAnvilBlockEntity be)) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        int x = g.guiWidth() / 2 + 14, y = g.guiHeight() / 2 - 12;
        var p = mc.player;
        List<Runnable> rows = new ArrayList<>();
        Component head;
        Component hint;
        var r = be.recipe();
        if (r != null) {
            ItemStack out = new ItemStack(r.item());
            head = out.getHoverName().copy().withStyle(out.getRarity().getStyleModifier());
            hint = Component.translatable("anvil.soulless.hud.feed");
            int[] row = {0};
            for (int i = 0; i < r.costs().size(); i++) {
                var c = r.costs().get(i);
                int laid = be.filled(i), carry = SoulAnvilRecipes.count(p, c.item().get());
                rows.add(line(g, new ItemStack(c.item().get()), laid, c.count(), carry));
            }
            if (r.souls() > 0) rows.add(line(g, new ItemStack(ModItems.SOUL_VESSEL.get()), be.soulsPaid(), r.souls(), SoulVessels.total(p)));
        } else if (!be.held().isEmpty()) {
            head = be.held().getHoverName();
            hint = Component.translatable("anvil.soulless.hud.held");
        } else {
            head = Component.translatable("block.soulless.soul_anvil");
            hint = Component.translatable(SoulAnvilBlock.workable(p.getMainHandItem()) ? "anvil.soulless.hud.lay" : "anvil.soulless.hud.open");
        }
        int w = Math.max(font.width(head), font.width(hint)) + 16;
        w = Math.max(w, 120);
        int h = 30 + rows.size() * 18;
        AnvilStyle.panel(g, x, y, x + w, y + h, AnvilStyle.RIM);
        g.drawString(font, head, x + 8, y + 7, AnvilStyle.TEXT, false);
        for (int i = 0; i < rows.size(); i++) {
            ROW_Y = y + 19 + i * 18;
            ROW_X = x + 8;
            rows.get(i).run();
        }
        g.drawString(font, hint, x + 8, y + h - 11, AnvilStyle.DIM, false);
    }

    private static int ROW_X, ROW_Y;

    private static Runnable line(GuiGraphics g, ItemStack icon, int laid, int need, int carry) {
        return () -> {
            var font = Minecraft.getInstance().font;
            g.renderItem(icon, ROW_X, ROW_Y);
            boolean full = laid >= need;
            String text = laid + " / " + need;
            g.drawString(font, text, ROW_X + 20, ROW_Y + 4, full ? AnvilStyle.OK : AnvilStyle.TEXT, false);
            if (!full) {
                String have = Component.translatable("anvil.soulless.hud.carry", carry).getString();
                g.drawString(font, have, ROW_X + 26 + font.width(text), ROW_Y + 4, carry > 0 ? AnvilStyle.SOUL : AnvilStyle.SHORT, false);
            }
        };
    }

    private AnvilHud() {}
}
