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
 *   <li>the whiskers: no motion of their own - they trail the head with inertia;</li>
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

        // the whiskers do not move by themselves: they trail the head. Each link is a damped spring that lags behind
        // the head's turning (its body's yaw and pitch plus the keyframed head), and each link follows the one before,
        // so a turn sweeps them out and they swing back and settle; a little drag sweeps them back as it swims
        whiskers(e, t, state);

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

    private static final float WHISKER_GAIN = 5.5F, WHISKER_K = 0.16F, WHISKER_DAMP = 0.78F, WHISKER_MAX = 45;

    private void whiskers(LeviathanEntity e, float t, AnimationState<LeviathanEntity> state) {
        float pt = state.getPartialTick();
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        float headYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot()) - (head == null ? 0 : head.getRotY() * Mth.RAD_TO_DEG);
        float headPitch = Mth.lerp(pt, e.xRotO, e.getXRot()) - (head == null ? 0 : head.getRotX() * Mth.RAD_TO_DEG);
        float dt = Float.isNaN(e.lastHeadYaw) ? 0 : Mth.clamp(t - e.lastWhiskerT, 0, 2);
        float wYaw = dt > 0 ? Mth.wrapDegrees(headYaw - e.lastHeadYaw) / dt : 0;
        float wPitch = dt > 0 ? (headPitch - e.lastHeadPitch) / dt : 0;
        e.lastHeadYaw = headYaw;
        e.lastHeadPitch = headPitch;
        e.lastWhiskerT = t;
        float drag = (float) e.getDeltaMovement().length() * 20;
        for (int w = 0; w < 6; w++) {
            int links = w < 4 ? WHISKER_LINKS : 4;
            float[] s = e.whisker[w];
            for (int k = 0; k < links; k++) {
                // what this link is pulled toward: against the head's turn, and after the link before it
                float pullP = -wPitch * WHISKER_GAIN + (w < 4 ? drag : -drag) * 0.6F + (k > 0 ? s[(k - 1) * 4] : 0);
                float pullY = -wYaw * WHISKER_GAIN + (k > 0 ? s[(k - 1) * 4 + 1] : 0);
                if (dt > 0) {
                    s[k * 4 + 2] = (s[k * 4 + 2] + (pullP - s[k * 4]) * WHISKER_K * dt) * (float) Math.pow(WHISKER_DAMP, dt);
                    s[k * 4 + 3] = (s[k * 4 + 3] + (pullY - s[k * 4 + 1]) * WHISKER_K * dt) * (float) Math.pow(WHISKER_DAMP, dt);
                    s[k * 4] = Mth.clamp(s[k * 4] + s[k * 4 + 2] * dt, -WHISKER_MAX, WHISKER_MAX);
                    s[k * 4 + 1] = Mth.clamp(s[k * 4 + 1] + s[k * 4 + 3] * dt, -WHISKER_MAX, WHISKER_MAX);
                }
                // each link carries only its share of the bend (the chain adds them up toward the tip)
                float share = 1F / links;
                add("whisker_" + w + "_" + k, s[k * 4] * share * Mth.DEG_TO_RAD, s[k * 4 + 1] * share * Mth.DEG_TO_RAD, 0);
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
