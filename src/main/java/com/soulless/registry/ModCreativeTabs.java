package com.soulless.registry;

import com.soulless.Soulless;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Soulless.MODID);

    public static final RegistryObject<CreativeModeTab> SOULLESS = TABS.register("soulless", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.soulless"))
            .icon(() -> new ItemStack(ModItems.SOUL_FORGED.get()))
            .displayItems((params, out) -> {
                out.accept(ModBlocks.SOUL_ANVIL_ITEM.get());
                out.accept(ModBlocks.WRAITH_IRON_ORE_ITEM.get());
                out.accept(ModBlocks.DEEPSLATE_WRAITH_IRON_ORE_ITEM.get());
                out.accept(ModItems.SOUL_VESSEL.get());
                out.accept(ModItems.RAW_WRAITH_IRON.get());
                out.accept(ModItems.WRAITH_IRON_INGOT.get());
                out.accept(ModItems.REAPER_SOUL.get());
                out.accept(ModItems.RENEGADE_SIGIL.get());
                out.accept(ModItems.EYE_OF_LAMENT.get());
                out.accept(ModItems.EYE_OF_DROWNED.get());
                out.accept(ModItems.LEVIATHAN_SPAWN_EGG.get());
                out.accept(ModItems.SOUL_FORGED.get());
                out.accept(ModItems.SOUL_KATANA.get());
                out.accept(ModItems.ABYSSAL_HEART.get());
                out.accept(ModItems.SOUL_RING.get());
                out.accept(ModBlocks.SOUL_ALTAR_ITEM.get());
            })
            .build());

    private ModCreativeTabs() {}
}
