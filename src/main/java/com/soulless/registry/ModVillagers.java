package com.soulless.registry;

import com.google.common.collect.ImmutableSet;
import com.soulless.Soulless;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Legacy: the sensei used to be a villager with this profession. Kept registered only so old worlds still load
 * those villagers - npc.LegacySensei turns each into the sensei character (npc.SenseiEntity).
 */
public final class ModVillagers {
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, Soulless.MODID);

    public static final RegistryObject<VillagerProfession> SENSEI = PROFESSIONS.register("sensei",
            () -> new VillagerProfession("sensei", poi -> false, poi -> false, ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_WEAPONSMITH));

    private ModVillagers() {}
}
