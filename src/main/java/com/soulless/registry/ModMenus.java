package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.forge.SoulAnvilMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Soulless.MODID);

    public static final RegistryObject<MenuType<SoulAnvilMenu>> SOUL_ANVIL = MENUS.register("soul_anvil",
            () -> new MenuType<>(SoulAnvilMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
