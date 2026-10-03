package com.soulless.ability;

/**
 * Tick timeline of "Soul Rend" (right click), shared by the server (hit, movement) and the client (animation):
 * <pre>
 * 0 ── WINDUP (sword hauled back over the right shoulder) ── STRIKE (diagonal cut, hit at HIT_AT) ── RECOVER
 * </pre>
 * The cooldown starts at the hit. No sword attacks from start until the end of RECOVER.
 */
public final class RendTimeline {
    /** Full charge: holding right click this long gives the full-power cut (released automatically). */
    public static final int WINDUP = 120;
    /** Heaving the sword onto the shoulder; an earlier release still waits for this. */
    public static final int HEAVE = 14;
    /** Power of the weakest (instantly released) cut. */
    public static final float MIN_POWER = 0.2F;
    public static final int STRIKE = 60;
    /** Ticks into STRIKE at which the blade connects. */
    public static final int HIT_AT = 4;
    public static final int RECOVER = 70;
    public static final int TOTAL = WINDUP + STRIKE + RECOVER;
    /**
     * With the Soulsmith's Ring there is no recovery: after the cut (6 ticks) the blade is held a moment and
     * swung straight back to guard, and the sword is free again.
     */
    public static final int RING_HOLD = 6, RING_RETURN = 12;
    public static final int RING_AFTER = 6 + RING_HOLD + RING_RETURN;

    public static int total(boolean ring) {
        return ring ? WINDUP + RING_AFTER : TOTAL;
    }
    public static final int COOLDOWN = 31 * 20;

    private RendTimeline() {}
}
