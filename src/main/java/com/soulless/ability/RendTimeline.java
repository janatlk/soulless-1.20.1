package com.soulless.ability;

/**
 * Tick timeline of "Soul Rend" (right click), shared by the server and the client animation:
 * <pre>
 * 0 ── WINDUP (sword hauled back over the right shoulder, charging) ── the cut (the beam flies) ── back to guard
 * </pre>
 * There is no recovery: the blade is held a moment after the cut and swung straight back to guard.
 */
public final class RendTimeline {
    /** Full charge: holding right click this long gives the strongest beam (released automatically). */
    public static final int WINDUP = 60;
    /** Heaving the sword onto the shoulder; an earlier release still waits for this. */
    public static final int HEAVE = 14;
    /** Charge of the weakest (instantly released) cut. */
    public static final float MIN_POWER = 0.2F;
    /** Ticks into the cut at which the beam leaves the blade. */
    public static final int HIT_AT = 4;
    /** After the cut (6 ticks): the blade held a moment, then swung back to guard. */
    public static final int CUT = 6, HOLD = 6, RETURN = 12;
    public static final int AFTER = CUT + HOLD + RETURN;
    public static final int TOTAL = WINDUP + AFTER;

    private RendTimeline() {}
}
