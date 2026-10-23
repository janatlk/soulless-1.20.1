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

    /** The Scaled Chestplate's Breath of the Deep. */
    public static final KeyMapping DASH_KEY = new KeyMapping("key.soulless.dash", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.soulless");

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(SLAM_KEY);
        event.register(DASH_KEY);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SOUL_EMBER.get(), SoulEmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.ABYSS_BREATH.get(), AbyssBreathParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SOUL_WISP.get(), SoulWisp.Sprites::new);
        event.registerSpriteSet(ModParticles.GOLD_EMBER.get(), SoulEmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD_EMBER.get(), s -> new RenegadeParticle.Provider(s, RenegadeParticle.Kind.EMBER));
        event.registerSpriteSet(ModParticles.DARK_SMOKE.get(), s -> new RenegadeParticle.Provider(s, RenegadeParticle.Kind.SMOKE));
        event.registerSpriteSet(ModParticles.RED_SPARK.get(), s -> new RenegadeParticle.Provider(s, RenegadeParticle.Kind.SPARK));
        event.registerSpriteSet(ModParticles.CRIMSON_SLASH.get(), s -> new RenegadeParticle.Provider(s, RenegadeParticle.Kind.SLASH));
        event.registerSpriteSet(ModParticles.CRIMSON_BURST.get(), s -> new RenegadeParticle.Provider(s, RenegadeParticle.Kind.BURST));
        event.registerSpriteSet(ModParticles.ASH_FLAKE.get(), s -> new RenegadeParticle.Provider(s, RenegadeParticle.Kind.ASH));
    }

    @SubscribeEvent
    public static void onLayers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LeviathanArmorModel.OUTER, LeviathanArmorModel::outer);
        event.registerLayerDefinition(LeviathanArmorModel.INNER, LeviathanArmorModel::inner);
    }

    @SubscribeEvent
    public static void onLayersBaked(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
        LeviathanArmorModels.reset();
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.soulless.registry.ModEntities.WAILING_REAPER.get(), BossRenderers.Reaper::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.LEVIATHAN.get(), LeviathanRenderer::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SEEKING_EYE.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.0F, true));
        event.registerBlockEntityRenderer(com.soulless.registry.ModBlocks.SOUL_ANVIL_BE.get(), com.soulless.client.anvil.SoulAnvilRenderer::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SOUL_BOLT.get(), BossRenderers.Bolt::new);
        event.registerEntityRenderer((net.minecraft.world.entity.EntityType) com.soulless.registry.ModEntities.ABYSS_HEAD.get(),
                ctx -> new BossRenderers.AbyssHead(ctx));
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SOUL_ERUPTION.get(), BossRenderers.Invisible::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.ICE_SHARD.get(), BossRenderers.IceShard::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SOUL_SLASH.get(), BossRenderers.Slash::new);
        event.registerEntityRenderer((net.minecraft.world.entity.EntityType) com.soulless.registry.ModEntities.TAMED_FOX.get(),
                ctx -> new net.minecraft.client.renderer.entity.FoxRenderer(ctx));
        event.registerEntityRenderer(com.soulless.registry.ModEntities.RENEGADE.get(), RenegadeRenderer::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.RED_SLASH.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.CHAIN_SEAT.get(), ChainSeatRenderer::new);
        event.registerEntityRenderer(com.soulless.registry.ModEntities.SPECTRAL_BLADE.get(), RenegadeRenderer.Blade::new);
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
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        KatanaAnim.register();
        ChainedAnim.register();
        SwimRendAnim.register();
    }

    private ClientModEvents() {}
}
