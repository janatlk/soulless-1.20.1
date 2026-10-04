package com.soulless.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The local player's katana thrust (the combo's third step), driven by {@link KatanaAnim}: a short dash as the
 * lunge explodes out. The client owns the player's movement, so the dash is applied here; the hit itself is
 * resolved by the server at the lunge (KatanaCombat).
 */
public final class KatanaThrust {
    /** Forward speed given by the lunge on the ground (blocks/tick); it carries the player about two blocks. */
    public static final double DASH_SPEED = 0.95;
    /** Under the Kitsune's blessing the lunge carries this much further. */
    private static final double BLESSED_DASH = 1.35;
    /** In water the lunge is a third as strong. */
    private static final double WATER_FACTOR = 0.33;
    /** In the air the lunge pushes less. */
    private static final double AIR_FACTOR = 0.4;

    /** {@code rate} > 1 under the Kitsune's blessing: the lunge carries further. */
    public static void dash(AbstractClientPlayer player, float rate) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double speed = DASH_SPEED * (rate > 1 ? BLESSED_DASH : 1) * (player.onGround() ? 1 : AIR_FACTOR)
                * (player.isInWater() ? WATER_FACTOR : 1);
        // stop short of the first creature in the way: the point reaches it, the body stays out of its reach
        double room = roomAhead(player, forward);
        speed = Math.min(speed, Math.max(0, room / SLIDE));
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(forward.x * speed, v.y, forward.z * speed);   // along the ground, no hop
    }

    /** A dash of speed v slides about v * SLIDE blocks on the ground (friction 0.546 per tick). */
    private static final double SLIDE = 2.2;
    /** The lunge ends this far (blade's reach) from the nearest creature ahead. */
    private static final double STOP_SHORT = 1.6;

    /** How far the player can lunge before coming within STOP_SHORT of something alive in front. */
    private static double roomAhead(AbstractClientPlayer player, Vec3 forward) {
        double max = DASH_SPEED * BLESSED_DASH * SLIDE;
        double room = max;
        for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().expandTowards(forward.scale(max + STOP_SHORT)).inflate(1.0),
                x -> x != player && x.isAlive() && !x.isSpectator())) {
            Vec3 to = e.position().subtract(player.position());
            double along = to.x * forward.x + to.z * forward.z;
            double side = Math.abs(to.x * forward.z - to.z * forward.x);
            if (along <= 0 || side > 1.0 + e.getBbWidth() / 2) continue;
            room = Math.min(room, along - e.getBbWidth() / 2 - STOP_SHORT);
        }
        return room;
    }

    private KatanaThrust() {}
}
