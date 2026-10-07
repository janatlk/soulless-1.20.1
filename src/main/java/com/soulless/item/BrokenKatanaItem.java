package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Broken Legendary Katana: the sensei's ancestral blade, snapped generations ago when it was raised against an
 * innocent. Only Kitsune can mend it (world.Astral: sleep under a roof beside a white fox that trusts you, with this
 * blade on you). A Legendary Katana worn out to nothing turns back into this.
 */
public class BrokenKatanaItem extends Item {
    public BrokenKatanaItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.broken_katana").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soulless.broken_katana.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
