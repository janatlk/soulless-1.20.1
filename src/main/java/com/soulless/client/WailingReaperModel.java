package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.boss.WailingReaperEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** The Reaper's model; on top of the animations it swings the dangling lower spine (see the entity). */
public class WailingReaperModel extends DefaultedEntityGeoModel<WailingReaperEntity> {
    public WailingReaperModel() {
        super(Soulless.id("wailing_reaper"));
    }

    @Override
    public void setCustomAnimations(WailingReaperEntity e, long instanceId, AnimationState<WailingReaperEntity> state) {
        super.setCustomAnimations(e, instanceId, state);
        float pt = state.getPartialTick();
        for (int i = 0; i < WailingReaperEntity.SPINE_LINKS; i++) {
            CoreGeoBone bone = getAnimationProcessor().getBone("spine_tail" + i);
            if (bone == null) continue;
            // the same signs as the keyframes: +x swings back (bone x is mirrored), +z toward the model's right
            bone.setRotX(-Mth.lerp(pt, e.spineXo[i], e.spineX[i]) * Mth.DEG_TO_RAD);
            bone.setRotZ(Mth.lerp(pt, e.spineZo[i], e.spineZ[i]) * Mth.DEG_TO_RAD);
        }
    }
}
