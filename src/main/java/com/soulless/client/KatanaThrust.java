package com.soulless.client;

import com.soulless.item.KatanaCombat;
import com.soulless.network.KatanaCutPacket;
import com.soulless.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * The local player's katana thrust (the combo's third step), driven by {@link KatanaAnim}: a short dash at the
 * start of the lunge, then the hit at the lunge: the server strikes the first thing along the blade's line
 * (KatanaCombat). The client owns the player's movement, so the dash is applied here.
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

    public static void strike(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || player != mc.player) return;
        // the server finds what the blade runs into along the thrust's line
        ModNetwork.sendToServer(new KatanaCutPacket(KatanaCombat.THRUST_STEP));
        mc.player.resetAttackStrengthTicker();
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private KatanaThrust() {}
}
