package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The sensei's Training Katana, a wooden bokken: the katana's stance, cuts and thrust (and the parry once the sensei
 * has taught it), but little damage and no magic - no harakiri, no Kitsune's blessing; it wears out like any tool.
 */
public class TrainingKatanaItem extends SoulKatanaItem {
    /** 1 (base) + 5 (tier) - 3 = 3 attack damage. */
    private static final int DAMAGE_MODIFIER = -3;

    public TrainingKatanaItem(Properties properties) {
        super(properties, DAMAGE_MODIFIER);
    }

    @Override
    public boolean isTraining() {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.training_katana").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soulless.training_katana.stats", 1 + (int) getDamage()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soulless.training_katana.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
