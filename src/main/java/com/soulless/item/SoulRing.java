package com.soulless.item;

import com.soulless.registry.ModItems;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;

/**
 * The Soulsmith's Ring, worn in the Curios ring slot (or anywhere in the inventory without Curios). Mostly
 * cosmetic: Soul Forged sheds golden embers instead of souls, the abilities spark with gold, and the sword's
 * stuns last a second longer.
 */
public final class SoulRing {
    /** Extra stun the sword deals while the ring is worn. */
    public static final int STUN_BONUS_TICKS = 20;

    /** Golden embers cooling to deep orange. */
    public static final DustColorTransitionOptions EMBER =
            new DustColorTransitionOptions(new Vector3f(1.0F, 0.85F, 0.3F), new Vector3f(0.85F, 0.35F, 0.05F), 0.9F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.22F), 1.1F);

    private static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static boolean worn(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (CURIOS) return CuriosCompat.wearing(player, ModItems.SOUL_RING.get());
        return player.getInventory().items.stream().anyMatch(s -> s.is(ModItems.SOUL_RING.get()));
    }

    /** Golden sparks around {@code at} (server side). */
    public static void sparkle(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(GOLD, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
        level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, Math.max(1, count / 3), spread, spread, spread, 0.6);
    }

    /** Where the sword is, roughly: in front of the chest, a little to the right. */
    public static Vec3 swordPoint(Player player) {
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 right = new Vec3(-look.z, 0, look.x);
        return player.position().add(0, 1.4, 0).add(look.scale(0.5)).add(right.scale(0.45));
    }

    private SoulRing() {}
}
