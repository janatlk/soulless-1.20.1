package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.boss.LeviathanEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Leviathan's model. On top of the keyframes (breathing, roar, bite) everything that should never stop moving is
 * procedural, from the entity's age:
 * <ul>
 *   <li>the body and tail: a travelling wave from neck to tail fin, growing toward the tail, as a swimming dragon's,
 *       and bending into its turns;</li>
 *   <li>the head tentacles: each a slow wave of its own, out of step with the others;</li>
 *   <li>the whiskers: quicker, lighter drifting;</li>
 *   <li>the eyelids: a blink every few seconds, at random, both eyes together.</li>
 * </ul>
 */
public class LeviathanModel extends DefaultedEntityGeoModel<LeviathanEntity> {
    private static final int SEGMENTS = 12, ARMS = 6, ARM_LINKS = 5, WHISKERS = 4, WHISKER_LINKS = 3;

    public LeviathanModel() {
        super(Soulless.id("leviathan"));
    }

    @Override
    public void setCustomAnimations(LeviathanEntity e, long instanceId, AnimationState<LeviathanEntity> state) {
        super.setCustomAnimations(e, instanceId, state);
        float t = e.tickCount + state.getPartialTick();
        float turn = Mth.lerp(state.getPartialTick(), e.turnO, e.turn);
        float d = Mth.DEG_TO_RAD;

        // the body: a wave running back along it, bigger toward the tail, plus the bend of a turn
        for (int i = 0; i < SEGMENTS; i++) {
            float f = (i + 1f) / SEGMENTS;
            float wave = Mth.sin(t * 0.09F - i * 0.55F) * (2.5F + 7.5F * f);
            float rise = Mth.sin(t * 0.06F - i * 0.4F + 1.3F) * (1F + 3F * f);
            add("body_" + i, rise * d, (wave - turn * 1.6F) * d, 0);
        }
        add("tail_fin", 0, Mth.sin(t * 0.09F - SEGMENTS * 0.55F) * 14 * d, 0);

        // the crown of tentacles: slow and out of step, more at the tips
        for (int a = 0; a < ARMS; a++) {
            float phase = a * 1.7F;
            for (int k = 0; k < ARM_LINKS; k++) {
                float f = (k + 1f) / ARM_LINKS;
                float sway = Mth.sin(t * 0.05F + phase - k * 0.6F) * (2F + 5F * f);
                float lift = Mth.sin(t * 0.04F + phase * 1.3F - k * 0.5F) * (1.5F + 4F * f);
                add("arm_" + a + "_" + k, lift * d, sway * d, 0);
            }
        }

        // the whiskers drift quicker and lighter
        for (int w = 0; w < WHISKERS; w++) {
            for (int k = 0; k < WHISKER_LINKS; k++) {
                float f = (k + 1f) / WHISKER_LINKS;
                add("whisker_" + w + "_" + k, Mth.sin(t * 0.13F + w * 2.1F - k * 0.8F) * 8 * f * d,
                        Mth.sin(t * 0.11F + w * 1.4F - k * 0.7F) * 10 * f * d, 0);
            }
        }

        // a blink every few seconds (the lid's rest is closed; open it is squashed up to nothing)
        long seed = e.getUUID().getLeastSignificantBits();
        int period = 70 + (int) Math.floorMod(seed, 50L);
        float since = (t + Math.floorMod(seed, 97L)) % period;
        float shut = since < 3 ? since / 3 : since < 6 ? 1 - (since - 3) / 3 : 0;
        for (String side : new String[]{"l", "r"}) {
            CoreGeoBone lid = getAnimationProcessor().getBone("lid_" + side);
            if (lid != null) lid.setScaleY(Math.max(0.02F, shut));
        }
    }

    /** Adds to the bone's rest pose (and to whatever the keyframes did this frame). */
    private void add(String name, float x, float y, float z) {
        CoreGeoBone b = getAnimationProcessor().getBone(name);
        if (b == null) return;
        var rest = b.getInitialSnapshot();
        b.setRotX(rest.getRotX() + x);
        b.setRotY(rest.getRotY() + y);
        b.setRotZ(rest.getRotZ() + z);
    }
}
