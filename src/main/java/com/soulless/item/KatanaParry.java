package com.soulless.item;

import com.soulless.Soulless;
import com.soulless.network.KatanaAnimPacket;
import com.soulless.network.ModNetwork;
import com.soulless.registry.ModEffects;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The katana's parry (right click): the blade is brought across in front for a short window. Anything physical
 * that reaches the player in that window is parried - no damage:
 * <ul>
 *   <li>a blow from a creature that swings to strike (every mob with an attack animation swings right before the
 *       hit; contact damage - a blaze or slime touching you, the dragon's knock-back - has no swing and goes through):
 *       the attacker is stunned for 5 s (bosses never are);</li>
 *   <li>a projectile you could knock aside - arrows, tridents, fireballs, the Reaper's burning skull: it is
 *       deflected or broken (no stun for whoever shot it). Dragon breath, wither skulls, shulker bullets, potions,
 *       fireworks, evoker fangs, guardian beams and explosions are not physical and cannot be parried.</li>
 * </ul>
 * The parry guards all around (any direction) and catches every blow that lands in the window, not just the first.
 * A parry makes the next katana strike 20% stronger. A parry that catches nothing can't be repeated for a moment.
 * Animation: client.KatanaAnim "parry" (KatanaAnimPacket step 4) and "parry_hit" (step 5).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class KatanaParry {
    public static final int PARRY = 4, PARRIED = 5;
    /** The window opens this many ticks after the press (the blade comes across) and lasts this long. */
    public static final int WINDOW_FROM = 1, WINDOW = 8;
    /** After a parry that caught nothing, the next one has to wait this long (from the press). */
    public static final int WHIFF_COOLDOWN = 30;
    public static final int STUN_TICKS = 2 * 20;
    /** Players are stunned for less: 5 s without moving or striking back would decide any duel. */
    public static final int PLAYER_STUN_TICKS = STUN_TICKS;
    public static final float BONUS = 1.2F;
    /** A parry grants this much of the Kitsune's blessing. */
    public static final int BLESSING_TICKS = 2 * 20;
    /** The riposte (the first cut after a parry) plays this much faster. */
    public static final float RIPOSTE_RATE = 1.8F;
    /** The bonus waits this long for the next strike. */
    private static final int BONUS_TICKS = 60;

    private static final Map<UUID, Long> STARTED = new HashMap<>();
    /** When the last blow was caught: the window stays open {@link #CHAIN} ticks after each one. */
    private static final Map<UUID, Long> LAST_CATCH = new HashMap<>();
    public static final int CHAIN = 10;
    /** Players whose current window has parried something (every blow in the window is parried; no whiff cooldown). */
    private static final java.util.Set<UUID> CAUGHT = new java.util.HashSet<>();
    /** How many blows the current window has caught (three at once earns an advancement). */
    private static final Map<UUID, Integer> CAUGHT_COUNT = new HashMap<>();
    private static final Map<UUID, Long> BONUS_UNTIL = new HashMap<>();
    /** Every blow or projectile a player has parried (the sensei's sparring counts with it). */
    private static final Map<UUID, Integer> TOTAL = new HashMap<>();

    public static int caughtTotal(Player player) {
        return TOTAL.getOrDefault(player.getUUID(), 0);
    }

    public static void tryStart(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof SoulKatanaItem) || !player.isAlive() || player.isSpectator()) return;
        if (ResonanceRitual.isActive(player) || KatanaCombat.isCutting(player)) return;
        if (!com.soulless.upgrade.Upgrades.has(player, com.soulless.upgrade.Upgrades.KATANA_PARRY)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.soulless.upgrade_locked",
                    net.minecraft.network.chat.Component.translatable("upgrade.soulless." + com.soulless.upgrade.Upgrades.KATANA_PARRY)), true);
            return;
        }
        long now = player.level().getGameTime();
        Long last = STARTED.get(player.getUUID());
        if (last != null && now - last < WHIFF_COOLDOWN && !CAUGHT.contains(player.getUUID())) return;
        if (last != null && now - last < WINDOW_FROM + WINDOW) return;   // the window is still open
        STARTED.put(player.getUUID(), now);
        CAUGHT.remove(player.getUUID());
        CAUGHT_COUNT.remove(player.getUUID());
        send(player, PARRY);
    }

    private static void send(ServerPlayer player, int step) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new KatanaAnimPacket(player.getId(), step, 1));
    }

    private static boolean inWindow(Player player) {
        Long start = STARTED.get(player.getUUID());
        if (start == null || !(player.getMainHandItem().getItem() instanceof SoulKatanaItem)) return false;
        long now = player.level().getGameTime();
        long t = now - start;
        if (t >= WINDOW_FROM && t < WINDOW_FROM + WINDOW) return true;
        // a parry that caught something keeps the guard up a little longer: a quick second blow is caught too
        Long caught = LAST_CATCH.get(player.getUUID());
        return CAUGHT.contains(player.getUUID()) && caught != null && now - caught < CHAIN;
    }

    /** The Deep Stun path stretches the stun by this much. */
    public static final float STUN_PATH_MULT = 1.75F;

    public static int stunTicks(ItemStack katana) {
        return Math.round(STUN_TICKS * (com.soulless.upgrade.Upgrades.has(katana, com.soulless.upgrade.Upgrades.KATANA_STUN) ? STUN_PATH_MULT : 1));
    }

    /** When this player last raised the guard (game time; -1: never). */
    public static long started(Player player) {
        return STARTED.getOrDefault(player.getUUID(), -1L);
    }

    /** {@code /soulless resetcd}: the next parry may come at once. */
    public static void resetCooldown(Player player) {
        STARTED.remove(player.getUUID());
        CAUGHT.remove(player.getUUID());
    }

    /** A parry just landed: the riposte is ready (full strength, no weapon cooldown). */
    public static boolean riposteReady(Player player) {
        Long until = BONUS_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    /** The next katana strike's multiplier: the parry bonus, used up by that strike. */
    public static float consumeBonus(Player player) {
        Long until = BONUS_UNTIL.remove(player.getUUID());
        return until != null && player.level().getGameTime() <= until ? BONUS : 1;
    }

    /** A projectile about to hit a parrying player is knocked aside instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) return;
        Projectile p = event.getProjectile();
        if (!inWindow(player) || !parryable(p)) return;
        event.setCanceled(true);
        if (p instanceof AbstractArrow) {
            // arrows and tridents glance off and drop
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.scale(-0.12).add(0, 0.15, 0));
            p.setPos(p.position().add(v.normalize().scale(-0.6)));
        } else {
            p.discard();
        }
        success(player, p.position(), null);
    }

    /** Projectiles that are things you could knock aside, not magic or blasts. */
    private static boolean parryable(Projectile p) {
        if (p instanceof DragonFireball || p instanceof WitherSkull || p instanceof ShulkerBullet || p instanceof FireworkRocketEntity) return false;
        if (p instanceof ThrowableItemProjectile) return false;   // potions, eggs, snowballs, pearls, bottles
        return true;
    }

    /** A blow aimed at a parrying player: parried if it is a creature's swung strike. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inWindow(player)) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker) return;
        if (src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.BYPASSES_ARMOR) || src.is(DamageTypeTags.IS_FIRE)) return;
        // a swung strike: mobs with an attack animation swing right before they hit (players always do)
        if (!(attacker instanceof Player) && !attacker.swinging && !(attacker instanceof com.soulless.boss.RenegadeEntity)) return;
        event.setCanceled(true);
        success(player, attacker.position().add(0, attacker.getBbHeight() * 0.6, 0).lerp(player.getEyePosition(), 0.5), attacker);
    }

    /**
     * A blow or projectile parried. The window stays open: everything that reaches the player in it, from any side,
     * is parried too. The recoil animation plays for the first one.
     */
    private static void success(ServerPlayer player, Vec3 at, Entity attacker) {
        boolean first = CAUGHT.add(player.getUUID());
        LAST_CATCH.put(player.getUUID(), player.level().getGameTime());
        int count = CAUGHT_COUNT.merge(player.getUUID(), 1, Integer::sum);
        TOTAL.merge(player.getUUID(), 1, Integer::sum);
        com.soulless.Achievements.grant(player, "first_parry");
        if (count >= 3) com.soulless.Achievements.grant(player, "triple_parry");
        com.soulless.CombatTrial.onParry(player);
        BONUS_UNTIL.put(player.getUUID(), player.level().getGameTime() + BONUS_TICKS);
        if (first) send(player, PARRIED);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.15, 0.15, 0.15, 0.4);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.9F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.soulless.registry.ModSounds.KATANA_HIT.get(), SoundSource.PLAYERS, 0.5F, 1.3F);
        // the Kitsune smiles on a clean parry: a few seconds of her blessing (a longer one already running is kept)
        MobEffectInstance blessing = player.getEffect(ModEffects.SOUL_RESONANCE.get());
        if ((blessing == null || blessing.getDuration() < BLESSING_TICKS)) {
            player.addEffect(new MobEffectInstance(ModEffects.SOUL_RESONANCE.get(), BLESSING_TICKS, 0, false, true, true));
        }
        if (attacker instanceof LivingEntity l && !isBoss(l)) {
            int stun = stunTicks(player.getMainHandItem());
            l.addEffect(new MobEffectInstance(ModEffects.STUN.get(), l instanceof Player ? Math.min(stun, PLAYER_STUN_TICKS * 2) : stun, 0));
        }
        if (first) player.displayClientMessage(Component.translatable("message.soulless.parry"), true);
    }

    private static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || e instanceof com.soulless.boss.WailingReaperEntity;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STARTED.remove(event.getEntity().getUUID());
        CAUGHT.remove(event.getEntity().getUUID());
        BONUS_UNTIL.remove(event.getEntity().getUUID());
    }

    private KatanaParry() {}
}
