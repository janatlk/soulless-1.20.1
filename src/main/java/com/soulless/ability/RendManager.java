package com.soulless.ability;

import com.soulless.Soulless;
import com.soulless.effect.Stun;
import com.soulless.item.SoulForgedItem;
import com.soulless.network.ModNetwork;
import com.soulless.network.RendHitPacket;
import com.soulless.network.RendStartPacket;
import com.soulless.registry.ModParticles;
import com.soulless.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Soul Rend" (right click): a six-second wind-up with the sword hauled over the right shoulder, then a
 * diagonal cut from the right shoulder down to the left. Everything in the cut takes damage and is flung away,
 * lighter creatures much further; the three nearest are stunned; the wielder heals per creature struck.
 * The cooldown starts at the hit; the sword cannot attack until the recovery ends.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class RendManager {
    public static final float DAMAGE = 15.0F;
    public static final double RANGE = 5.5;
    public static final double HALF_ARC_DEG = 80;
    public static final int STUN_TARGETS = 3;
    public static final int STUN_SECONDS = 7;
    public static final float HEAL_PER_TARGET = 2.0F;   // one heart
    private static final String CD_TAG = "soulless_rend_ready_at";

    private static final UUID SLOW_UUID = UUID.fromString("2b7d0e55-3c1a-4a8e-9f6d-7c4b1a2e9d30");

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    /** Clears the Soul Rend cooldown ({@code /soulless resetcd}). */
    public static void resetCooldown(ServerPlayer player) {
        player.getPersistentData().remove(CD_TAG);
    }

    /** Game time when this player's Soul Rend is ready again (0 if never used). */
    public static long readyAt(ServerPlayer player) {
        return player.getPersistentData().getLong(CD_TAG);
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void tryStart(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof SoulForgedItem) || !player.isAlive() || player.isSpectator()) return;
        if (isActive(player) || SlamManager.isSlamming(player)) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long ready = readyAt(player);
        if (ready > now) {
            player.displayClientMessage(Component.translatable("message.soulless.rend_cooldown", Mth.ceil((ready - now) / 20F)), true);
            return;
        }
        ACTIVE.put(player.getUUID(), now);
        setSlow(player, -0.55);
        ModNetwork.sendTrackingAndSelf(player, new RendStartPacket(player.getId()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.4F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM_WINDUP.get(), SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, Long>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive()) {
                if (player != null) setSlow(player, 0);
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            long age = level.getGameTime() - e.getValue();
            if (age == RendTimeline.WINDUP - 20) {
                // the blade is fully charged: one last breath before the cut
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.6F);
            }
            if (age == RendTimeline.WINDUP + RendTimeline.HIT_AT) {
                strike(player);
                setSlow(player, -0.3);
            }
            if (age >= RendTimeline.TOTAL) {
                setSlow(player, 0);
                it.remove();
            }
        }
    }

    private static void strike(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float yaw = player.getYRot();

        List<LivingEntity> hit = new ArrayList<>();
        AABB box = player.getBoundingBox().inflate(RANGE, 2.5, RANGE);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != player && x.isAlive() && !x.isSpectator())) {
            if (t.isAlliedTo(player) || (t instanceof TamableAnimal pet && pet.isOwnedBy(player))) continue;
            if (inCut(player, t)) hit.add(t);
        }

        hit.sort(Comparator.comparingDouble(t -> t.distanceToSqr(player)));
        for (int i = 0; i < hit.size(); i++) {
            LivingEntity t = hit.get(i);
            t.invulnerableTime = 0;
            t.hurt(level.damageSources().playerAttack(player), DAMAGE);
            if (i < STUN_TARGETS) {
                Stun.apply(t, STUN_SECONDS * 20, player);
            }
            fling(t, player);
            level.sendParticles(ModParticles.SOUL_EMBER.get(), t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 25, 0.3, 0.4, 0.3, 0.15);
            level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 12, 0.3, 0.3, 0.3, 0.4);
        }
        if (!hit.isEmpty()) {
            player.heal(HEAL_PER_TARGET * hit.size());
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.1, player.getZ(), Math.min(hit.size(), 6), 0.4, 0.1, 0.4, 0);
        }

        player.getPersistentData().putLong(CD_TAG, level.getGameTime() + RendTimeline.COOLDOWN);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.55F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM_IMPACT.get(), SoundSource.PLAYERS, 1.0F, 1.5F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5F, 0.6F);
        ModNetwork.sendNear(level, player.position(), 64, new RendHitPacket(player.getId(), player.getX(), player.getY(), player.getZ(), yaw, hit.size()));
    }

    /** Lowest / highest point of the cut relative to the wielder's eyes. */
    public static final double CUT_BELOW = 2.5, CUT_ABOVE = 3.0;

    /**
     * Is the target inside the cut: within {@link #RANGE} horizontally, within {@link #HALF_ARC_DEG} of where
     * the wielder faces, and between {@link #CUT_BELOW} below and {@link #CUT_ABOVE} above the eyes.
     * Shared with the client zone preview ({@code /soulless zones}).
     */
    public static boolean inCut(Player player, Entity target) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye);
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.length() > RANGE || to.y < -CUT_BELOW || to.y > CUT_ABOVE) return false;
        double angle = Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(look), -1, 1)));
        return flat.lengthSqr() <= 0.5 || angle <= HALF_ARC_DEG;
    }

    /**
     * Throw a struck creature away from the wielder. Distance in blocks = 2 + 136 / max health (capped at 40):
     * an iron golem (100) goes ~3 blocks, a zombie (20) ~9, a chicken (4) ~36. Knockback resistance is ignored.
     */
    private static void fling(LivingEntity t, ServerPlayer player) {
        double blocks = Math.min(40, 2 + 136.0 / Math.max(1, t.getMaxHealth()));
        Vec3 away = new Vec3(t.getX() - player.getX(), 0, t.getZ() - player.getZ());
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : away.normalize();
        // horizontal speed decays by 0.91 per tick in the air (much faster on landing): long throws need a
        // higher arc and a bit more speed to cover the distance before touching down
        double speed = blocks / (blocks > 12 ? 7.0 : 10.5);
        double up = 0.45 + Math.min(1.1, blocks / 32.0);
        t.setDeltaMovement(away.x * speed, up, away.z * speed);
        t.hasImpulse = true;
        t.hurtMarked = true;
    }

    private static void setSlow(ServerPlayer player, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOW_UUID);
        if (amount != 0) {
            speed.addTransientModifier(new AttributeModifier(SLOW_UUID, "Soul Rend", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** No sword attacks from the start of the wind-up until the recovery ends. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isActive(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            setSlow(p, 0);
        }
        ACTIVE.remove(event.getEntity().getUUID());
    }

    /** Keep the cooldown across death/respawn (persistent data is copied only when asked). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(CD_TAG)) {
            event.getEntity().getPersistentData().putLong(CD_TAG, old.getLong(CD_TAG));
        }
    }

    private RendManager() {}
}
