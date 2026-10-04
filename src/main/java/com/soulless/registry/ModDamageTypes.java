package com.soulless.registry;

import com.soulless.Soulless;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Damage types are data-driven: see data/soulless/damage_type/soul_curse.json. */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> SOUL_CURSE = ResourceKey.create(Registries.DAMAGE_TYPE, Soulless.id("soul_curse"));

    /** The Kitsune ritual's own blade (a death by it reads "... died with honour"). */
    public static final ResourceKey<DamageType> SEPPUKU = ResourceKey.create(Registries.DAMAGE_TYPE, Soulless.id("seppuku"));

    public static DamageSource seppuku(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SEPPUKU));
    }

    public static DamageSource soulCurse(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SOUL_CURSE));
    }

    private ModDamageTypes() {}
}
