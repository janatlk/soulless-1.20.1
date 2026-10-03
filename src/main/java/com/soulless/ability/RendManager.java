package com.soulless.ability;

import com.soulless.Soulless;
import com.soulless.effect.Stun;
import com.soulless.item.SoulBonus;
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
    public static final int DURABILITY_COST = 10;
    private static final String CD_TAG = "soulless_rend_ready_at";

    private static final UUID SLOW_UUID = UUID.fromString("2b7d0e55-3c1a-4a8e-9f6d-7c4b1a2e9d30");

    private static final class State {
        /** Position on the timeline (ticks; it advances faster with the Soulsmith's Ring). */
        double age;
        /** Last tick's position, so moments are caught when the faster timeline steps over them. */
        double prevAge = -1;
        final float speed;
        final boolean ring;
        float power = 1;
        /** Wind-up age at which the player let go of right click, or -1 while still held. */
        double releaseAt = -1;
        boolean released;

        State(boolean ring) {
            this.ring = ring;
            this.speed = 1F;
        }

        boolean crossed(double moment) {
            return prevAge < moment && age >= moment;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    /** The player let go of right click during the wind-up: cut now, weaker the earlier it is. */
    public static void release(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s == null || s.released || s.releaseAt >= 0) return;
        double age = s.age;
        if (age >= RendTimeline.WINDUP) return;
        s.releaseAt = Math.max(age, RendTimeline.HEAVE);
        s.power = Math.max(RendTimeline.MIN_POWER, (float) (age / RendTimeline.WINDUP));
    }

    /** Stop the ability without a cut (the sword left the hand). */
    private static void abort(ServerPlayer player) {
        setSlow(player, 0);
        ModNetwork.sendTrackingAndSelf(player, new RendStartPacket(player.getId(), RendTimeline.TOTAL));
    }

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
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) return;   // shared with Soul Crush
        ACTIVE.put(player.getUUID(), new State(com.soulless.item.SoulRing.worn(player)));
        setSlow(player, -0.55);
        ModNetwork.sendTrackingAndSelf(player, new RendStartPacket(player.getId(), 0));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.4F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM_WINDUP.get(), SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            State s = e.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive()) {
                if (player != null) setSlow(player, 0);
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            s.prevAge = s.age;
            s.age += s.speed;
            double age = s.age;
            if (s.ring && ((int) age) % 2 == 0) {
                com.soulless.item.SoulRing.sparkle(level, com.soulless.item.SoulRing.swordPoint(player).add(0, 0.6, 0), 3, 0.25);
            }
            if (age < RendTimeline.WINDUP + RendTimeline.HIT_AT && !(player.getMainHandItem().getItem() instanceof SoulForgedItem)) {
                abort(player);
                it.remove();
                continue;
            }
            if (s.releaseAt >= 0 && !s.released && age >= s.releaseAt) {
                // fast-forward the timeline to the cut and tell everyone to do the same
                s.released = true;
                s.prevAge = RendTimeline.WINDUP - 0.001;
                s.age = age = RendTimeline.WINDUP;
                ModNetwork.sendTrackingAndSelf(player, new RendStartPacket(player.getId(), RendTimeline.WINDUP));
            }
            if (s.crossed(RendTimeline.WINDUP - 20) && !s.released) {
                // the blade is fully charged: one last breath before the cut
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.6F);
            }
            if (s.crossed(RendTimeline.WINDUP + RendTimeline.HIT_AT)) {
                strike(player, s.power);
                if (s.ring) {
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                    for (int i = 0; i < 10; i++) {
                        com.soulless.item.SoulRing.sparkle(level, player.getEyePosition().add(look.scale(1.5 + i * 0.6)), 4, 0.35);
                    }
                }
                setSlow(player, -0.3);
            }
            if (age >= RendTimeline.TOTAL) {
                setSlow(player, 0);
                it.remove();
            }
        }
    }

    /**
     * The cut does nothing by itself: it only releases the beam (30-60 damage by charge, stopped by blocks,
     * a little lifesteal; see SoulSlashEntity). It costs durability and starts the cooldown shared with Soul Crush.
     */
    private static void strike(ServerPlayer player, float power) {
        ServerLevel level = player.serverLevel();
        float yaw = player.getYRot();
        player.getMainHandItem().hurtAndBreak(DURABILITY_COST, player,
                p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));

        SoulSlashEntity slash = new SoulSlashEntity(com.soulless.registry.ModEntities.SOUL_SLASH.get(), level);
        Vec3 sight = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(0, -0.3, 0).add(sight.scale(1.2));
        slash.moveTo(from.x, from.y, from.z);
        slash.setup(player, sight, (power - RendTimeline.MIN_POWER) / (1 - RendTimeline.MIN_POWER));
        level.addFreshEntity(slash);

        player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), SlamManager.COOLDOWN_TICKS);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.55F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM_IMPACT.get(), SoundSource.PLAYERS, 1.0F, 1.5F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5F, 0.6F);
        ModNetwork.sendNear(level, player.position(), 64, new RendHitPacket(player.getId(), player.getX(), player.getY(), player.getZ(), yaw, 0));
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
