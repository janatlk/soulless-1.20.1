package com.soulless.client;

import com.soulless.ability.SlamTimeline;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client-side mirrors of server state: who is cursed, who is slamming, how hard the camera shakes. */
public final class ClientState {
    /** entity id -> game time the Soul Curse ends */
    public static final Int2LongOpenHashMap CURSED = new Int2LongOpenHashMap();
    /** entity id -> game time a slam started */
    public static final Int2LongOpenHashMap SLAMS = new Int2LongOpenHashMap();
    /** entity id -> game time that slam hit the ground */
    public static final Int2LongOpenHashMap IMPACTS = new Int2LongOpenHashMap();

    public static float shake;
    /** Game time the local player's current slash started, or MIN_VALUE. */
    public static long slashStart = Long.MIN_VALUE;
    /** The player currently being rendered (between RenderPlayerEvent.Pre and Post), for the item renderer. */
    public static Entity renderingPlayer;

    public static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static boolean isCursed(Entity entity) {
        long end = CURSED.getOrDefault(entity.getId(), Long.MIN_VALUE);
        return end != Long.MIN_VALUE && end > gameTime();
    }

    /** Ticks (with partial tick) since this entity started a slam, or -1 when it is not slamming. */
    public static float slamTicks(Entity entity) {
        long start = SLAMS.getOrDefault(entity.getId(), Long.MIN_VALUE);
        if (start == Long.MIN_VALUE) return -1;
        float t = gameTime() - start + Minecraft.getInstance().getFrameTime();
        if (t < 0) return -1;
        if (!IMPACTS.containsKey(entity.getId()) && t > SlamTimeline.MAX_AIR + 10) return -1;
        return t;
    }

    /** Ticks (with partial tick) since this entity's slam hit the ground, or -1. */
    public static float impactTicks(Entity entity) {
        long at = IMPACTS.getOrDefault(entity.getId(), Long.MIN_VALUE);
        if (at == Long.MIN_VALUE) return -1;
        float t = gameTime() - at + Minecraft.getInstance().getFrameTime();
        return t >= 0 ? t : -1;
    }

    /** Ticks (with partial tick) into the local player's slash, or -1. */
    public static float slashTicks(Entity player) {
        if (slashStart == Long.MIN_VALUE) return -1;
        float t = gameTime() - slashStart + Minecraft.getInstance().getFrameTime();
        return t >= 0 ? t : -1;
    }

    public static void endSlam(int entityId) {
        SLAMS.remove(entityId);
        IMPACTS.remove(entityId);
    }

    public static void clear() {
        CURSED.clear();
        SLAMS.clear();
        IMPACTS.clear();
        shake = 0;
        renderingPlayer = null;
    }

    private ClientState() {}
}
