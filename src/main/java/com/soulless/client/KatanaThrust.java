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
    /** In the air the lunge pushes less. */
    private static final double AIR_FACTOR = 0.4;

    public static void dash(AbstractClientPlayer player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double speed = DASH_SPEED * (player.onGround() ? 1 : AIR_FACTOR);
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(forward.x * speed, v.y, forward.z * speed);   // along the ground, no hop
    }

    private KatanaThrust() {}
}
