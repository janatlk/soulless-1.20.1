package com.soulless.item;

import com.soulless.registry.ModItems;
import com.soulless.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * The Soulsmith's Ring, worn in the Curios ring slot (or anywhere in the inventory without Curios). A "skin" for
 * Soul Forged: every effect of the sword - the souls it sheds, Soul Crush, Soul Rend, its trail and its wave -
 * turns from violet soul fire to gold. The only gameplay change: the sword's stuns last a second longer.
 */
public final class SoulRing {
    /** Extra stun the sword deals while the ring is worn. */
    public static final int STUN_BONUS_TICKS = 20;

    private static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static boolean worn(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (CURIOS) return CuriosCompat.wearing(player, ModItems.SOUL_RING.get());
        return player.getInventory().items.stream().anyMatch(s -> s.is(ModItems.SOUL_RING.get()));
    }

    /** The sword's flame tongues: violet soul fire, or gold with the ring. */
    public static ParticleOptions ember(boolean gold) {
        return gold ? ModParticles.GOLD_EMBER.get() : ModParticles.SOUL_EMBER.get();
    }

    public static ParticleOptions ember(Entity wielder) {
        return ember(worn(wielder));
    }

    /** Drifting souls, or golden embers with the ring. */
    public static ParticleOptions soul(boolean gold) {
        return gold ? ModParticles.GOLD_EMBER.get() : ParticleTypes.SOUL;
    }

    /** A violet accent (portal motes, dragon breath, soul flames) or a golden glint with the ring. */
    public static ParticleOptions accent(boolean gold, ParticleOptions violet) {
        return gold ? ParticleTypes.WAX_ON : violet;
    }

    private SoulRing() {}
}
