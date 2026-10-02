package com.soulless;

import com.mojang.logging.LogUtils;
import com.soulless.network.ModNetwork;
import com.soulless.registry.ModCreativeTabs;
import com.soulless.registry.ModEffects;
import com.soulless.registry.ModItems;
import com.soulless.registry.ModParticles;
import com.soulless.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Soulless.MODID)
public class Soulless {
    public static final String MODID = "soulless";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Soulless() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        bus.addListener(this::commonSetup);
        // Custom ArmPose enum values must exist before HumanoidModel first switches over the enum.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.soulless.client.SoulArmPose::init);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
