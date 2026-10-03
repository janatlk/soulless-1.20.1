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
 * seconds) -> CUT (diagonal cut down to the left, torso unwinding, follow-through held) -> REST (the spent
 * blade laid back on the shoulder; the player walks normally) -> GUARD.
 */
public final class RendAnim {
    private static final SlamAnim.Pose SHOULDER = new SlamAnim.Pose(-2.75F, -2.5F, -0.55F, 0.95F, -0.35F, 0.45F, -6, 0.05F, 25)
            .twist(0.45F).legs(0.6F);
    private static final SlamAnim.Pose CUT = new SlamAnim.Pose(-0.55F, -0.7F, 0.95F, 1.2F, -0.55F, 0.55F, 16, 0.15F, -35)
            .twist(-0.6F);
    /** Recovering: the blade rests back over the right shoulder, the off hand low on the grip. */
    private static final SlamAnim.Pose REST = new SlamAnim.Pose(-2.0F, -1.45F, -0.25F, 0.6F, 0, 0, 0, 0, 20)
            .legs(0);

    /** Ticks of the cut itself, from the shoulder to the end of the swing. */
    public static final int CUT_TICKS = 6;
    private static final int HEAVE_TICKS = RendTimeline.HEAVE;
    /** Recovery: lift the blade onto the shoulder, carry it there, then bring it back to guard. */
    private static final int TO_REST = 14, BACK_TO_GUARD = 30;

    @Nullable
    public static SlamAnim.Pose sample(Entity entity) {
        float t = ClientState.rendTicks(entity);
        if (t < 0) return null;
        if (t < RendTimeline.WINDUP) {
            if (t < HEAVE_TICKS) return SlamAnim.Pose.lerp(SlamAnim.GUARD, SHOULDER, smooth(t / HEAVE_TICKS));
            return charged(t);
        }
        float s = t - RendTimeline.WINDUP;
        if (com.soulless.item.SoulRing.worn(entity)) {
            // the ring: no recovery - after the cut the blade swings straight back to guard
            if (s < CUT_TICKS) return SlamAnim.Pose.lerp(charged(RendTimeline.WINDUP), CUT, cutCurve(s / CUT_TICKS));
            SlamAnim.Pose cut = SlamAnim.Pose.lerp(CUT, CUT, 0);
            float back = (s - CUT_TICKS - RendTimeline.RING_HOLD) / RendTimeline.RING_RETURN;
            if (back <= 0) return cut;
            SlamAnim.Pose guard = SlamAnim.Pose.lerp(SlamAnim.GUARD, SlamAnim.GUARD, 0).legs(0);
            return back < 1 ? SlamAnim.Pose.lerp(cut, guard, smooth(back)) : null;
        }
        if (s < RendTimeline.STRIKE) {
            if (s < CUT_TICKS) return SlamAnim.Pose.lerp(charged(RendTimeline.WINDUP), CUT, cutCurve(s / CUT_TICKS));
            // the follow-through is held, the stance slowly relaxing
            SlamAnim.Pose held = SlamAnim.Pose.lerp(CUT, REST, 0);
            held.legs = 1 - smooth((s - CUT_TICKS) / (RendTimeline.STRIKE - CUT_TICKS)) * 0.6F;
            return held;
        }
        float r = t - RendTimeline.WINDUP - RendTimeline.STRIKE;
        SlamAnim.Pose cut = SlamAnim.Pose.lerp(CUT, CUT, 0).legs(0.4F);
        if (r < TO_REST) return SlamAnim.Pose.lerp(cut, REST, smooth(r / TO_REST));
        if (r < RendTimeline.RECOVER - BACK_TO_GUARD) {
            SlamAnim.Pose p = SlamAnim.Pose.lerp(REST, REST, 0);
            p.mainX += Mth.sin(r * 0.12F) * 0.04F;   // the heavy blade bobs on the shoulder
            return p;
        }
        if (r < RendTimeline.RECOVER) {
            SlamAnim.Pose guard = SlamAnim.Pose.lerp(SlamAnim.GUARD, SlamAnim.GUARD, 0).legs(0);
            return SlamAnim.Pose.lerp(REST, guard, smooth((r - RendTimeline.RECOVER + BACK_TO_GUARD) / BACK_TO_GUARD));
        }
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
     * First person as {fromKey, toKey, blend}: keys are 0 rest, 4 shoulder, 5 cut, 6 shoulder rest
     * (values in {@link FirstPersonSword}). Null when not rending.
     */
    @Nullable
    public static float[] firstPersonKeys(Entity entity) {
        float t = ClientState.rendTicks(entity);
        if (t < 0) return null;
        if (t < RendTimeline.WINDUP) return new float[]{0, 4, smooth(t / HEAVE_TICKS)};
        float s = t - RendTimeline.WINDUP;
        if (com.soulless.item.SoulRing.worn(entity)) {
            if (s < CUT_TICKS) return new float[]{4, 5, cutCurve(s / CUT_TICKS)};
            float back = (s - CUT_TICKS - RendTimeline.RING_HOLD) / RendTimeline.RING_RETURN;
            if (back <= 0) return new float[]{5, 5, 1};
            return back < 1 ? new float[]{5, 0, smooth(back)} : null;
        }
        if (s < RendTimeline.STRIKE) {
            if (s < CUT_TICKS) return new float[]{4, 5, cutCurve(s / CUT_TICKS)};
            return new float[]{5, 5, 1};
        }
        float r = t - RendTimeline.WINDUP - RendTimeline.STRIKE;
        if (r < TO_REST) return new float[]{5, 6, smooth(r / TO_REST)};
        if (r < RendTimeline.RECOVER - BACK_TO_GUARD) return new float[]{6, 6, 1};
        if (r < RendTimeline.RECOVER) return new float[]{6, 0, smooth((r - RendTimeline.RECOVER + BACK_TO_GUARD) / BACK_TO_GUARD)};
        return null;
    }

    private RendAnim() {}
}
