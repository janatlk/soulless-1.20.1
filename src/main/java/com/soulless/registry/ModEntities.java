package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.boss.AbyssHeadEntity;
import com.soulless.boss.SoulBoltEntity;
import com.soulless.boss.SoulEruptionEntity;
import com.soulless.boss.WailingReaperEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = Soulless.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Soulless.MODID);

    /** About four times a player's height; floats on soul flames instead of legs. */
    public static final RegistryObject<EntityType<WailingReaperEntity>> WAILING_REAPER = ENTITIES.register("wailing_reaper",
            () -> EntityType.Builder.of(WailingReaperEntity::new, MobCategory.MONSTER)
                    .sized(2.0F, 7.0F).fireImmune().clientTrackingRange(16).build("wailing_reaper"));

    public static final RegistryObject<EntityType<SoulBoltEntity>> SOUL_BOLT = ENTITIES.register("soul_bolt",
            () -> EntityType.Builder.<SoulBoltEntity>of(SoulBoltEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).fireImmune().clientTrackingRange(8).updateInterval(1).build("soul_bolt"));

    public static final RegistryObject<EntityType<AbyssHeadEntity>> ABYSS_HEAD = ENTITIES.register("abyss_head",
            () -> EntityType.Builder.<AbyssHeadEntity>of(AbyssHeadEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).fireImmune().clientTrackingRange(8).updateInterval(1).build("abyss_head"));

    public static final RegistryObject<EntityType<SoulEruptionEntity>> SOUL_ERUPTION = ENTITIES.register("soul_eruption",
            () -> EntityType.Builder.<SoulEruptionEntity>of(SoulEruptionEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.5F).fireImmune().clientTrackingRange(8).build("soul_eruption"));

    public static final RegistryObject<EntityType<com.soulless.ability.SoulSlashEntity>> SOUL_SLASH = ENTITIES.register("soul_slash",
            () -> EntityType.Builder.<com.soulless.ability.SoulSlashEntity>of(com.soulless.ability.SoulSlashEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(8).updateInterval(1).build("soul_slash"));

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WAILING_REAPER.get(), WailingReaperEntity.createAttributes().build());
    }

    private ModEntities() {}
}
