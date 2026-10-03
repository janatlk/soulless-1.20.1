package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Soulsmith's Ring: worn with Soul Forged it gilds the sword and lengthens its stuns (see {@link SoulRing}). */
public class SoulRingItem extends Item {
    public SoulRingItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "tooltip.soulless.soul_ring.";
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(k + "with").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "stun", SoulRing.STUN_BONUS_TICKS / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "gold").withStyle(ChatFormatting.GOLD));
    }
}
