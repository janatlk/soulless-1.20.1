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

    /** Read it to learn the katana's parry. */
    public static final RegistryObject<Item> PARRY_SCROLL = ITEMS.register("parry_scroll",
            () -> new com.soulless.item.ParryScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> SENSEI_SPAWN_EGG = ITEMS.register("sensei_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.SENSEI, 0x2B2420, 0xB3343A, new Item.Properties()));

    /** Opens the Soul Altar. */
    public static final RegistryObject<Item> ABYSSAL_HEART = material("abyssal_heart", Rarity.EPIC, true);

    private static RegistryObject<Item> material(String name, Rarity rarity, boolean foil) {
        return ITEMS.register(name, () -> new SoulMaterialItem(new Item.Properties().fireResistant().rarity(rarity), foil));
    }

    private ModItems() {}
}
