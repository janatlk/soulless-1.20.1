package com.soulless.item;

import com.soulless.Soulless;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Damage bonuses of Soul Forged:
 * <ul>
 * <li>Standing on soul sand / soul soil: +2 damage on normal hits, +20% on ability hits.</li>
 * <li>Harvested souls: every creature with a soul killed by the sword (hostiles, villagers, wandering
 * traders, golems; not spawner mobs) adds one to the sword's counter; +1% damage per 100 souls.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SoulBonus {
    public static final float SOUL_GROUND_HIT_BONUS = 2.0F;
    public static final float SOUL_GROUND_ABILITY_MULT = 1.2F;
    public static final int SOULS_PER_PERCENT = 100;
    private static final String SOULS_TAG = "soulless_souls";
    private static final String SPAWNER_TAG = "soulless_from_spawner";

    /** Set by abilities around their damage calls, so the soul-ground bonus knows which kind of hit it is. */
    private static boolean abilityHit;

    public static void abilityDamage(Runnable hit) {
        abilityHit = true;
        try {
            hit.run();
        } finally {
            abilityHit = false;
        }
    }

    public static int souls(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(SOULS_TAG);
    }

    public static float soulMultiplier(ItemStack stack) {
        return 1 + (souls(stack) / SOULS_PER_PERCENT) * 0.01F;
    }

    public static boolean onSoulGround(Player p) {
        return p.onGround() && p.level().getBlockState(p.getOnPos()).is(BlockTags.SOUL_SPEED_BLOCKS);
    }

    public static boolean hasSoul(LivingEntity e) {
        return e instanceof Enemy || e instanceof AbstractVillager || e instanceof AbstractGolem;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof SoulForgedItem)) return;
        float amount = event.getAmount();
        if (onSoulGround(p)) {
            amount = abilityHit ? amount * SOUL_GROUND_ABILITY_MULT : amount + SOUL_GROUND_HIT_BONUS;
        }
        event.setAmount(amount * soulMultiplier(stack));
    }

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().getPersistentData().putBoolean(SPAWNER_TAG, true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof SoulForgedItem) || !hasSoul(dead) || dead.getPersistentData().getBoolean(SPAWNER_TAG)) return;
        int before = souls(stack);
        int after = before + 1;
        stack.getOrCreateTag().putInt(SOULS_TAG, after);
        if (after / SOULS_PER_PERCENT > before / SOULS_PER_PERCENT) {
            p.displayClientMessage(Component.translatable("message.soulless.souls_milestone", after, after / SOULS_PER_PERCENT)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    private SoulBonus() {}
}
