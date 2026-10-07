package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.soulless.Soulless;
import com.soulless.npc.KitsuneEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Kitsune as an astral spirit: a white fox (geo kitsune_plain = the vanilla fox's shape, tools/gen_kitsune.py) drawn
 * with the game's own snow fox texture - the sleeping one while she sleeps - but see-through, tinted a cold
 * moonlight blue, glowing in the dark (full bright) and slowly breathing in and out of sight. Scaled up to about three
 * blocks. Awake, her head turns to whoever she talks to.
 */
public class KitsuneRenderer extends GeoEntityRenderer<KitsuneEntity> {
    private static final ResourceLocation SNOW = new ResourceLocation("textures/entity/fox/snow_fox.png");
    private static final ResourceLocation SNOW_SLEEP = new ResourceLocation("textures/entity/fox/snow_fox_sleep.png");

    public KitsuneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        withScale(1.9F);
        shadowRadius = 0.9F;
        shadowStrength = 0.35F;
    }

    @Override
    public RenderType getRenderType(KitsuneEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(KitsuneEntity animatable, float partialTick, int packedLight) {
        float t = (animatable.tickCount + partialTick) * 0.05F;
        int alpha = (int) (150 + Mth.sin(t) * 25);
        return Color.ofRGBA(205, 228, 255, alpha);
    }

    @Override
    public void render(KitsuneEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    private static final class Model extends DefaultedEntityGeoModel<KitsuneEntity> {
        Model() {
            super(Soulless.id("kitsune_plain"));
        }

        @Override
        public ResourceLocation getTextureResource(KitsuneEntity e) {
            return e.state() == KitsuneEntity.SLEEP ? SNOW_SLEEP : SNOW;
        }

        @Override
        public void setCustomAnimations(KitsuneEntity e, long instanceId, AnimationState<KitsuneEntity> state) {
            super.setCustomAnimations(e, instanceId, state);
            if (e.state() != KitsuneEntity.AWAKE) return;
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head == null || data == null) return;
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -30, 30) * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -55, 55) * Mth.DEG_TO_RAD);
        }
    }
}
