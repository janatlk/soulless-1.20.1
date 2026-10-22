package com.soulless.item;

import com.soulless.Soulless;
import com.soulless.network.KatanaAnimPacket;
import com.soulless.network.ModNetwork;
import com.soulless.registry.ModEffects;
import com.soulless.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The katana's ability (R): the player kneels, turns the blade on themselves and drives it through their own
 * belly - losing three hearts, which can kill them ("died with honour") - to receive the Kitsune's blessing (ModEffects.SOUL_RESONANCE):
 * <ul>
 *   <li>the katana moves 25% faster, and its thrust dashes further;</li>
 *   <li>its hits heal 5% of the damage dealt;</li>
 *   <li>physical blows at the player (melee, arrows and other projectiles) miss one time in four - the Wailing
 *       Reaper's attacks never miss.</li>
 * </ul>
 * Being hit before the blade goes in breaks the ritual (no cost, no blessing, a short cooldown).
 * The animation is client.KatanaAnim's "ritual" (KatanaAnimPacket step 3; -1 stops it).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class ResonanceRitual {
    public static final int RITUAL = 3, STOP = -1;
    /** Ritual length and the tick the blade goes in (client.KatanaPoses "ritual" timeline). */
    public static final int TOTAL = 46, STAB_AT = 19;
    public static final int BLESSING_SECONDS = 20;
    public static final int COOLDOWN_TICKS = 60 * 20, INTERRUPTED_COOLDOWN = 3 * 20;
    /** The blade costs three hearts, always - and it can kill. */
    public static final float SELF_DAMAGE = 6.0F;
    public static final float LIFESTEAL = 0.05F, MISS_CHANCE = 0.25F;

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();
    /** The player whose own blade is being driven in right now (its damage must not break the ritual). */
    private static Player stabbing;

    public static boolean isActive(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void tryStart(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof SoulKatanaItem item) || !player.isAlive() || player.isSpectator()) return;
        if (!KatanaSouls.has(player, KatanaSouls.RESONANCE)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.soulless.katana_locked",
                    net.minecraft.network.chat.Component.translatable("tooltip.soulless.katana_stage." + KatanaSouls.RESONANCE)), true);
            return;
        }
        if (isActive(player) || player.getCooldowns().isOnCooldown(item)) return;
        if (!player.onGround() || player.isInWater()) {
            player.displayClientMessage(Component.translatable("message.soulless.ritual_ground"), true);
            return;
        }
        ACTIVE.put(player.getUUID(), player.level().getGameTime());
        player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);
        broadcast(player, RITUAL);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.6F, 0.9F);
    }

    private static void broadcast(ServerPlayer player, int step) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new KatanaAnimPacket(player.getId(), step, 1));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Iterator<Map.Entry<UUID, Long>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            long t = player.level().getGameTime() - e.getValue();
            if (!(player.getMainHandItem().getItem() instanceof SoulKatanaItem) && t < STAB_AT) {
                it.remove();
                broadcast(player, STOP);
                continue;
            }
            if (t == STAB_AT) stab(player);
            if (t >= TOTAL) it.remove();
        }
        // the blessing's fox fire flickers around everyone who carries it
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (p.tickCount % 3 == 0 && p.hasEffect(ModEffects.SOUL_RESONANCE.get())) {
                // on a ring around the legs, so it never sits in front of the player's own eyes
                double a = p.getRandom().nextDouble() * Math.PI * 2, r = 0.55 + p.getRandom().nextDouble() * 0.15;
                p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(a) * r, p.getY() + 0.1 + p.getRandom().nextDouble() * 0.8,
                        p.getZ() + Math.sin(a) * r, 1, 0.02, 0.05, 0.02, 0.005);
            }
        }
    }

    /** The blade goes in: three hearts (armor, enchantments and resistance don't help), then the blessing. */
    private static void stab(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        com.soulless.CombatTrial.onRitual(player);
        com.soulless.Achievements.grant(player, "kitsune_ritual");
        stabbing = player;
        player.invulnerableTime = 0;
        player.hurt(com.soulless.registry.ModDamageTypes.seppuku(level), SELF_DAMAGE);
        stabbing = null;
        if (!player.isAlive()) {   // died with honour
            com.soulless.Achievements.grant(player, "died_with_honour");
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.SOUL_RESONANCE.get(), BLESSING_SECONDS * 20, 0, false, true, true));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_HIT.get(), SoundSource.PLAYERS, 0.5F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SOUL_RESONANCE.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
        // the burst itself is drawn on each client (client.KatanaRitualFx.stab); a few souls rise from the ground
        Vec3 c = player.position().add(0, 0.2, 0);
        level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 8, 0.6, 0.1, 0.6, 0.03);
    }

    /** Hit before the blade goes in: the ritual breaks. Under the blessing: physical blows may miss. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DamageSource src = event.getSource();
        if (player == stabbing) return;
        Long start = ACTIVE.get(player.getUUID());
        if (start != null && player.level().getGameTime() - start < STAB_AT && event.getAmount() > 0) {
            ACTIVE.remove(player.getUUID());
            broadcast(player, STOP);
            if (player.getMainHandItem().getItem() instanceof SoulKatanaItem item) {
                player.getCooldowns().addCooldown(item, INTERRUPTED_COOLDOWN);
            }
            player.displayClientMessage(Component.translatable("message.soulless.ritual_broken"), true);
            return;
        }
        if (player.hasEffect(ModEffects.SOUL_RESONANCE.get()) && isPhysical(src) && player.getRandom().nextFloat() < MISS_CHANCE) {
            event.setCanceled(true);
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_SWING.get(), SoundSource.PLAYERS, 0.3F, 1.4F);
            player.displayClientMessage(Component.translatable("message.soulless.kitsune_miss"), true);
        }
    }

    /**
     * A physical blow: struck by a creature's own hand or weapon, or by a projectile (arrows, tridents...).
     * Explosions, magic and everything the Wailing Reaper does are not.
     */
    private static boolean isPhysical(DamageSource src) {
        Entity direct = src.getDirectEntity(), attacker = src.getEntity();
        if (src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.BYPASSES_ARMOR)) return false;
        if (attacker instanceof com.soulless.boss.WailingReaperEntity) return false;
        if (direct instanceof Projectile) return true;
        return direct != null && direct == attacker && attacker instanceof LivingEntity;
    }

    /** Under the blessing, everything the player hurts heals them a little. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && p != event.getEntity()
                && p.hasEffect(ModEffects.SOUL_RESONANCE.get()) && event.getAmount() > 0) {
            p.heal(event.getAmount() * LIFESTEAL);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private ResonanceRitual() {}
}
