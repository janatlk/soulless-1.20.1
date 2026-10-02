package com.soulless.client;

import com.soulless.ability.RendTimeline;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import static com.soulless.client.SlamAnim.easeIn;
import static com.soulless.client.SlamAnim.smooth;

/**
 * Third-person body animation of "Soul Rend":
 * GUARD -> SHOULDER (sword heaved back over the right shoulder, torso coiled, slowly drawing tighter for six
 * seconds) -> CUT (diagonal cut down to the left, torso unwinding, follow-through held) -> LOW (the heavy
 * blade drags low) -> GUARD.
 */
public final class RendAnim {
    private static final SlamAnim.Pose SHOULDER = new SlamAnim.Pose(-2.75F, -2.5F, -0.55F, 0.95F, -0.35F, 0.45F, -6, 0.05F, 25).twist(0.45F);
    private static final SlamAnim.Pose CUT = new SlamAnim.Pose(-0.55F, -0.7F, 0.95F, 1.2F, -0.55F, 0.55F, 16, 0.15F, -35).twist(-0.6F);
    private static final SlamAnim.Pose LOW = new SlamAnim.Pose(-0.15F, -0.3F, 0.2F, 0.5F, 0, 0, 6, 0, -75).twist(-0.15F);

    /** Ticks of the cut itself, from the shoulder to the end of the swing. */
    public static final int CUT_TICKS = 6;
    private static final int HEAVE_TICKS = 14;

    @Nullable
    public static SlamAnim.Pose sample(Entity entity) {
        float t = ClientState.rendTicks(entity);
        if (t < 0) return null;
        if (t < RendTimeline.WINDUP) {
            if (t < HEAVE_TICKS) return SlamAnim.Pose.lerp(SlamAnim.GUARD, SHOULDER, smooth(t / HEAVE_TICKS));
            return charged(t);
        }
        float s = t - RendTimeline.WINDUP;
        if (s < RendTimeline.STRIKE) {
            if (s < CUT_TICKS) return SlamAnim.Pose.lerp(charged(RendTimeline.WINDUP), CUT, cutCurve(s / CUT_TICKS));
            return SlamAnim.Pose.lerp(CUT, LOW, smooth((s - CUT_TICKS) / (RendTimeline.STRIKE - CUT_TICKS)) * 0.25F);
        }
        float r = t - RendTimeline.WINDUP - RendTimeline.STRIKE;
        SlamAnim.Pose held = SlamAnim.Pose.lerp(CUT, LOW, 0.25F);
        if (r < 40) return SlamAnim.Pose.lerp(held, LOW, smooth(r / 40));
        if (r < RendTimeline.RECOVER) return SlamAnim.Pose.lerp(LOW, SlamAnim.GUARD, smooth((r - 40) / (RendTimeline.RECOVER - 40)));
        return null;
    }

    /** The shoulder pose drawing ever tighter while the blade charges, with a faint tremble of effort. */
    private static SlamAnim.Pose charged(float t) {
        float k = Mth.clamp((t - HEAVE_TICKS) / (RendTimeline.WINDUP - HEAVE_TICKS), 0, 1);
        SlamAnim.Pose p = SlamAnim.Pose.lerp(SHOULDER, SHOULDER, 0);
        float tremble = Mth.sin(t * 1.9F) * 0.025F * k;
        p.mainX -= 0.15F * k + tremble;
        p.offX -= 0.12F * k + tremble;
        p.bodyYaw += 0.15F * k;
        p.lower += 0.08F * k;
        p.lean -= 3 * k;
        return p;
    }

    /** Slow start, violent middle, slight settle: the weight of the blade. */
    static float cutCurve(float k) {
        k = Mth.clamp(k, 0, 1);
        return k < 0.3F ? easeIn(k / 0.3F) * 0.25F : 0.25F + 0.75F * smooth((k - 0.3F) / 0.7F);
    }

    /**
     * First person as {fromKey, toKey, blend}: keys are 0 rest, 4 shoulder, 5 cut, 6 low (values in
     * {@link FirstPersonSword}). Null when not rending.
     */
    @Nullable
    public static float[] firstPersonKeys(Entity entity) {
        float t = ClientState.rendTicks(entity);
        if (t < 0) return null;
        if (t < RendTimeline.WINDUP) return new float[]{0, 4, smooth(t / HEAVE_TICKS)};
        float s = t - RendTimeline.WINDUP;
        if (s < RendTimeline.STRIKE) {
            if (s < CUT_TICKS) return new float[]{4, 5, cutCurve(s / CUT_TICKS)};
            return new float[]{5, 6, smooth((s - CUT_TICKS) / (RendTimeline.STRIKE - CUT_TICKS)) * 0.25F};
        }
        float r = t - RendTimeline.WINDUP - RendTimeline.STRIKE;
        if (r < 40) return new float[]{5, 6, 0.25F + 0.75F * smooth(r / 40)};
        if (r < RendTimeline.RECOVER) return new float[]{6, 0, smooth((r - 40) / (RendTimeline.RECOVER - 40))};
        return null;
    }

    private RendAnim() {}
}
