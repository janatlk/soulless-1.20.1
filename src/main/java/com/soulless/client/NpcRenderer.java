package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.soulless.npc.NpcEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A villager-shaped character: the vanilla villager model under any number of texture layers drawn one over another
 * (a skin, then clothes...), optionally without the big nose. Its name shows while the player looks at it (not while
 * a screen is open).
 * Register: {@code event.registerEntityRenderer(TYPE, ctx -> new NpcRenderer<>(ctx, false, base, overlay...))}.
 */
public class NpcRenderer<T extends NpcEntity> extends MobRenderer<T, VillagerModel<T>> {
    /** The vanilla villager skin and the plains clothes - the usual bottom layers. */
    public static final ResourceLocation VILLAGER = new ResourceLocation("textures/entity/villager/villager.png");
    public static final ResourceLocation PLAINS = new ResourceLocation("textures/entity/villager/type/plains.png");

    private final ResourceLocation base;

    public NpcRenderer(EntityRendererProvider.Context ctx, boolean nose, ResourceLocation base, ResourceLocation... overlays) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.base = base;
        getModel().getHead().getChild("nose").visible = nose;
        for (ResourceLocation tex : overlays) addLayer(new Overlay<>(this, tex));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return base;
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected boolean shouldShowName(T entity) {
        if (net.minecraft.client.Minecraft.getInstance().screen != null) return false;   // talking, or the portrait in the dialogue
        return super.shouldShowName(entity) || (entity == entityRenderDispatcher.crosshairPickEntity
                && net.minecraft.client.Minecraft.renderNames() && entity.distanceToSqr(entityRenderDispatcher.camera.getPosition()) < 64);
    }

    private static final class Overlay<T extends NpcEntity> extends RenderLayer<T, VillagerModel<T>> {
        private final ResourceLocation texture;

        Overlay(RenderLayerParent<T, VillagerModel<T>> parent, ResourceLocation texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            renderColoredCutoutModel(getParentModel(), texture, pose, buffers, light, entity, 1, 1, 1);
        }
    }
}
