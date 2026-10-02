package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.boss.SoulAltarFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The altar's feature; where and how rarely it is placed is data: worldgen/placed_feature + forge/biome_modifier. */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Soulless.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SOUL_ALTAR = FEATURES.register("soul_altar",
            () -> new SoulAltarFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
