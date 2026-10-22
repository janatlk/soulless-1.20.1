package com.soulless.item;

import com.soulless.Soulless;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The katana grows with its owner. Forged, it is a plain blade (weaker than iron); every creature with a soul it
 * kills (SoulBonus.hasSoul, not spawner mobs) adds one soul to that very blade - a katana that only hangs on the
 * wall stays asleep. At set counts it wakes a stage further, each stage harder hitting and with a new move:
 * <ol start="0">
 *   <li>Sleeping - the two cuts</li>
 *   <li>Awakened - the parry (RMB)</li>
 *   <li>Hungering - the thrust closing the combo</li>
 *   <li>Resonant - Soul Resonance (R)</li>
 *   <li>True - full strength, beyond netherite</li>
 * </ol>
 * Souls are only ever gained. Kept on the stack's tag, so they travel with the blade and reach every client.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class KatanaSouls {
    public static final String TAG = "soulless_katana_souls";
    /** Souls needed for each stage. */
    public static final int[] AT = {0, 50, 200, 500, 1000};
    public static final int TRUE = AT.length - 1, PARRY = 1, THRUST = 2, RESONANCE = 3;
    /** Attack damage at each stage (iron sword 6, diamond 7, netherite 8). */
    public static final float[] DAMAGE = {5, 6.5F, 7.5F, 8.5F, 10};

    public static int souls(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG);
    }

    public static int stage(ItemStack stack) {
        int n = souls(stack), s = 0;
        while (s < TRUE && n >= AT[s + 1]) s++;
        return s;
    }

    /** The katana in this player's main hand has reached the stage (false without one). */
    public static boolean has(Player player, int stage) {
        ItemStack s = player.getMainHandItem();
        return s.getItem() instanceof SoulKatanaItem && stage(s) >= stage;
    }

    public static void set(ItemStack stack, int souls) {
        stack.getOrCreateTag().putInt(TAG, Math.max(0, souls));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof SoulKatanaItem) || !SoulBonus.hasSoul(dead) || SoulBonus.fromSpawner(dead)) return;
        int before = stage(stack);
        set(stack, souls(stack) + 1);
        int after = stage(stack);
        if (after > before) awaken(p, after);
    }

    private static void awaken(ServerPlayer p, int stage) {
        p.displayClientMessage(Component.translatable("message.soulless.katana_stage", Component.translatable("tooltip.soulless.katana_stage." + stage))
                .withStyle(ChatFormatting.AQUA), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.6F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
        p.serverLevel().sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.04);
    }

    private KatanaSouls() {}
}
