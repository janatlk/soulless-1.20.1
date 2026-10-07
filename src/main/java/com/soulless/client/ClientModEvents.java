package com.soulless.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulless.Soulless;
import com.soulless.registry.ModParticles;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Soulless.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping SLAM_KEY = new KeyMapping("key.soulless.slam", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.soulless");

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(SLAM_KEY);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SOUL_EMBER.get(), SoulEmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GOLD_EMBER.get(), SoulEmberParticle.Provider::new);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.soulless.registry.ModEntities.WAILING_REAPER.get(), BossRenderers.Reaper::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SOUL_BOLT.get(), BossRenderers.Bolt::new);
        event.registerEntityRenderer((net.minecraft.world.entity.EntityType) com.soulless.registry.ModEntities.ABYSS_HEAD.get(),
                ctx -> new BossRenderers.AbyssHead(ctx));
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SOUL_ERUPTION.get(), BossRenderers.Invisible::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.ICE_SHARD.get(), BossRenderers.IceShard::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SOUL_SLASH.get(), BossRenderers.Slash::new);
        event.registerEntityRenderer((net.minecraft.world.entity.EntityType) com.soulless.registry.ModEntities.TAMED_FOX.get(),
                ctx -> new net.minecraft.client.renderer.entity.FoxRenderer(ctx));
        event.registerEntityRenderer(com.soulless.registry.ModEntities.KITSUNE.get(), KitsuneRenderer::new);
        // talking characters
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SENSEI.get(), ctx -> new NpcRenderer<>(ctx, false,
                NpcRenderer.VILLAGER, NpcRenderer.PLAINS, com.soulless.Soulless.id("textures/entity/npc/sensei.png")));
    }

    @SubscribeEvent
    public static void onTooltipFactories(net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SectionTooltip.class, s -> s);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "abilities", new AbilityHud());
    }

    @SubscribeEvent
    public static void onModels(ModelEvent.RegisterAdditional event) {
        event.register(SoulForgedRenderer.ICON_MODEL);
        event.register(KatanaRenderer.ICON_MODEL);
        event.register(KatanaRenderer.TRAINING_ICON_MODEL);
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        KatanaAnim.register();
        SwimRendAnim.register();
    }

    private ClientModEvents() {}
}
