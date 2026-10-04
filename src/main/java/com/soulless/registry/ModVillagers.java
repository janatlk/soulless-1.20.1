package com.soulless.registry;

import com.google.common.collect.ImmutableSet;
import com.soulless.Soulless;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The sensei: a villager profession with no job site (no ordinary villager can take it up); it only lives in its dojo
 * (world.SenseiDojoPiece). Its trades: item.SenseiTrades. Its look: textures/entity/villager/profession/sensei.png,
 * no big nose (client.SenseiRender).
 */
public final class ModVillagers {
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, Soulless.MODID);

    public static final RegistryObject<VillagerProfession> SENSEI = PROFESSIONS.register("sensei",
            () -> new VillagerProfession("sensei", poi -> false, poi -> false, ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_WEAPONSMITH));

    private ModVillagers() {}
}
