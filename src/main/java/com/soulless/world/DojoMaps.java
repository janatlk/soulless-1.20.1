package com.soulless.world;

import com.soulless.Soulless;
import com.soulless.npc.SenseiQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * How the player first hears of the dojo (STORY_KATANA.md, act 0): a map to the nearest one, sold by cartographers
 * (journeyman, 14 emeralds and a compass) or found in some chests (village cartographers', shipwreck map chests,
 * dungeons - one time in five).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class DojoMaps {
    private static final String NAME = "item.soulless.map.dojo";
    private static final Set<ResourceLocation> CHESTS = Set.of(
            new ResourceLocation("chests/village/village_cartographer"),
            new ResourceLocation("chests/shipwreck_map"),
            new ResourceLocation("chests/simple_dungeon"));

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER) return;
        event.getTrades().get(3).add((trader, random) -> {
            if (!(trader.level() instanceof ServerLevel level)) return null;
            ItemStack map = SenseiQuest.explorerMap(level, trader.blockPosition(), SenseiQuest.DOJO_MAPS, NAME);
            if (map.isEmpty()) return null;
            return new MerchantOffer(new ItemStack(Items.EMERALD, 14), new ItemStack(Items.COMPASS), map, 12, 5, 0.2F);
        });
    }

    @SubscribeEvent
    public static void onLootTable(LootTableLoadEvent event) {
        if (!CHESTS.contains(event.getName())) return;
        event.getTable().addPool(LootPool.lootPool()
                .name("soulless_dojo_map")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.2F))
                .add(LootItem.lootTableItem(Items.MAP)
                        .apply(ExplorationMapFunction.makeExplorationMap().setDestination(SenseiQuest.DOJO_MAPS)
                                .setMapDecoration(MapDecoration.Type.RED_X).setZoom((byte) 2).setSkipKnownStructures(false))
                        .apply(SetNameFunction.setName(Component.translatable(NAME))))
                .build());
    }

    private DojoMaps() {}
}
