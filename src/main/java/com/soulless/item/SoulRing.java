package com.soulless.item;

import com.soulless.registry.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * Is the Soulsmith's Ring worn? In the Curios ring slot when Curios is installed, otherwise anywhere in the
 * inventory. Works on both sides (Curios syncs worn items to clients).
 */
public final class SoulRing {
    /** Soul Rend runs this much faster with the ring. */
    public static final float REND_SPEED = 1.1F;
    /** Soul Crush with the ring: leap power and damage (and the damage cap) multipliers. */
    public static final double SLAM_LEAP_UP = 1.75;
    public static final float SLAM_DAMAGE_MULT = 1.75F;
    /** The upward kick when Soul Crush is started in mid-air. */
    public static final double SLAM_AIR_KICK = 0.6;

    private static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static boolean worn(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (CURIOS) return CuriosCompat.wearing(player, ModItems.SOUL_RING.get());
        return player.getInventory().items.stream().anyMatch(s -> s.is(ModItems.SOUL_RING.get()));
    }

    /** The ring's gold, shown on the sword only while an empowered ability runs. */
    private static final net.minecraft.core.particles.DustParticleOptions GOLD =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.78F, 0.22F), 1.1F);

    /** Golden sparks around {@code at} (server side). */
    public static void sparkle(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at, int count, double spread) {
        level.sendParticles(GOLD, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, at.x, at.y, at.z, Math.max(1, count / 3), spread, spread, spread, 0.6);
    }

    /** Where the sword is, roughly: in front of the chest, a little to the right. */
    public static net.minecraft.world.phys.Vec3 swordPoint(Player player) {
        net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0, player.getYRot());
        net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
        return player.position().add(0, 1.4, 0).add(look.scale(0.5)).add(right.scale(0.45));
    }

    public static float rendSpeed(Entity entity) {
        return worn(entity) ? REND_SPEED : 1F;
    }

    private SoulRing() {}
}
