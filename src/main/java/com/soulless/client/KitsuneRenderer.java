package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.npc.KitsuneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Kitsune (tools/gen_kitsune.py): her markings and eyes glow; awake, her head turns to whoever she talks to. */
public class KitsuneRenderer extends GeoEntityRenderer<KitsuneEntity> {
    public KitsuneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 1.3F;
    }

    private static final class Model extends DefaultedEntityGeoModel<KitsuneEntity> {
        Model() {
            super(Soulless.id("kitsune"));
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
