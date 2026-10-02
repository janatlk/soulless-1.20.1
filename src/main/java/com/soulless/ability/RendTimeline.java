package com.soulless.ability;

/**
 * Tick timeline of "Soul Rend" (right click), shared by the server (hit, movement) and the client (animation):
 * <pre>
 * 0 ── WINDUP (sword hauled back over the right shoulder) ── STRIKE (diagonal cut, hit at HIT_AT) ── RECOVER
 * </pre>
 * The cooldown starts at the hit. No sword attacks from start until the end of RECOVER.
 */
public final class RendTimeline {
    public static final int WINDUP = 120;
    public static final int STRIKE = 60;
    /** Ticks into STRIKE at which the blade connects. */
    public static final int HIT_AT = 4;
    public static final int RECOVER = 100;
    public static final int TOTAL = WINDUP + STRIKE + RECOVER;
    public static final int COOLDOWN = 31 * 20;

    private RendTimeline() {}
}
