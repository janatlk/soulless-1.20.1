package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.effect.SoulCurseEffect;
import com.soulless.effect.StunEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Soulless.MODID);

    public static final RegistryObject<MobEffect> SOUL_CURSE = EFFECTS.register("soul_curse", SoulCurseEffect::new);
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun", StunEffect::new);
    public static final RegistryObject<MobEffect> SOUL_RESONANCE = EFFECTS.register("soul_resonance",
            com.soulless.effect.SoulResonanceEffect::new);

    private ModEffects() {}
}
