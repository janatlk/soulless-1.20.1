package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A Soul Forged crafting material. Lore and source come from the lang file ("<descriptionId>.desc"). */
public class SoulMaterialItem extends Item {
    private final boolean foil;

    public SoulMaterialItem(Properties properties, boolean foil) {
        super(properties);
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (stack.is(com.soulless.registry.ModItems.ABYSSAL_HEART.get())) {
            tooltip.add(Component.translatable("tooltip.soulless.material.altar").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        tooltip.add(Component.translatable("tooltip.soulless.material.source").withStyle(ChatFormatting.DARK_GRAY));
    }
}
