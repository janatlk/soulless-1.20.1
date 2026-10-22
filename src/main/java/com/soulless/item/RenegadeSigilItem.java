package com.soulless.item;

import com.soulless.boss.RenegadeEncounter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * The Renegade's Sigil, forged at the Soul Anvil: broken (right click) it calls the Renegade (boss.RenegadeEncounter).
 * He comes only for a Legendary Katana - with none on the player the sigil stays whole.
 */
public class RenegadeSigilItem extends Item {
    public RenegadeSigilItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        boolean katana = p.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof SoulKatanaItem);
        if (!katana) {
            p.displayClientMessage(Component.translatable("message.soulless.sigil_no_katana").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (RenegadeEncounter.fighting(p) != null || RenegadeEncounter.in(p)) return InteractionResultHolder.fail(stack);
        if (!p.getAbilities().instabuild) stack.shrink(1);
        RenegadeEncounter.begin(p);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.soulless.renegade_sigil.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.soulless.renegade_sigil").withStyle(ChatFormatting.DARK_RED));
    }
}
