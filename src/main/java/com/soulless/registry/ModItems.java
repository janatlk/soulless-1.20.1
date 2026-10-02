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

    // Crafting materials. No source yet: they are meant to drop from future bosses.
    public static final RegistryObject<Item> WRAITHSTEEL_INGOT = material("wraithsteel_ingot", Rarity.UNCOMMON, false);
    public static final RegistryObject<Item> SOULFLAME_CORE = material("soulflame_core", Rarity.RARE, false);
    public static final RegistryObject<Item> ANCIENT_HILT = material("ancient_hilt", Rarity.RARE, false);
    public static final RegistryObject<Item> ABYSSAL_HEART = material("abyssal_heart", Rarity.EPIC, true);

    private static RegistryObject<Item> material(String name, Rarity rarity, boolean foil) {
        return ITEMS.register(name, () -> new SoulMaterialItem(new Item.Properties().fireResistant().rarity(rarity), foil));
    }

    private ModItems() {}
}
