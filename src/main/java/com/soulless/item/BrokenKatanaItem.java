package com.soulless.item;

import com.soulless.Soulless;
import com.soulless.npc.dialogue.DialogueContext;
import com.soulless.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Broken Legendary Katana: the sensei's ancestral blade, snapped generations ago when it was raised against an
 * innocent. Mending it:
 * <ul>
 * <li>while Kitsune is bound in the astral, she reforges it (world.Astral: sleep under a roof beside your sleeping
 * white fox with this blade on you);</li>
 * <li>once she is free (and the oath given), Mending does it: a broken blade with Mending (kept from the katana, or
 * put on at an anvil) drinks the experience its bearer picks up while holding it; at {@link #MEND_XP} it is whole
 * again, worn to a quarter.</li>
 * </ul>
 * A Legendary Katana worn out to nothing turns back into this, keeping its enchantments and name.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public class BrokenKatanaItem extends Item {
    public static final int MEND_XP = 300;
    private static final String MEND = "soulless_mend";

    public BrokenKatanaItem(Properties properties) {
        super(properties);
    }

    /** Only Mending can be put on it (at an anvil, with a book). */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.MENDING;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return EnchantmentHelper.getEnchantments(book).containsKey(Enchantments.MENDING);
    }

    private static boolean canMend(ServerPlayer p) {
        var f = DialogueContext.playerFlags(p);
        return f.getBoolean(KatanaOath.SWORN) && f.getBoolean(KatanaOath.FREED);
    }

    @SubscribeEvent
    public static void onXp(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !canMend(p)) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(hand);
            if (!s.is(ModItems.BROKEN_KATANA.get()) || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, s) <= 0) continue;
            int xp = s.getOrCreateTag().getInt(MEND) + event.getOrb().getValue();
            event.setCanceled(true);
            event.getOrb().discard();
            if (xp < MEND_XP) {
                s.getOrCreateTag().putInt(MEND, xp);
                p.displayClientMessage(Component.translatable("message.soulless.katana_mending", xp * 100 / MEND_XP).withStyle(ChatFormatting.GRAY), true);
                return;
            }
            ItemStack katana = new ItemStack(ModItems.SOUL_KATANA.get());
            var tag = s.getTag().copy();
            tag.remove(MEND);
            katana.setTag(tag);
            katana.setDamageValue(katana.getMaxDamage() * 3 / 4);
            p.setItemInHand(hand, katana);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.4, 0.3, 0.08);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1);
            p.displayClientMessage(Component.translatable("message.soulless.katana_mended").withStyle(ChatFormatting.AQUA), true);
            return;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.broken_katana").withStyle(ChatFormatting.GRAY));
        int xp = stack.hasTag() ? stack.getTag().getInt(MEND) : 0;
        if (xp > 0) tooltip.add(Component.translatable("tooltip.soulless.broken_katana.mend", xp * 100 / MEND_XP).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.soulless.broken_katana.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
