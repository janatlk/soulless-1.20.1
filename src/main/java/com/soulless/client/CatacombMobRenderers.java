package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soulless.Soulless;
import com.soulless.mob.ClamMimicEntity;
import com.soulless.mob.LampkeeperEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The catacombs' small dwellers: the Lampkeeper (translucent, fading when its lantern is out) and the Clam Mimic. */
public final class CatacombMobRenderers {
    public static class Lampkeeper extends GeoEntityRenderer<LampkeeperEntity> {
        public Lampkeeper(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(Soulless.id("lampkeeper"), false));
            // its glow (eyes, lantern, the soul in its hem) goes out with the lantern
            addRenderLayer(new AutoGlowingGeoLayer<>(this) {
                @Override
                public void render(PoseStack ps, LampkeeperEntity e, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                                   VertexConsumer buffer, float partial, int light, int overlay) {
                    if (e.lit()) super.render(ps, e, model, type, buffers, buffer, partial, light, overlay);
                }
            });
            shadowRadius = 0.3F;
        }

        @Override
        public RenderType getRenderType(LampkeeperEntity e, ResourceLocation texture, MultiBufferSource buffers, float partial) {
            return RenderType.entityTranslucent(texture);
        }

        /** Its lantern out, it is all but invisible - a faint shape in the water. */
        @Override
        public void actuallyRender(PoseStack ps, LampkeeperEntity e, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                                   VertexConsumer buffer, boolean reRender, float partial, int light, int overlay,
                                   float r, float g, float b, float a) {
            super.actuallyRender(ps, e, model, type, buffers, buffer, reRender, partial, light, overlay, r, g, b, a * (e.lit() ? 1 : 0.12F));
        }
    }

    public static class ClamMimic extends GeoEntityRenderer<ClamMimicEntity> {
        public ClamMimic(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(Soulless.id("clam_mimic"), false));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            shadowRadius = 0.6F;
        }

        @Override
        protected float getDeathMaxRotation(ClamMimicEntity e) {
            return 0;
        }
    }

    private CatacombMobRenderers() {}
}
