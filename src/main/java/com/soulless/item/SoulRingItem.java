package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Soul Ring: a vessel worn on the hand - it catches and keeps up to {@link #CAPACITY} souls for the Soul Anvil
 * (in the inventory or a Curios ring slot), but has no ward to burn them on. Worn with Soul Forged it still gilds the
 * sword and lengthens its stuns (see {@link SoulRing}).
 */
public class SoulRingItem extends SoulVesselItem {
    public static final int CAPACITY = 1000;

    public SoulRingItem(Properties props) {
        super(props);
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    /** No ward: a right click does nothing. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** The ring the player wears in a ring slot (Curios), or empty. */
    public static ItemStack worn(Player player) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("curios")) return ItemStack.EMPTY;
        return CuriosCompat.find(player, com.soulless.registry.ModItems.SOUL_RING.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "tooltip.soulless.soul_ring.";
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.soulless.vessel.souls", souls(stack), CAPACITY).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.soulless.vessel.catch").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "no_ward").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(k + "with").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "stun", SoulRing.STUN_BONUS_TICKS / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "gold").withStyle(ChatFormatting.GOLD));
    }
}
