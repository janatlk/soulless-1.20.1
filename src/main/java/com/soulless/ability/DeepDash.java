package com.soulless.ability;

import com.soulless.Soulless;
import com.soulless.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Breath of the Deep, the whole Leviathan set's ability (V): the Leviathan's own charge. In water the wearer shoots
 * forward along their gaze some ten blocks, striking and throwing aside whatever they pass through; on land it is a
 * short leap. Then it rests (the chestplate's cooldown).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class DeepDash {
    public static final int COOLDOWN_TICKS = 240;
    private static final int DASH_TICKS = 8;
    private static final float DAMAGE = 9;

    private record Dash(Vec3 dir, int[] left, Set<UUID> hit) {}

    private static final Map<UUID, Dash> ACTIVE = new HashMap<>();

    /** The whole Leviathan set worn. */
    public static boolean worn(net.minecraft.world.entity.player.Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.LEVIATHAN_HELMET.get())
                && p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.LEVIATHAN_CHESTPLATE.get())
                && p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.LEVIATHAN_LEGGINGS.get())
                && p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.LEVIATHAN_BOOTS.get());
    }

    /** The rest after a dash: 12 s, 1.5 s less for each Quick Breath on the pieces worn. */
    public static int cooldown(net.minecraft.world.entity.player.Player p) {
        return COOLDOWN_TICKS - 30 * com.soulless.upgrade.Upgrades.worn(p, com.soulless.upgrade.Upgrades.DASH_COOLDOWN);
    }

    public static void tryStart(ServerPlayer p) {
        if (!worn(p) || ACTIVE.containsKey(p.getUUID())) return;
        var item = ModItems.LEVIATHAN_CHESTPLATE.get();
        if (p.getCooldowns().isOnCooldown(item)) {
            int s = (int) Math.ceil(p.getCooldowns().getCooldownPercent(item, 0) * cooldown(p) / 20F);
            p.displayClientMessage(Component.translatable("message.soulless.dash_cooldown", s), true);
            return;
        }
        boolean wet = p.isInWater();
        Vec3 dir = p.getLookAngle();
        if (!wet) dir = new Vec3(dir.x, Math.max(dir.y, 0.25), dir.z).normalize();
        // the anvil's upgrades, counted over the pieces worn (each up to 4)
        int water = com.soulless.upgrade.Upgrades.worn(p, com.soulless.upgrade.Upgrades.DASH_WATER),
                land = com.soulless.upgrade.Upgrades.worn(p, com.soulless.upgrade.Upgrades.DASH_LAND);
        double reach = wet ? 1 + 0.15 * water : 1 + 0.25 * land;
        ACTIVE.put(p.getUUID(), new Dash(dir.scale((wet ? 1.9 : 1.1) * reach), new int[]{wet ? DASH_TICKS : 4}, new HashSet<>()));
        p.getCooldowns().addCooldown(item, cooldown(p));
        p.level().playSound(null, p.blockPosition(), wet ? SoundEvents.WARDEN_SONIC_CHARGE : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.2F, wet ? 1.4F : 0.7F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_FLOP, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    /** Each piece worn: 5% less damage taken in water or rain. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.isInWaterOrRain()) return;
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (e.getItemBySlot(s).getItem() instanceof com.soulless.item.LeviathanArmorItem) n++;
        }
        if (n > 0) event.setAmount(event.getAmount() * (1 - PER_PIECE * n));
    }

    public static final float PER_PIECE = 0.05F;

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Dash d = ACTIVE.get(p.getUUID());
        if (d == null) return;
        if (!p.isAlive() || d.left[0]-- <= 0) {
            ACTIVE.remove(p.getUUID());
            return;
        }
        p.setDeltaMovement(d.dir);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ServerLevel level = p.serverLevel();
        Vec3 c = p.position().add(0, p.getBbHeight() / 2, 0);
        level.sendParticles(p.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD, c.x, c.y, c.z, 10, 0.4, 0.5, 0.4, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.01);
        // whatever it passes through: struck once and thrown aside
        var box = p.getBoundingBox().inflate(1.2);
        for (LivingEntity e : com.soulless.combat.Hitboxes.struck(level, box, e -> e != p && e.isAlive(), x -> x.getBoundingBox().intersects(box))) {
            if (!d.hit.add(e.getUUID())) continue;
            e.hurt(p.damageSources().playerAttack(p), DAMAGE + 2.5F * com.soulless.upgrade.Upgrades.worn(p, com.soulless.upgrade.Upgrades.DASH_DAMAGE));
            Vec3 side = e.position().subtract(p.position());
            side = new Vec3(side.x, 0, side.z);
            if (side.lengthSqr() < 1e-4) side = new Vec3(-d.dir.z, 0, d.dir.x);
            e.setDeltaMovement(side.normalize().scale(1.1).add(0, 0.3, 0));
            e.hurtMarked = true;
            level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1F, 0.6F);
        }
    }

    private DeepDash() {}
}
