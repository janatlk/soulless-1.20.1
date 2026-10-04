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
    /** In the air the lunge pushes less. */
    private static final double AIR_FACTOR = 0.4;

    /** {@code rate} > 1 under the Kitsune's blessing: the lunge carries further. */
    public static void dash(AbstractClientPlayer player, float rate) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double speed = DASH_SPEED * (rate > 1 ? BLESSED_DASH : 1) * (player.onGround() ? 1 : AIR_FACTOR);
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(forward.x * speed, v.y, forward.z * speed);   // along the ground, no hop
    }

    private KatanaThrust() {}
}
