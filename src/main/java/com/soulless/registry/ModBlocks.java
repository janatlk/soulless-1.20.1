package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.boss.SoulAltarBlock;
import com.soulless.boss.SoulAltarBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Soulless.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Soulless.MODID);

    public static final RegistryObject<Block> SOUL_ALTAR = BLOCKS.register("soul_altar", () -> new SoulAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(SoulAltarBlock.FILLED) ? 12 : 6)));

    public static final RegistryObject<Item> SOUL_ALTAR_ITEM = ModItems.ITEMS.register("soul_altar",
            () -> new BlockItem(SOUL_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SoulAltarBlockEntity>> SOUL_ALTAR_BE = BLOCK_ENTITIES.register("soul_altar",
            () -> BlockEntityType.Builder.of(SoulAltarBlockEntity::new, SOUL_ALTAR.get()).build(null));

    /** Wraith Iron ore, in stone and in deepslate (worldgen: configured_feature/wraith_iron_ore). */
    public static final RegistryObject<Block> WRAITH_IRON_ORE = BLOCKS.register("wraith_iron_ore", () -> new net.minecraft.world.level.block.DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 3.0F).requiresCorrectToolForDrops()
                    .lightLevel(s -> 3), net.minecraft.util.valueproviders.UniformInt.of(2, 5)));
    public static final RegistryObject<Block> DEEPSLATE_WRAITH_IRON_ORE = BLOCKS.register("deepslate_wraith_iron_ore", () -> new net.minecraft.world.level.block.DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE).lightLevel(s -> 3), net.minecraft.util.valueproviders.UniformInt.of(2, 5)));
    /** The Soul Anvil: every weapon of the mod is forged here (forge.SoulAnvilRecipes). */
    public static final RegistryObject<Block> SOUL_ANVIL = BLOCKS.register("soul_anvil", () -> new com.soulless.forge.SoulAnvilBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.ANVIL).lightLevel(s -> 4).noOcclusion()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.soulless.forge.SoulAnvilBlockEntity>> SOUL_ANVIL_BE = BLOCK_ENTITIES.register("soul_anvil",
            () -> BlockEntityType.Builder.of(com.soulless.forge.SoulAnvilBlockEntity::new, SOUL_ANVIL.get()).build(null));

    public static final RegistryObject<Item> WRAITH_IRON_ORE_ITEM = ModItems.ITEMS.register("wraith_iron_ore",
            () -> new BlockItem(WRAITH_IRON_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DEEPSLATE_WRAITH_IRON_ORE_ITEM = ModItems.ITEMS.register("deepslate_wraith_iron_ore",
            () -> new BlockItem(DEEPSLATE_WRAITH_IRON_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SOUL_ANVIL_ITEM = ModItems.ITEMS.register("soul_anvil",
            () -> new BlockItem(SOUL_ANVIL.get(), new Item.Properties().rarity(Rarity.RARE)));

    private ModBlocks() {}
}
