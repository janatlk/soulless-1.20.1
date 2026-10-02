package com.soulless.ability;

import com.soulless.Soulless;
import com.soulless.effect.SoulCurse;
import com.soulless.item.SoulForgedItem;
import com.soulless.network.ModNetwork;
import com.soulless.network.SlamImpactPacket;
import com.soulless.network.SlamStartPacket;
import com.soulless.registry.ModParticles;
import com.soulless.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
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
    public static final float MAX_DAMAGE = 30.0F;
    public static final float MIN_DAMAGE = 14.0F;
    public static final float RADIUS = 5.0F;
    public static final int CURSE_SECONDS = 8;
    public static final int CURSE_AMPLIFIER = 1;
    public static final int COOLDOWN_TICKS = 160;

    /** Ticks of the leap before the sword is driven down. Mirrored by the client animation. */
    public static final int WINDUP_TICKS = 7;
    private static final int MAX_AIR_TICKS = 22;

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    public static void tryStart(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SoulForgedItem item) || !player.isAlive() || player.isSpectator()) return;
        if (player.getCooldowns().isOnCooldown(item) || ACTIVE.containsKey(player.getUUID())) return;

        ServerLevel level = player.serverLevel();
        player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);
        ACTIVE.put(player.getUUID(), level.getGameTime());

        item.triggerAnim(player, GeoItem.getOrAssignId(stack, level), SoulForgedItem.CONTROLLER, SoulForgedItem.SLAM_ANIM);
        ModNetwork.sendTrackingAndSelf(player, new SlamStartPacket(player.getId()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM_WINDUP.get(), SoundSource.PLAYERS, 1.2F, 1.0F);

        Vec3 look = horizontalLook(player);
        player.setDeltaMovement(look.x * 0.35, 0.62, look.z * 0.35);
        player.hurtMarked = true;
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, Long>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            long age = player.serverLevel().getGameTime() - entry.getValue();
            player.fallDistance = 0;
            if (age == WINDUP_TICKS) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x * 0.3, -1.8, v.z * 0.3);
                player.hurtMarked = true;
            }
            if ((age > WINDUP_TICKS && player.onGround()) || age >= MAX_AIR_TICKS) {
                impact(player);
                it.remove();
            }
        }
        CrackManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private static void impact(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = horizontalLook(player);
        Vec3 center = player.position().add(look.scale(1.3));
        BlockPos groundPos = CrackManager.findSurface(level, BlockPos.containing(center.x, player.getY(), center.z));
        double groundY = groundPos != null ? groundPos.getY() + 1.0 : player.getY();
        center = new Vec3(center.x, groundY, center.z);

        AABB box = new AABB(center, center).inflate(RADIUS, 3.0, RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (target.isAlliedTo(player) || (target instanceof TamableAnimal pet && pet.isOwnedBy(player))) continue;
            double dist = Math.sqrt(Mth.square(target.getX() - center.x) + Mth.square(target.getZ() - center.z));
            if (dist > RADIUS) continue;
            float damage = (float) Mth.lerp(dist / RADIUS, MAX_DAMAGE, MIN_DAMAGE);
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), damage);
            Vec3 away = new Vec3(target.getX() - center.x, 0, target.getZ() - center.z);
            away = away.lengthSqr() < 1.0E-4 ? look : away.normalize();
            double push = 1.1 - 0.6 * (dist / RADIUS);
            target.push(away.x * push, 0.75, away.z * push);
            target.hurtMarked = true;
            SoulCurse.apply(target, CURSE_SECONDS * 20, CURSE_AMPLIFIER, player);
        }

        level.playSound(null, center.x, center.y, center.z, ModSounds.SLAM_IMPACT.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.55F);

        // Shockwave: rings of soul embers, dragon breath and debris from the struck block.
        BlockState ground = groundPos != null ? level.getBlockState(groundPos) : null;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.2, center.z, 1, 0, 0, 0, 0);
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
        ModNetwork.sendNear(level, center, 40, new SlamImpactPacket(center.x, center.y, center.z, RADIUS));
        player.fallDistance = 0;
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
    }

    private SlamManager() {}
}
