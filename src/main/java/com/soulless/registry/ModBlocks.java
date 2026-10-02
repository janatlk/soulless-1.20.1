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

    private ModBlocks() {}
}
