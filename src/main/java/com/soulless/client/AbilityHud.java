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
 * Left of the hotbar, the Leviathan set's Breath of the Deep. Two small ability slots right of the hotbar while the sword is held (Soul Crush key, Soul Rend RMB) with a
 * cooldown shade and seconds left, plus a charge bar under the crosshair during the Soul Rend wind-up.
 */
public final class AbilityHud implements IGuiOverlay {
    private static final int SLOT = 20;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;
        // the Leviathan set's Breath of the Deep: a slot left of the hotbar (left of the off-hand slot when it is shown)
        if (com.soulless.ability.DeepDash.worn(player)) {
            var chest = com.soulless.registry.ModItems.LEVIATHAN_CHESTPLATE.get();
            float cd = player.getCooldowns().getCooldownPercent(chest, partialTick);
            int dx = width / 2 - 91 - 8 - SLOT - (player.getOffhandItem().isEmpty() ? 0 : 29);
            slot(g, mc.font, dx, height - SLOT - 2, com.soulless.client.ClientModEvents.DASH_KEY.getTranslatedKeyMessage(), cd,
                    Mth.ceil(cd * com.soulless.ability.DeepDash.cooldown(player) / 20F), 0xFF6FE6F5);
        }
        if (player.getMainHandItem().getItem() instanceof com.soulless.item.SoulKatanaItem katana) {
            katana(g, mc.font, player, katana, partialTick, width, height);
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof SoulForgedItem item)) return;
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

    /**
     * The katana: R - Soul Resonance (its cooldown), RMB - the parry (locked until the blade's stage reaches it, lit while the window is
     * open, shaded after a parry that caught nothing), and while the blessing lasts a third, blue slot with its seconds.
     */
    private static void katana(GuiGraphics g, Font font, Player player, com.soulless.item.SoulKatanaItem item, float partial, int width, int height) {
        int x = width / 2 + 91 + 8, y = height - SLOT - 2;
        float ritual = player.getCooldowns().getCooldownPercent(item, partial);
        if (!com.soulless.upgrade.Upgrades.has(player, com.soulless.upgrade.Upgrades.KATANA_RESONANCE)) {
            g.fill(x, y, x + SLOT, y + SLOT, 0x99000000);
            g.fill(x, y + SLOT - 2, x + SLOT, y + SLOT, 0xFF555555);
            g.drawString(font, "?", x + (SLOT - font.width("?")) / 2, y + 6, 0xFF888888, true);
        } else {
            slot(g, font, x, y, Component.literal(ClientKeys.slamKeyName()), ritual, Mth.ceil(ritual * ClientState.ritualCooldownTotal / 20F), 0xFF4FB6FF);
        }

        int px = x + SLOT + 4;
        if (!com.soulless.upgrade.Upgrades.has(player, com.soulless.upgrade.Upgrades.KATANA_PARRY)) {
            g.fill(px, y, px + SLOT, y + SLOT, 0x99000000);
            g.fill(px, y + SLOT - 2, px + SLOT, y + SLOT, 0xFF555555);
            g.drawString(font, "?", px + (SLOT - font.width("?")) / 2, y + 6, 0xFF888888, true);
        } else {
            float since = ClientState.gameTime() + partial - ClientState.parryStart;
            int window = com.soulless.item.KatanaParry.WINDOW_FROM + com.soulless.item.KatanaParry.WINDOW;
            float cd = !ClientState.parryCaught && since < com.soulless.item.KatanaParry.WHIFF_COOLDOWN
                    ? 1 - since / com.soulless.item.KatanaParry.WHIFF_COOLDOWN : 0;
            slot(g, font, px, y, Component.translatable("hud.soulless.rmb"), since < window ? 0 : cd,
                    Mth.ceil((1 - since / com.soulless.item.KatanaParry.WHIFF_COOLDOWN) * com.soulless.item.KatanaParry.WHIFF_COOLDOWN / 20F), 0xFFF4F8FF);
            if (since >= 0 && since < window) g.fill(px, y, px + SLOT, y + SLOT, 0x66FFFFFF);   // the window is open
        }

        var blessing = player.getEffect(com.soulless.registry.ModEffects.SOUL_RESONANCE.get());
        if (blessing != null) {
            int bx = px + SLOT + 4;
            g.fill(bx, y, bx + SLOT, y + SLOT, 0x99001A33);
            g.fill(bx, y + SLOT - 2, bx + SLOT, y + SLOT, 0xFF4FB6FF);
            String s = String.valueOf(Mth.ceil(blessing.getDuration() / 20F));
            g.drawString(font, s, bx + (SLOT - font.width(s)) / 2, y + 6, 0xFF9FD8FF, true);
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
