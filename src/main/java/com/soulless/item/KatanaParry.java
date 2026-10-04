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
    public static final int STUN_TICKS = 5 * 20;
    public static final float BONUS = 1.2F;
    /** The riposte (the first cut after a parry) plays this much faster. */
    public static final float RIPOSTE_RATE = 1.8F;
    /** The bonus waits this long for the next strike. */
    private static final int BONUS_TICKS = 60;

    private static final Map<UUID, Long> STARTED = new HashMap<>();
    private static final Map<UUID, Long> BONUS_UNTIL = new HashMap<>();

    public static void tryStart(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof SoulKatanaItem) || !player.isAlive() || player.isSpectator()) return;
        if (KitsuneRitual.isActive(player) || KatanaCombat.isCutting(player)) return;
        long now = player.level().getGameTime();
        Long last = STARTED.get(player.getUUID());
        if (last != null && now - last < WHIFF_COOLDOWN) return;
        STARTED.put(player.getUUID(), now);
        send(player, PARRY);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.soulless.registry.ModSounds.KATANA_SWING.get(),
                SoundSource.PLAYERS, 0.2F, 1.5F);
    }

    private static void send(ServerPlayer player, int step) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new KatanaAnimPacket(player.getId(), step, 1));
    }

    private static boolean inWindow(Player player) {
        Long start = STARTED.get(player.getUUID());
        if (start == null || !(player.getMainHandItem().getItem() instanceof SoulKatanaItem)) return false;
        long t = player.level().getGameTime() - start;
        return t >= WINDOW_FROM && t < WINDOW_FROM + WINDOW;
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
        if (!(attacker instanceof Player) && !attacker.swinging) return;
        event.setCanceled(true);
        success(player, attacker.position().add(0, attacker.getBbHeight() * 0.6, 0).lerp(player.getEyePosition(), 0.5), attacker);
    }

    private static void success(ServerPlayer player, Vec3 at, Entity attacker) {
        STARTED.remove(player.getUUID());   // parrying again is allowed right away
        BONUS_UNTIL.put(player.getUUID(), player.level().getGameTime() + BONUS_TICKS);
        send(player, PARRIED);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.15, 0.15, 0.15, 0.4);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.9F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.soulless.registry.ModSounds.KATANA_HIT.get(), SoundSource.PLAYERS, 0.5F, 1.3F);
        if (attacker instanceof LivingEntity l && !isBoss(l)) {
            l.addEffect(new MobEffectInstance(ModEffects.STUN.get(), STUN_TICKS, 0));
        }
        player.displayClientMessage(Component.translatable("message.soulless.parry"), true);
    }

    private static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || e instanceof com.soulless.boss.WailingReaperEntity;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STARTED.remove(event.getEntity().getUUID());
        BONUS_UNTIL.remove(event.getEntity().getUUID());
    }

    private KatanaParry() {}
}
