package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soulless.ability.RendManager;
import com.soulless.ability.SlamManager;
import com.soulless.item.SoulForgedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * {@code /soulless zones}: while the sword is held, outlines where each ability lands, using the same
 * geometry as the server - Soul Crush as a purple circle on the ground, Soul Rend as a gold wedge in front -
 * and boxes every creature that would be hit, in the matching colour.
 */
public final class ZoneRenderer {
    private static final float[] SLAM = {0.75F, 0.3F, 1.0F};
    private static final float[] REND = {1.0F, 0.82F, 0.25F};

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !ClientState.showZones) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(player.getMainHandItem().getItem() instanceof SoulForgedItem)) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();

        // Soul Crush: blast circle (+ a cross where the blade lands)
        Vec3 c = SlamManager.impactCenter(player).add(0, 0.05, 0);
        circle(vc, m, n, c, SlamManager.RADIUS, -180, 180, SLAM);
        line(vc, m, n, c.add(-0.4, 0, 0), c.add(0.4, 0, 0), SLAM);
        line(vc, m, n, c.add(0, 0, -0.4), c.add(0, 0, 0.4), SLAM);

        // Soul Rend: the beam's path along the line of sight, up to the first block
        Vec3 eye = player.getEyePosition(event.getPartialTick());
        Vec3 sight = player.getViewVector(event.getPartialTick());
        Vec3 far = eye.add(sight.scale(com.soulless.ability.SoulSlashEntity.RANGE));
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 end = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation() : far;
        line(vc, m, n, eye.add(0, -0.3, 0), end, REND);

        // creatures that would be hit right now
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(9),
                x -> x != player && x.isAlive())) {
            if (SlamManager.inBlast(c, e)) {
                LevelRenderer.renderLineBox(ps, vc, e.getBoundingBox().inflate(0.05), SLAM[0], SLAM[1], SLAM[2], 1);
            }
        }
        ps.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Horizontal unit vector a player with this yaw faces. */
    private static Vec3 dir(float yawDeg) {
        return Vec3.directionFromRotation(0, yawDeg);
    }

    private static void circle(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 center, double r, float fromYaw, float toYaw, float[] col) {
        int steps = Math.max(8, (int) ((toYaw - fromYaw) / 7.5F));
        Vec3 prev = center.add(dir(fromYaw).scale(r));
        for (int i = 1; i <= steps; i++) {
            Vec3 next = center.add(dir(Mth.lerp(i / (float) steps, fromYaw, toYaw)).scale(r));
            line(vc, m, n, prev, next, col);
            prev = next;
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float[] col) {
        Vec3 d = b.subtract(a).normalize();
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(col[0], col[1], col[2], 1).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(col[0], col[1], col[2], 1).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
    }

    private ZoneRenderer() {}
}
