package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.item.SoulForgedItem;
import com.soulless.item.SoulMaterialItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Soulless.MODID);

    public static final RegistryObject<Item> SOUL_FORGED = ITEMS.register("soul_forged",
            () -> new SoulForgedItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    /** A fast two-handed katana: jodan stance and alternating diagonal cuts (abilities still to come). */
    public static final RegistryObject<Item> SOUL_KATANA = ITEMS.register("soul_katana",
            () -> new com.soulless.item.SoulKatanaItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    /** Worn with Soul Forged (Curios ring slot): a higher, harder Soul Crush usable in mid-air, a faster Soul Rend with no recovery. */
    public static final RegistryObject<Item> SOUL_RING = ITEMS.register("soul_ring",
            () -> new com.soulless.item.SoulRingItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));


    /** Wraith Iron: mined from its ore deep below (raw), smelted into ingots - what the katana is forged from. */
    public static final RegistryObject<Item> LEVIATHAN_SCALE = ITEMS.register("leviathan_scale", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> RAW_WRAITH_IRON = ITEMS.register("raw_wraith_iron", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WRAITH_IRON_INGOT = ITEMS.register("wraith_iron_ingot", () -> new Item(new Item.Properties()));

    /** The Wailing Reaper's soul, left in its chest: forges Soul Forged at the Soul Anvil. */
    public static final RegistryObject<Item> REAPER_SOUL = material("reaper_soul", Rarity.EPIC, true);

    /** Holds the souls the Soul Anvil spends (item.SoulVesselItem). */
    public static final RegistryObject<Item> SOUL_VESSEL = ITEMS.register("soul_vessel",
            () -> new com.soulless.item.SoulVesselItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Calls the Renegade (item.RenegadeSigilItem). */
    public static final RegistryObject<Item> RENEGADE_SIGIL = ITEMS.register("renegade_sigil",
            () -> new com.soulless.item.RenegadeSigilItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    /** Seeking eyes: thrown, they fly toward their boss's structure (item.SeekingEyeItem). */
    public static final RegistryObject<Item> EYE_OF_LAMENT = ITEMS.register("eye_of_lament",
            () -> new com.soulless.item.SeekingEyeItem(new Item.Properties().rarity(Rarity.RARE), com.soulless.registry.ModStructures.EYE_OF_LAMENT, "lament"));
    public static final RegistryObject<Item> EYE_OF_DROWNED = ITEMS.register("eye_of_drowned",
            () -> new com.soulless.item.SeekingEyeItem(new Item.Properties().rarity(Rarity.RARE), com.soulless.registry.ModStructures.EYE_OF_DROWNED, "drowned"));

    public static final RegistryObject<Item> LEVIATHAN_SPAWN_EGG = ITEMS.register("leviathan_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.LEVIATHAN, 0x111318, 0xFF9A3A, new Item.Properties()));

    /** The Drowned One's Gills: breath in the water, none out of it (item.DrownedGills). */
    public static final RegistryObject<Item> DROWNED_GILLS = ITEMS.register("drowned_gills",
            () -> new com.soulless.item.DrownedGills.GillsItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** Opens the Soul Altar. */
    public static final RegistryObject<Item> ABYSSAL_HEART = material("abyssal_heart", Rarity.EPIC, true);

    /** The Leviathan's armour, forged at the Soul Anvil from its scales (item.LeviathanArmorItem). */
    public static final RegistryObject<Item> LEVIATHAN_HELMET = leviathanArmor("leviathan_helmet", net.minecraft.world.item.ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> LEVIATHAN_CHESTPLATE = leviathanArmor("leviathan_chestplate", net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> LEVIATHAN_LEGGINGS = leviathanArmor("leviathan_leggings", net.minecraft.world.item.ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> LEVIATHAN_BOOTS = leviathanArmor("leviathan_boots", net.minecraft.world.item.ArmorItem.Type.BOOTS);

    private static RegistryObject<Item> leviathanArmor(String name, net.minecraft.world.item.ArmorItem.Type type) {
        return ITEMS.register(name, () -> new com.soulless.item.LeviathanArmorItem(type, new Item.Properties().fireResistant().rarity(Rarity.EPIC)));
    }

    private static RegistryObject<Item> material(String name, Rarity rarity, boolean foil) {
        return ITEMS.register(name, () -> new SoulMaterialItem(new Item.Properties().fireResistant().rarity(rarity), foil));
    }

    private ModItems() {}
}
