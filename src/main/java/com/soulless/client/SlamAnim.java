package com.soulless.client;

import com.soulless.ability.SlamTimeline;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Third-person body animation of "Soul Crush", sampled from {@link SlamTimeline} key poses:
 * GUARD -> CHARGE (crouch, sword dragged low) -> AIR (front flip, sword overhead) -> DIVE (sword driven down)
 * -> KNEEL (one knee down, blade buried) -> back to GUARD.
 */
public final class SlamAnim {
    /** Arm angles in radians; lean/flip/itemPitch in degrees; lower in blocks. */
    public static final class Pose {
        float mainX, offX, mainY, offY, frontLeg, backLeg, lean, lower, itemPitch;
        float flip;

        Pose(float mainX, float offX, float mainY, float offY, float frontLeg, float backLeg, float lean, float lower, float itemPitch) {
            this.mainX = mainX;
            this.offX = offX;
            this.mainY = mainY;
            this.offY = offY;
            this.frontLeg = frontLeg;
            this.backLeg = backLeg;
            this.lean = lean;
            this.lower = lower;
            this.itemPitch = itemPitch;
        }

        static Pose lerp(Pose a, Pose b, float k) {
            Pose p = new Pose(Mth.lerp(k, a.mainX, b.mainX), Mth.lerp(k, a.offX, b.offX), Mth.lerp(k, a.mainY, b.mainY),
                    Mth.lerp(k, a.offY, b.offY), Mth.lerp(k, a.frontLeg, b.frontLeg), Mth.lerp(k, a.backLeg, b.backLeg),
                    Mth.lerp(k, a.lean, b.lean), Mth.lerp(k, a.lower, b.lower), Mth.lerp(k, a.itemPitch, b.itemPitch));
            p.flip = Mth.lerp(k, a.flip, b.flip);
            return p;
        }
    }

    public static final Pose GUARD = new Pose(-0.95F, -1.1F, -0.3F, 0.55F, 0, 0, 0, 0, 0);
    private static final Pose CHARGE = new Pose(0.45F, 0.35F, -0.15F, 0.45F, -0.55F, 0.35F, 22, 0.18F, -40);
    private static final Pose AIR = new Pose(-3.0F, -3.05F, -0.1F, 0.25F, -1.2F, -1.0F, 0, 0, 10);
    private static final Pose DIVE = new Pose(-0.55F, -0.65F, -0.1F, 0.3F, 0.2F, 0.35F, 18, 0, -124);
    private static final Pose KNEEL = new Pose(-0.55F, -0.65F, -0.1F, 0.3F, -1.35F, 0.6F, 24, 0.45F, -124);

    /** The current slam pose of this entity, or null when it is not slamming. */
    @Nullable
    public static Pose sample(Entity entity) {
        float t = ClientState.slamTicks(entity);
        if (t < 0) return null;
        float ti = ClientState.impactTicks(entity);
        if (ti >= 0) {
            if (ti < SlamTimeline.KNEEL) {
                Pose p = Pose.lerp(DIVE, KNEEL, smooth(ti / 1.5F));
                p.lower += (float) Math.max(0, 0.12 * Math.sin(ti * 1.3) * Math.exp(-ti * 0.35));  // impact jolt
                return p;
            }
            float r = (ti - SlamTimeline.KNEEL) / SlamTimeline.RECOVER;
            return r >= 1 ? null : Pose.lerp(KNEEL, GUARD, smooth(r));
        }
        if (t < SlamTimeline.CHARGE) {
            return Pose.lerp(GUARD, CHARGE, smooth(t / 4F));
        }
        if (t < SlamTimeline.PLUNGE) {
            float u = (t - SlamTimeline.CHARGE) / (SlamTimeline.PLUNGE - SlamTimeline.CHARGE);
            Pose p = Pose.lerp(CHARGE, AIR, smooth(Math.min(1, u * 2.5F)));
            float tuck = Mth.sin(u * Mth.PI);
            p.frontLeg = Mth.lerp(tuck, 0, AIR.frontLeg);
            p.backLeg = Mth.lerp(tuck, 0, AIR.backLeg);
            p.flip = 360 * smooth(u);
            return p;
        }
        return Pose.lerp(AIR, DIVE, easeIn(Math.min(1, (t - SlamTimeline.PLUNGE) / 3F)));
    }

    /**
     * First-person slam as {fromKey, toKey, blend}: keys are 0 rest, 1 low (charge), 2 raised (air), 3 struck.
     * The key values themselves live in {@link FirstPersonSword}. Null when not slamming.
     */
    @Nullable
    public static float[] firstPersonKeys(Entity entity) {
        float t = ClientState.slamTicks(entity);
        if (t < 0) return null;
        float ti = ClientState.impactTicks(entity);
        if (ti >= 0) {
            if (ti < SlamTimeline.KNEEL) return new float[]{3, 3, 1};
            float r = (ti - SlamTimeline.KNEEL) / SlamTimeline.RECOVER;
            return r >= 1 ? null : new float[]{3, 0, smooth(r)};
        }
        if (t < SlamTimeline.CHARGE) return new float[]{0, 1, smooth(t / 4F)};
        if (t < SlamTimeline.PLUNGE) return new float[]{1, 2, smooth(Math.min(1, (t - SlamTimeline.CHARGE) / 5F))};
        return new float[]{2, 3, easeIn(Math.min(1, (t - SlamTimeline.PLUNGE) / 2.5F))};
    }

    static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    static float easeIn(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k;
    }

    private SlamAnim() {}
}
