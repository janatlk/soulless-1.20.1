package com.soulless.item;

import com.soulless.npc.dialogue.DialogueContext;
import com.soulless.registry.ModDamageTypes;
import com.soulless.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * The oath given to Kitsune (STORY_KATANA.md, act 4): the Legendary Katana never harms an innocent soul - animals
 * and villagers.
 * <ul>
 * <li>A side target of a cut that is innocent is simply passed by (no penalty: it only stood near the real target).</li>
 * <li>A blow aimed at an innocent does no harm; the blade loses {@link #BREACH_WEAR} durability and, while Kitsune is
 * still bound in the astral, the whole blow turns back on the one who struck.</li>
 * </ul>
 * Flags (player dialogue flags, kept through death): {@link #SWORN}, {@link #REFUSED}, {@link #FREED}.
 */
public final class KatanaOath {
    public static final String SWORN = "kitsune.oath", REFUSED = "kitsune.refused", FREED = "kitsune.freed";
    public static final int BREACH_WEAR = 50;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85F, 0.12F, 0.18F), 1.2F);

    public static boolean sworn(Player p) {
        return DialogueContext.playerFlags(p).getBoolean(SWORN);
    }

    public static boolean chose(Player p) {
        return sworn(p) || DialogueContext.playerFlags(p).getBoolean(REFUSED);
    }

    public static boolean innocent(LivingEntity e) {
        return e instanceof Animal || e instanceof WaterAnimal || e instanceof AbstractVillager;
    }

    /**
     * Before a katana blow lands on {@code target}: true = it must not land. {@code primary}: the blow was aimed at it
     * (the main target of a cut, the thrust's target), not a side target caught in the arc.
     */
    public static boolean stops(ServerPlayer player, LivingEntity target, boolean primary, float damage) {
        if (!player.getMainHandItem().is(ModItems.SOUL_KATANA.get()) || !sworn(player) || !innocent(target)) return false;
        if (!primary) return true;
        boolean freed = DialogueContext.playerFlags(player).getBoolean(FREED);
        if (!freed) player.hurt(ModDamageTypes.oath(player.level()), damage);
        player.serverLevel().sendParticles(RED, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.3, 0.3, 0);
        player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 1, 0.6F);
        player.displayClientMessage(Component.translatable("message.soulless.oath_breach").withStyle(ChatFormatting.RED), true);
        wear(player, BREACH_WEAR);
        return true;
    }

    /** Wears the katana in the main hand; at the end of its durability it does not vanish - it breaks in two. */
    public static void wear(ServerPlayer player, int amount) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SoulKatanaItem k)) return;
        if (player.getAbilities().instabuild) return;
        if (k.isTraining()) {
            stack.hurtAndBreak(amount, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            return;
        }
        int damage = stack.getDamageValue() + amount;
        if (damage >= stack.getMaxDamage() - 1) {
            player.setItemInHand(InteractionHand.MAIN_HAND, shatter(player, stack));
        } else {
            stack.setDamageValue(damage);
        }
    }

    /**
     * The katana breaks: the Broken Legendary Katana takes its place (sound, message). It keeps the katana's
     * enchantments and name - with Mending it can grow whole again once Kitsune is free (BrokenKatanaItem).
     */
    public static ItemStack shatter(ServerPlayer player, ItemStack katana) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1, 0.7F);
        player.displayClientMessage(Component.translatable("message.soulless.katana_shattered").withStyle(ChatFormatting.GRAY), true);
        ItemStack broken = new ItemStack(ModItems.BROKEN_KATANA.get());
        if (katana.hasTag()) {
            var tag = katana.getTag().copy();
            tag.remove("Damage");
            broken.setTag(tag);
        }
        return broken;
    }

    private KatanaOath() {}
}
