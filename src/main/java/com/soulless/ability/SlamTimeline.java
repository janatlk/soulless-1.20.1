package com.soulless.ability;

/**
 * Tick timeline of "Soul Crush", shared by the server (movement, damage) and the client (animation, effects).
 * <pre>
 * 0 ── CHARGE ── leap ── (front flip, sword overhead) ── PLUNGE ── dive ── impact ── KNEEL ── RECOVER
 * </pre>
 * Impact happens when the player lands after PLUNGE (or at MAX_AIR), so it is sent explicitly to clients.
 */
public final class SlamTimeline {
    /** Crouch and gather soul fire into the blade; the player is rooted. */
    public static final int CHARGE = 7;
    /** Peak of the leap: the dive starts here. */
    public static final int PLUNGE = 14;
    /** Fail-safe: impact even if the player never lands (long drops off cliffs are allowed). */
    public static final int MAX_AIR = 400;
    /** Kneeling with the sword buried in the ground; rooted. */
    public static final int KNEEL = 14;
    /** Rising back into the guard stance. */
    public static final int RECOVER = 10;

    public static final double LEAP_UP = 0.62;   // a little higher than a jump
    public static final double LEAP_FORWARD = 0.25;
    public static final double DIVE_SPEED = -2.6;

    private SlamTimeline() {}
}
