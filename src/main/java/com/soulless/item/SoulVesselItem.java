package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Soul Vessel: an urn of Wraith Iron that catches the souls of what its owner kills (upgrade.SoulVessels) - the
 * currency of the Soul Anvil. Right click burns {@link #WARD_COST} of them for {@link #WARD_TICKS} of Resistance IV
 * (80% less damage), then rests {@link #COOLDOWN} ticks.
 */
public class SoulVesselItem extends Item {
    public static final int MAX = 256, WARD_COST = 10, WARD_TICKS = 5 * 20, COOLDOWN = 25 * 20;
    private static final String TAG = "soulless_souls";

    public SoulVesselItem(Properties props) {
        super(props);
    }

    public static int souls(ItemStack stack) {
        var t = stack.getTag();
        return t == null ? 0 : t.getInt(TAG);
    }

    public static void set(ItemStack stack, int n) {
        stack.getOrCreateTag().putInt(TAG, Mth.clamp(n, 0, max(stack)));
    }

    /** How many souls this holds (the Soul Ring more than a vessel). */
    public int capacity() {
        return MAX;
    }

    public static int max(ItemStack stack) {
        return stack.getItem() instanceof SoulVesselItem v ? v.capacity() : MAX;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (souls(stack) < WARD_COST) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.soulless.vessel_empty", WARD_COST)
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            set(stack, souls(stack) - WARD_COST);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, WARD_TICKS, 3, false, true, true));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.5F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
            ((net.minecraft.server.level.ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return souls(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * souls(stack) / max(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5FE0F0;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return souls(stack) >= max(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.vessel.souls", souls(stack), max(stack)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.soulless.vessel.catch").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soulless.vessel.ward", Component.translatable("hud.soulless.rmb"), WARD_COST,
                WARD_TICKS / 20, COOLDOWN / 20).withStyle(ChatFormatting.DARK_AQUA));
    }
}
