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

    public static final RegistryObject<EntityType<com.soulless.boss.IceShardEntity>> ICE_SHARD = ENTITIES.register("ice_shard",
            () -> EntityType.Builder.<com.soulless.boss.IceShardEntity>of(com.soulless.boss.IceShardEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(8).updateInterval(20).build("ice_shard"));

    public static final RegistryObject<EntityType<com.soulless.ability.SoulSlashEntity>> SOUL_SLASH = ENTITIES.register("soul_slash",
            () -> EntityType.Builder.<com.soulless.ability.SoulSlashEntity>of(com.soulless.ability.SoulSlashEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(8).updateInterval(1).build("soul_slash"));

    /** Talking characters (npc.NpcEntity): villager-sized. */
    public static final RegistryObject<EntityType<com.soulless.npc.SenseiEntity>> SENSEI = ENTITIES.register("sensei",
            () -> EntityType.Builder.<com.soulless.npc.SenseiEntity>of(com.soulless.npc.SenseiEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("sensei"));

    /** A white fox tamed with sweet berries (pet.TamedFox): a vanilla fox's size. */
    public static final RegistryObject<EntityType<com.soulless.pet.TamedFox>> TAMED_FOX = ENTITIES.register("tamed_fox",
            () -> EntityType.Builder.<com.soulless.pet.TamedFox>of(com.soulless.pet.TamedFox::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.7F).clientTrackingRange(8).build("tamed_fox"));

    /** Kitsune, asleep in the astral (npc.KitsuneEntity): about three blocks long lying down. */
    public static final RegistryObject<EntityType<com.soulless.npc.KitsuneEntity>> KITSUNE = ENTITIES.register("kitsune",
            () -> EntityType.Builder.<com.soulless.npc.KitsuneEntity>of(com.soulless.npc.KitsuneEntity::new, MobCategory.MISC)
                    .sized(2.0F, 1.6F).fireImmune().clientTrackingRange(10).build("kitsune"));

    /** The Renegade, the sensei's fallen student (boss.RenegadeEntity): a player's size. */
    public static final RegistryObject<EntityType<com.soulless.boss.RenegadeEntity>> RENEGADE = ENTITIES.register("renegade",
            () -> EntityType.Builder.<com.soulless.boss.RenegadeEntity>of(com.soulless.boss.RenegadeEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).fireImmune().clientTrackingRange(10).build("renegade"));

    public static final RegistryObject<EntityType<com.soulless.boss.RedSlashEntity>> RED_SLASH = ENTITIES.register("red_slash",
            () -> EntityType.Builder.<com.soulless.boss.RedSlashEntity>of(com.soulless.boss.RedSlashEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(8).build("red_slash"));

    public static final RegistryObject<EntityType<com.soulless.boss.ChainSeatEntity>> CHAIN_SEAT = ENTITIES.register("chain_seat",
            () -> EntityType.Builder.<com.soulless.boss.ChainSeatEntity>of(com.soulless.boss.ChainSeatEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.2F).fireImmune().clientTrackingRange(8).build("chain_seat"));

    public static final RegistryObject<EntityType<com.soulless.boss.SpectralBladeEntity>> SPECTRAL_BLADE = ENTITIES.register("spectral_blade",
            () -> EntityType.Builder.<com.soulless.boss.SpectralBladeEntity>of(com.soulless.boss.SpectralBladeEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).fireImmune().clientTrackingRange(8).updateInterval(1).build("spectral_blade"));

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WAILING_REAPER.get(), WailingReaperEntity.createAttributes().build());
        event.put(SENSEI.get(), com.soulless.npc.NpcEntity.createAttributes().build());
        event.put(TAMED_FOX.get(), com.soulless.pet.TamedFox.createAttributes().build());
        event.put(KITSUNE.get(), com.soulless.npc.KitsuneEntity.createAttributes().build());
        event.put(RENEGADE.get(), com.soulless.boss.RenegadeEntity.createAttributes().build());
    }

    private ModEntities() {}
}
