package com.soulless.item;

import com.soulless.boss.SeekingEyeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An eye that seeks a boss's structure, thrown like an Eye of Ender: it flies toward the nearest one and falls back
 * to the ground - never breaking, glowing where it lies and untouched by fire and lava. One for every summoning place.
 */
public class SeekingEyeItem extends Item {
    private final TagKey<Structure> seeks;
    private final String where;

    public SeekingEyeItem(Properties props, TagKey<Structure> seeks, String where) {
        super(props.fireResistant());
        this.seeks = seeks;
        this.where = where;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        BlockPos to = sl.findNearestMapStructure(seeks, player.blockPosition(), 100, false);
        if (to == null) {
            player.displayClientMessage(Component.translatable("message.soulless.eye_nothing").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        SeekingEyeEntity eye = new SeekingEyeEntity(sl, player.getX(), player.getY(0.5), player.getZ());
        eye.setItem(stack.copyWithCount(1));
        eye.signalTo(to);
        sl.addFreshEntity(eye);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, 0.5F, 0.6F);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 0.8F, 0.8F);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.swing(hand, true);
        return InteractionResultHolder.consume(stack);
    }

    /** Lying on the ground it glows, so it is easy to find again. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide && !entity.isCurrentlyGlowing()) entity.setGlowingTag(true);
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.eye." + where).withStyle(ChatFormatting.GRAY));
    }
}
