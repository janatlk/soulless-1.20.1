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
 *   <li>the eyes: shut like sliding doors, one after another from the front, both sides together.</li>
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

        // the crown of tentacles breathes with it: the whole crown opens on the in-breath and closes on the out (the idle
        // animation's 4 s), each arm rolling a wave down to its tip, plus a sway of its own
        float breath = t * Mth.TWO_PI / 80F;
        for (int a = 0; a < ARMS; a++) {
            float side = a % 2 == 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3F : -1;
            for (int k = 0; k < ARM_LINKS; k++) {
                float f = (k + 1f) / ARM_LINKS;
                float open = Mth.sin(breath - k * 0.45F) * (4F + 9F * f);
                float sway = Mth.sin(t * 0.07F + a * 1.7F - k * 0.7F) * (2F + 6F * f);
                float lift = Mth.sin(t * 0.055F + a * 2.3F - k * 0.6F) * (2F + 5F * f);
                add("arm_" + a + "_" + k, (lift - open * up * 0.6F) * d, (sway + open * side) * d, 0);
            }
        }

        // the whiskers drift quicker and lighter; the two on top (4, 5) curl and uncurl slowly
        for (int w = 0; w < WHISKERS; w++) {
            for (int k = 0; k < WHISKER_LINKS; k++) {
                float f = (k + 1f) / WHISKER_LINKS;
                add("whisker_" + w + "_" + k, Mth.sin(t * 0.13F + w * 2.1F - k * 0.8F) * 8 * f * d,
                        Mth.sin(t * 0.11F + w * 1.4F - k * 0.7F) * 10 * f * d, 0);
            }
        }
        for (int w = 4; w <= 5; w++) {
            for (int k = 0; k < 4; k++) {
                float f = (k + 1f) / 4;
                add("whisker_" + w + "_" + k, Mth.sin(t * 0.06F + w - k * 0.5F) * 7 * f * d, Mth.sin(t * 0.08F + w * 2 - k * 0.6F) * 5 * f * d, 0);
            }
        }

        // the eyes shut like sliding doors, the two leaves meeting in the middle - one eye after another from the front,
        // both sides together, every few seconds
        long seed = e.getUUID().getLeastSignificantBits();
        int period = 80 + (int) Math.floorMod(seed, 60L);
        float since = (t + Math.floorMod(seed, 97L)) % period;
        for (int n = 0; n < 3; n++) {
            float s = since - n * 4;
            float shut = s < 0 ? 0 : s < 2.5F ? s / 2.5F : s < 4 ? 1 : s < 6.5F ? 1 - (s - 4) / 2.5F : 0;
            for (String side : new String[]{"l", "r"}) {
                for (String leaf : new String[]{"f", "b"}) {
                    CoreGeoBone door = getAnimationProcessor().getBone("door_" + side + n + "_" + leaf);
                    if (door != null) door.setScaleZ(Math.max(0.02F, shut));
                }
            }
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
