package com.soulless.registry;

import com.soulless.Soulless;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Soulless.MODID);

    /** A soul leaving a body toward a vessel (client.SoulWisp; spawned straight from SoulWispPacket). */
    public static final RegistryObject<SimpleParticleType> SOUL_WISP = PARTICLES.register("soul_wisp", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SOUL_EMBER = PARTICLES.register("soul_ember", () -> new SimpleParticleType(false));
    /** The Soulsmith's Ring "skin": the same flame tongues in gold. */
    public static final RegistryObject<SimpleParticleType> GOLD_EMBER = PARTICLES.register("gold_ember", () -> new SimpleParticleType(false));

    // the Renegade's (client/RenegadeParticle, tools/gen_renegade_particles.py)
    public static final RegistryObject<SimpleParticleType> BLOOD_EMBER = PARTICLES.register("blood_ember", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DARK_SMOKE = PARTICLES.register("dark_smoke", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> RED_SPARK = PARTICLES.register("red_spark", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CRIMSON_SLASH = PARTICLES.register("crimson_slash", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> CRIMSON_BURST = PARTICLES.register("crimson_burst", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> ASH_FLAKE = PARTICLES.register("ash_flake", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
