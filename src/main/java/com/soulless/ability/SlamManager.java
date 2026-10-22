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
    /** Every creature in the blast takes this. */
    public static final float DAMAGE = 32.0F;
    /** Everything the blast hits is stunned this long, bosses included (+1 s with the Soulsmith's Ring). */
    public static final float STUN_SECONDS = 3;
    public static final float RADIUS = 4.0F;
    private static final int MAX_FALL_TICKS = SlamTimeline.MAX_AIR;
    public static final int COOLDOWN_TICKS = 240;

    /** Ticks of the leap before the sword is driven down. Mirrored by the client animation. */
    private static final class State {
        final long start;
        final double startY;
        /** Wearing the Soulsmith's Ring: a higher leap, more damage, allowed in mid-air. */
        final boolean ring;
        final boolean fromAir;
        long impact = -1;

        State(long start, double startY, boolean ring, boolean fromAir) {
            this.start = start;
            this.startY = startY;
            this.ring = ring;
            this.fromAir = fromAir;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static void tryStart(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SoulForgedItem item) || !player.isAlive() || player.isSpectator()) return;
        if (player.getCooldowns().isOnCooldown(item) || ACTIVE.containsKey(player.getUUID()) || RendManager.isActive(player)) return;
        if (!com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.FORGED_SLAM)) {
            player.displayClientMessage(Component.translatable("message.soulless.upgrade_locked", Component.translatable("upgrade.soulless." + com.soulless.upgrade.Upgrades.FORGED_SLAM)), true);
            return;
        }
        boolean ring = com.soulless.item.SoulRing.worn(player);
        if (player.isInWater()) {
            player.displayClientMessage(Component.translatable("message.soulless.slam_water"), true);
            return;
        }
        if (!player.onGround()) {
            player.displayClientMessage(Component.translatable("message.soulless.slam_ground"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        player.getCooldowns().addCooldown(item, cooldown(stack));
        ACTIVE.put(player.getUUID(), new State(level.getGameTime(), player.getY(), ring, false));
        com.soulless.Achievements.grant(player, "first_crush");

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
                level.sendParticles(com.soulless.item.SoulRing.ember(s.ring), player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.6, 0.05, 0.6, 0.08);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.5, 0.05, 0.5, 0.05);
            } else if (age == SlamTimeline.PLUNGE) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x * 0.3, SlamTimeline.DIVE_SPEED, v.z * 0.3);
                player.hurtMarked = true;
            }
            if ((age > SlamTimeline.PLUNGE && player.onGround()) || age >= MAX_FALL_TICKS) {
                s.impact = now;
                impact(player, s.startY, s.ring);
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

    private static void impact(ServerPlayer player, double startY, boolean withRing) {
        ServerLevel level = player.serverLevel();
        Vec3 look = horizontalLook(player);
        Vec3 center = impactCenter(player);
        BlockPos groundPos = CrackManager.findSurface(level, BlockPos.containing(center.x, player.getY(), center.z));
        double groundY = groundPos != null ? groundPos.getY() + 1.0 : player.getY();
        center = new Vec3(center.x, groundY, center.z);
        int stun = Math.round(STUN_SECONDS * 20) + (withRing ? com.soulless.item.SoulRing.STUN_BONUS_TICKS : 0);


        float radius = radius(player.getMainHandItem());
        AABB box = new AABB(center, center).inflate(radius, 3.0, radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator() && !com.soulless.pet.TamedFox.isPetOf(e, player))) {
            if (target.isAlliedTo(player) || (target instanceof TamableAnimal pet && pet.isOwnedBy(player))) continue;
            if (!inBlast(center, target, radius)) continue;
            target.invulnerableTime = 0;
            Vec3 keep = target.getDeltaMovement();
            SoulBonus.abilityDamage(() -> target.hurt(level.damageSources().playerAttack(player), DAMAGE));
            target.setDeltaMovement(keep);   // no knockback
            target.hurtMarked = true;
            // everything struck is stunned, bosses too (the Reaper drops what it was doing and reels)
            if (target instanceof com.soulless.boss.WailingReaperEntity reaper) reaper.stagger(stun);
            else com.soulless.effect.Stun.force(target, stun, player);
        }

        level.playSound(null, center.x, center.y, center.z, ModSounds.SLAM_IMPACT.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.55F);

        // Shockwave: rings of soul embers, dragon breath and debris from the struck block.
        BlockState ground = groundPos != null ? level.getBlockState(groundPos) : null;
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 3, 0.6, 0.2, 0.6, 0);
        level.sendParticles(com.soulless.item.SoulRing.accent(withRing, ParticleTypes.REVERSE_PORTAL), center.x, center.y + 0.5, center.z, 80, 1.5, 0.6, 1.5, 0.4);
        for (int ring = 1; ring <= (int) radius; ring++) {
            int points = ring * 10;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                double x = center.x + Math.cos(a) * ring;
                double z = center.z + Math.sin(a) * ring;
                level.sendParticles(com.soulless.item.SoulRing.ember(withRing), x, center.y + 0.1, z, 1, 0.1, 0.05, 0.1, 0.02);
                if (i % 3 == 0) {
                    level.sendParticles(com.soulless.item.SoulRing.accent(withRing, ParticleTypes.DRAGON_BREATH), x, center.y + 0.2, z, 1, 0.05, 0.1, 0.05, 0.03);
                }
                if (ground != null && i % 2 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, center.y + 0.1, z, 2, 0.2, 0.3, 0.2, 0.15);
                }
            }
        }
        level.sendParticles(com.soulless.item.SoulRing.ember(withRing), center.x, center.y + 0.5, center.z, 60, 0.6, 1.2, 0.6, 0.12);

        if (groundPos != null) {
            CrackManager.start(level, groundPos, radius + 1.5F, withRing);
        }
        ModNetwork.sendNear(level, center, 48, new SlamImpactPacket(player.getId(), center.x, center.y, center.z, radius));
        player.fallDistance = 0;
    }

    /** The Quake path widens the blast by this much; the Haste path shortens the shared cooldown. */
    public static final float QUAKE_MULT = 1.5F, HASTE_MULT = 0.65F;

    public static float radius(ItemStack stack) {
        return RADIUS * (com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.FORGED_QUAKE) ? QUAKE_MULT : 1);
    }

    public static int cooldown(ItemStack stack) {
        return Math.round(COOLDOWN_TICKS * (com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.FORGED_HASTE) ? HASTE_MULT : 1));
    }

    /** Where the blade lands: a little ahead of the wielder's feet. Shared with {@code /soulless zones}. */
    public static Vec3 impactCenter(Player player) {
        return player.position().add(horizontalLook(player).scale(1.1));
    }

    /** Is the target inside the blast around {@code center} (horizontal radius, 3 blocks up/down). */
    public static boolean inBlast(Vec3 center, Entity target) {
        return inBlast(center, target, RADIUS);
    }

    public static boolean inBlast(Vec3 center, Entity target, float radius) {
        double dist = Math.sqrt(Mth.square(target.getX() - center.x) + Mth.square(target.getZ() - center.z));
        return dist <= radius && Math.abs(target.getY() - center.y) <= 3.0;
    }

    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
    }

    private SlamManager() {}
}
