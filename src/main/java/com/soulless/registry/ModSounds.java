package com.soulless.registry;

import com.soulless.Soulless;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Soulless.MODID);

    public static final RegistryObject<SoundEvent> SLAM_WINDUP = sound("slam_windup");
    public static final RegistryObject<SoundEvent> SLAM_IMPACT = sound("slam_impact");
    public static final RegistryObject<SoundEvent> CURSE_IGNITE = sound("curse_ignite");
    public static final RegistryObject<SoundEvent> REAPER_SCREAM = sound("reaper_scream");
    public static final RegistryObject<SoundEvent> BOLT_FLY = sound("bolt_fly");
    public static final RegistryObject<SoundEvent> ERUPTION = sound("eruption");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Soulless.id(name)));
    }

    private ModSounds() {}
}
