package com.soulless.client;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client-side mirrors of server state: who is cursed, who is slamming, how hard the camera shakes. */
public final class ClientState {
    /** entity id -> game time the Soul Curse ends */
    public static final Int2LongOpenHashMap CURSED = new Int2LongOpenHashMap();
    /** entity id -> game time a slam started */
    public static final Int2LongOpenHashMap SLAMS = new Int2LongOpenHashMap();
    public static final float SLAM_ANIM_TICKS = 28;

    public static float shake;

    public static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static boolean isCursed(Entity entity) {
        long end = CURSED.getOrDefault(entity.getId(), Long.MIN_VALUE);
        return end != Long.MIN_VALUE && end > gameTime();
    }

    /** Ticks since this entity started a slam (with partial tick), or -1 when it is not slamming. */
    public static float slamTicks(Entity entity) {
        long start = SLAMS.getOrDefault(entity.getId(), Long.MIN_VALUE);
        if (start == Long.MIN_VALUE) return -1;
        float t = gameTime() - start + Minecraft.getInstance().getFrameTime();
        return t >= 0 && t < SLAM_ANIM_TICKS ? t : -1;
    }

    public static void clear() {
        CURSED.clear();
        SLAMS.clear();
        shake = 0;
    }

    private ClientState() {}
}
