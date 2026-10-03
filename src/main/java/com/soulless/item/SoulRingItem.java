package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Soulsmith's Ring: worn with Soul Forged it empowers both of the sword's abilities (see {@link SoulRing}). */
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
        tooltip.add(Component.translatable(k + "slam", Math.round((SoulRing.SLAM_LEAP_UP - 1) * 100), Math.round((SoulRing.SLAM_DAMAGE_MULT - 1) * 100))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "slam_air").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "rend", Math.round((SoulRing.REND_SPEED - 1) * 100)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(k + "rend_recover").withStyle(ChatFormatting.GOLD));
    }
}
