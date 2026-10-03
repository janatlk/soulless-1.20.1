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
        /** Game time the (possibly fast-forwarded) timeline started. */
        long start;
        float power = 1;
        /** Wind-up age at which the player let go of right click, or -1 while still held. */
        long releaseAt = -1;
        boolean released;

        State(long start) {
            this.start = start;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    /** The player let go of right click during the wind-up: cut now, weaker the earlier it is. */
    public static void release(ServerPlayer player) {
        State s = ACTIVE.get(player.getUUID());
        if (s == null || s.released || s.releaseAt >= 0) return;
        long age = player.serverLevel().getGameTime() - s.start;
        if (age >= RendTimeline.WINDUP) return;
        s.releaseAt = Math.max(age, RendTimeline.HEAVE);
        s.power = Math.max(RendTimeline.MIN_POWER, age / (float) RendTimeline.WINDUP);
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
        long ready = readyAt(player);
        if (ready > now) {
            player.displayClientMessage(Component.translatable("message.soulless.rend_cooldown", Mth.ceil((ready - now) / 20F)), true);
            return;
        }
        ACTIVE.put(player.getUUID(), new State(now));
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
            long now = level.getGameTime();
            long age = now - s.start;
            if (age < RendTimeline.WINDUP + RendTimeline.HIT_AT && !(player.getMainHandItem().getItem() instanceof SoulForgedItem)) {
                abort(player);
                it.remove();
                continue;
            }
            if (s.releaseAt >= 0 && !s.released && age >= s.releaseAt) {
                // fast-forward the timeline to the cut and tell everyone to do the same
                s.released = true;
                s.start = now - RendTimeline.WINDUP;
                age = RendTimeline.WINDUP;
                ModNetwork.sendTrackingAndSelf(player, new RendStartPacket(player.getId(), RendTimeline.WINDUP));
            }
            if (age == RendTimeline.WINDUP - 20 && !s.released) {
                // the blade is fully charged: one last breath before the cut
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.6F);
            }
            if (age == RendTimeline.WINDUP + RendTimeline.HIT_AT) {
                strike(player, s.power);
                setSlow(player, -0.3);
            }
            if (age >= RendTimeline.TOTAL) {
                setSlow(player, 0);
                it.remove();
            }
        }
    }

    /** The cut. {@code power} (MIN_POWER..1, from how long right click was held) scales damage, stun and heal. */
    private static void strike(ServerPlayer player, float power) {
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
            SoulBonus.abilityDamage(() -> t.hurt(level.damageSources().playerAttack(player), DAMAGE * power));
            if (i < STUN_TARGETS) {
                Stun.apply(t, Math.max(10, Math.round(STUN_SECONDS * 20 * power)), player);
            }
            fling(t, player, 0.5 + 0.5 * power);
            level.sendParticles(ModParticles.SOUL_EMBER.get(), t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 25, 0.3, 0.4, 0.3, 0.15);
            level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 12, 0.3, 0.3, 0.3, 0.4);
        }
        // the cut burns away every harmful effect on the wielder, and costs the blade durability
        List<net.minecraft.world.effect.MobEffect> bad = player.getActiveEffects().stream()
                .map(net.minecraft.world.effect.MobEffectInstance::getEffect)
                .filter(fx -> fx.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                .toList();
        bad.forEach(player::removeEffect);
        if (!bad.isEmpty()) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.4, 0.6, 0.4, 0.04);
        }
        player.getMainHandItem().hurtAndBreak(DURABILITY_COST, player,
                p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));

        if (!hit.isEmpty()) {
            player.heal(HEAL_PER_TARGET * power * hit.size());
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.1, player.getZ(), Math.min(hit.size(), 6), 0.4, 0.1, 0.4, 0);
        }

        // the cut flies on: a crescent of soul fire through blocks and creatures
        SoulSlashEntity slash = new SoulSlashEntity(com.soulless.registry.ModEntities.SOUL_SLASH.get(), level);
        Vec3 sight = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(0, -0.3, 0).add(sight.scale(1.5));
        slash.moveTo(from.x, from.y, from.z);
        slash.setup(player, sight, power);
        level.addFreshEntity(slash);

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
    private static void fling(LivingEntity t, ServerPlayer player, double scale) {
        double blocks = Math.min(40, 2 + 136.0 / Math.max(1, t.getMaxHealth())) * scale;
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
