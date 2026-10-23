package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.function.Supplier;

/** The items that lead to a boss show how hard it is (stars out of five); the Renegade's sigil also advises. */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class BossDifficulty {
    private static final Map<Supplier<Item>, Integer> STARS = Map.of(
            ModItems.EYE_OF_LAMENT::get, 2, ModItems.ABYSSAL_HEART::get, 2,   // the Wailing Reaper
            ModItems.RENEGADE_SIGIL::get, 3,                                 // the Renegade
            ModItems.EYE_OF_DROWNED::get, 4);                                 // the Leviathan

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        for (var e : STARS.entrySet()) {
            if (!event.getItemStack().is(e.getKey().get())) continue;
            int n = e.getValue();
            var lines = event.getToolTip();
            int at = Math.min(1, lines.size());
            lines.add(at, Component.translatable("tooltip.soulless.difficulty").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" " + "\u2605".repeat(n)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("\u2605".repeat(5 - n)).withStyle(ChatFormatting.DARK_GRAY)));
            if (event.getItemStack().is(ModItems.RENEGADE_SIGIL.get()))
                lines.add(at + 1, Component.translatable("tooltip.soulless.renegade_advice").withStyle(ChatFormatting.RED));
            return;
        }
    }

    private BossDifficulty() {}
}
