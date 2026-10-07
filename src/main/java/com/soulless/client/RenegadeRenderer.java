package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulless.Soulless;
import com.soulless.boss.RenegadeEntity;
import com.soulless.boss.SpectralBladeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Renegade (tools/gen_renegade.py): red eyes and blade edge glow; mid-blink he is not drawn at all. */
public class RenegadeRenderer extends GeoEntityRenderer<RenegadeEntity> {
    public RenegadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Soulless.id("renegade"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.5F;
    }

    @Override
    public void render(RenegadeEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.hidden()) return;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected float getDeathMaxRotation(RenegadeEntity animatable) {
        return 0;
    }

    /** His red blades of dark magic: a sword hanging point down, then flying point first; glowing. */
    public static class Blade extends EntityRenderer<SpectralBladeEntity> {
        private final ItemRenderer items;
        private final ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);

        public Blade(EntityRendererProvider.Context ctx) {
            super(ctx);
            items = ctx.getItemRenderer();
        }

        @Override
        public void render(SpectralBladeEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, e.yRotO, e.getYRot()) - 90));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, e.xRotO, e.getXRot()) - 45));
            pose.scale(1.4F, 1.4F, 1.4F);
            items.renderStatic(sword, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(SpectralBladeEntity e) {
            return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
        }
    }
}
