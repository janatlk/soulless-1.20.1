package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Scroll of Parrying: read it (right click) to learn the katana's parry for good - it survives death. The scroll
 * burns away in blue fox fire as it is read.
 */
public class ParryScrollItem extends Item {
    private static final String KEY = "soulless_parry_learned";

    public ParryScrollItem(Properties properties) {
        super(properties);
    }

    /** Learned parrying (kept in the player's persisted data, which Forge carries over death). */
    public static boolean knows(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);
    }

    private static void learn(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(KEY, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (knows(player)) {
            player.displayClientMessage(Component.translatable("message.soulless.parry_known"), true);
            return InteractionResultHolder.fail(stack);
        }
        learn(player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ServerLevel s = (ServerLevel) level;
        s.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        s.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 1.2F);
        s.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.7F);
        s.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.4F, 1.6F);
        s.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.5, 0.6, 0.5, 0.6);
        s.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.25, 0.25, 0.25, 0.04);
        player.displayClientMessage(Component.translatable("message.soulless.parry_learned").withStyle(ChatFormatting.AQUA), false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.parry_scroll").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soulless.parry_scroll.use").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }
}
