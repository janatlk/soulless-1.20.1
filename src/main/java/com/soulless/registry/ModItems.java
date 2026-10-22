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
    public static final RegistryObject<Item> RAW_WRAITH_IRON = ITEMS.register("raw_wraith_iron", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WRAITH_IRON_INGOT = ITEMS.register("wraith_iron_ingot", () -> new Item(new Item.Properties()));

    /** The Wailing Reaper's soul, left in its chest: forges Soul Forged at the Soul Anvil. */
    public static final RegistryObject<Item> REAPER_SOUL = material("reaper_soul", Rarity.EPIC, true);

    /** Opens the Soul Altar. */
    public static final RegistryObject<Item> ABYSSAL_HEART = material("abyssal_heart", Rarity.EPIC, true);

    private static RegistryObject<Item> material(String name, Rarity rarity, boolean foil) {
        return ITEMS.register(name, () -> new SoulMaterialItem(new Item.Properties().fireResistant().rarity(rarity), foil));
    }

    private ModItems() {}
}
