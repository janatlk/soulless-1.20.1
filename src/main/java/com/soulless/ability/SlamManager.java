package com.soulless.ability;

import com.soulless.Soulless;
import com.soulless.effect.SoulCurse;
import com.soulless.item.SoulBonus;
import com.soulless.item.SoulForgedItem;
import com.soulless.network.ModNetwork;
import com.soulless.network.SlamImpactPacket;
import com.soulless.network.SlamStartPacket;
import com.soulless.registry.ModParticles;
import com.soulless.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * "Soul Crush": the player leaps, drives the sword down and the ground erupts.
 * Server-authoritative; the client only asks via {@link com.soulless.network.SlamRequestPacket}.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SlamManager {
    /** Damage at the centre when landing where you started; the edge of the blast takes half. */
    public static final float BASE_DAMAGE = 14.0F;
    /** Extra centre damage per block the impact lands below the starting ground. */
    public static final float DAMAGE_PER_BLOCK = 5.0F;
    /** Everything the blast hits is stunned this long, bosses included. */
    public static final float STUN_SECONDS = 3;
    public static final float DAMAGE_CAP = 120.0F;
    public static final float RADIUS = 5.0F;
    private static final int MAX_FALL_TICKS = SlamTimeline.MAX_AIR;
    public static final int CURSE_SECONDS = 8;
    public static final int CURSE_AMPLIFIER = 1;
    public static final int COOLDOWN_TICKS = 240;

    /** Ticks of the leap before the sword is driven down. Mirrored by the client animation. */
    private static final class State {
        final long start;
        final double startY;
        long impact = -1;

        State(long start, double startY) {
            this.start = start;
            this.startY = startY;
        }
    }

    /** Centre damage for an impact {@code drop} blocks below where the slam began. */
    public static float centreDamage(double drop) {
        return (float) Math.min(DAMAGE_CAP, BASE_DAMAGE + Math.max(0, drop) * DAMAGE_PER_BLOCK);
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static void tryStart(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SoulForgedItem item) || !player.isAlive() || player.isSpectator()) return;
        if (player.getCooldowns().isOnCooldown(item) || ACTIVE.containsKey(player.getUUID()) || RendManager.isActive(player)) return;
        if (!player.onGround()) {
            player.displayClientMessage(Component.translatable("message.soulless.slam_ground"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);
        ACTIVE.put(player.getUUID(), new State(level.getGameTime(), player.getY()));

        item.triggerAnim(player, GeoItem.getOrAssignId(stack, level), SoulForgedItem.CONTROLLER, SoulForgedItem.SLAM_ANIM);
        ModNetwork.sendTrackingAndSelf(player, new SlamStartPacket(player.getId()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM_WINDUP.get(), SoundSource.PLAYERS, 1.4F, 0.8F);
        root(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State s = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            long age = now - s.start;
            player.fallDistance = 0;

            if (s.impact >= 0) {
                // kneeling with the sword in the ground, then the stance is released
                if (now - s.impact < SlamTimeline.KNEEL) root(player);
                if (now - s.impact >= SlamTimeline.KNEEL + SlamTimeline.RECOVER) it.remove();
                continue;
            }
            if (age < SlamTimeline.CHARGE) {
                root(player);
            } else if (age == SlamTimeline.CHARGE) {
                Vec3 look = horizontalLook(player);
                player.setDeltaMovement(look.x * SlamTimeline.LEAP_FORWARD, SlamTimeline.LEAP_UP, look.z * SlamTimeline.LEAP_FORWARD);
                player.hurtMarked = true;
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);
                level.sendParticles(ModParticles.SOUL_EMBER.get(), player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.6, 0.05, 0.6, 0.08);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.5, 0.05, 0.5, 0.05);
            } else if (age == SlamTimeline.PLUNGE) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x * 0.3, SlamTimeline.DIVE_SPEED, v.z * 0.3);
                player.hurtMarked = true;
            }
            if ((age > SlamTimeline.PLUNGE && player.onGround()) || age >= MAX_FALL_TICKS) {
                s.impact = now;
                impact(player, s.startY);
            }
        }
        CrackManager.tick(event.getServer());
    }

    /** Pins the player in place (charge and kneel phases). */
    private static void root(ServerPlayer player) {
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(0, Math.min(v.y, 0), 0);
        player.hurtMarked = true;
    }

    public static boolean isSlamming(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /**
     * No fall damage during Soul Crush. Resetting fallDistance each tick is not enough: fall damage is checked
     * while processing movement packets, and several can arrive within one server tick during the dive.
     */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())) {
            event.setDistance(0);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFallHurt(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private static void impact(ServerPlayer player, double startY) {
        ServerLevel level = player.serverLevel();
        Vec3 look = horizontalLook(player);
        Vec3 center = impactCenter(player);
        BlockPos groundPos = CrackManager.findSurface(level, BlockPos.containing(center.x, player.getY(), center.z));
        double groundY = groundPos != null ? groundPos.getY() + 1.0 : player.getY();
        center = new Vec3(center.x, groundY, center.z);
        float maxDamage = centreDamage(startY - player.getY());

        AABB box = new AABB(center, center).inflate(RADIUS, 3.0, RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (target.isAlliedTo(player) || (target instanceof TamableAnimal pet && pet.isOwnedBy(player))) continue;
            if (!inBlast(center, target)) continue;
            double dist = Math.sqrt(Mth.square(target.getX() - center.x) + Mth.square(target.getZ() - center.z));
            float damage = (float) Mth.lerp(dist / RADIUS, maxDamage, maxDamage * 0.5F);
            target.invulnerableTime = 0;
            SoulBonus.abilityDamage(() -> target.hurt(level.damageSources().playerAttack(player), damage));
            Vec3 away = new Vec3(target.getX() - center.x, 0, target.getZ() - center.z);
            away = away.lengthSqr() < 1.0E-4 ? look : away.normalize();
            double push = 1.1 - 0.6 * (dist / RADIUS);
            target.push(away.x * push, 0.75, away.z * push);
            target.hurtMarked = true;
            SoulCurse.apply(target, CURSE_SECONDS * 20, CURSE_AMPLIFIER, player);
            // everything struck is stunned, bosses too (the Reaper drops what it was doing and reels)
            if (target instanceof com.soulless.boss.WailingReaperEntity reaper) reaper.stagger(Math.round(STUN_SECONDS * 20));
            else com.soulless.effect.Stun.force(target, Math.round(STUN_SECONDS * 20), player);
        }

        level.playSound(null, center.x, center.y, center.z, ModSounds.SLAM_IMPACT.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.55F);

        // Shockwave: rings of soul embers, dragon breath and debris from the struck block.
        BlockState ground = groundPos != null ? level.getBlockState(groundPos) : null;
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 3, 0.6, 0.2, 0.6, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.5, center.z, 80, 1.5, 0.6, 1.5, 0.4);
        for (int ring = 1; ring <= (int) RADIUS; ring++) {
            int points = ring * 10;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                double x = center.x + Math.cos(a) * ring;
                double z = center.z + Math.sin(a) * ring;
                level.sendParticles(ModParticles.SOUL_EMBER.get(), x, center.y + 0.1, z, 1, 0.1, 0.05, 0.1, 0.02);
                if (i % 3 == 0) {
                    level.sendParticles(ParticleTypes.DRAGON_BREATH, x, center.y + 0.2, z, 1, 0.05, 0.1, 0.05, 0.03);
                }
                if (ground != null && i % 2 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, center.y + 0.1, z, 2, 0.2, 0.3, 0.2, 0.15);
                }
            }
        }
        level.sendParticles(ModParticles.SOUL_EMBER.get(), center.x, center.y + 0.5, center.z, 60, 0.6, 1.2, 0.6, 0.12);

        if (groundPos != null) {
            CrackManager.start(level, groundPos, RADIUS + 1.5F);
        }
        ModNetwork.sendNear(level, center, 48, new SlamImpactPacket(player.getId(), center.x, center.y, center.z, RADIUS));
        player.fallDistance = 0;
    }

    /** Where the blade lands: a little ahead of the wielder's feet. Shared with {@code /soulless zones}. */
    public static Vec3 impactCenter(Player player) {
        return player.position().add(horizontalLook(player).scale(1.1));
    }

    /** Is the target inside the blast around {@code center} (horizontal radius, 3 blocks up/down). */
    public static boolean inBlast(Vec3 center, Entity target) {
        double dist = Math.sqrt(Mth.square(target.getX() - center.x) + Mth.square(target.getZ() - center.z));
        return dist <= RADIUS && Math.abs(target.getY() - center.y) <= 3.0;
    }

    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
    }

    private SlamManager() {}
}
