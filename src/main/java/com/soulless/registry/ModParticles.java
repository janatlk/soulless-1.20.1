package com.soulless.registry;

import com.soulless.Soulless;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Soulless.MODID);

    public static final RegistryObject<SimpleParticleType> SOUL_EMBER = PARTICLES.register("soul_ember", () -> new SimpleParticleType(false));
    /** The Soulsmith's Ring "skin": the same flame tongues in gold. */
    public static final RegistryObject<SimpleParticleType> GOLD_EMBER = PARTICLES.register("gold_ember", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
