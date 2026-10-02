package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soulless.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The Soul Rend slash: a glowing crescent ribbon swept from above the right shoulder, through the front,
 * down to the lower left, drawn additively so it blooms over the world, then fading out.
 */
public final class SlashTrails {
    public record Trail(Vec3 origin, float yaw, long start) {}

    private static final int LIFE = 16;
    private static final int SWEEP_TICKS = 4;
    private static final float A_START = -100, A_END = 105;   // degrees along the arc
    private static final float R_IN = 1.3F, R_OUT = 3.6F;
    private static final int SEGMENTS = 40;

    /** Basis of the cut for a player facing {@code yaw}: forward, and the diagonal "upper right" direction. */
    private static Vec3[] basis(float yaw) {
        float y = yaw * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 upRight = right.add(0, 1, 0).normalize();
        return new Vec3[]{forward, upRight};
    }

    /** Point on the cut at arc angle a (degrees) and radius r from the shoulder. */
    private static Vec3 point(Trail t, Vec3[] b, float a, float r) {
        double rad = a * Mth.DEG_TO_RAD;
        Vec3 shoulder = t.origin().add(0, 1.45, 0);
        return shoulder.add(b[0].scale(Math.cos(rad) * r)).add(b[1].scale(-Math.sin(rad) * r));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientState.TRAILS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float now = ClientState.gameTime() + event.getPartialTick();
        ClientState.TRAILS.removeIf(t -> now - t.start() > LIFE);
        if (ClientState.TRAILS.isEmpty()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        for (Trail t : ClientState.TRAILS) {
            float age = now - t.start();
            float head = Mth.lerp(Mth.clamp(age / SWEEP_TICKS, 0, 1), A_START, A_END);
            float tail = Math.max(A_START, head - 150 + Math.max(0, age - SWEEP_TICKS) * 12);
            if (head - tail < 1) continue;
            float fade = 1 - Mth.clamp((age - SWEEP_TICKS) / (LIFE - SWEEP_TICKS), 0, 1);
            Vec3[] b = basis(t.yaw());
            float grow = 1 + Math.max(0, age - SWEEP_TICKS) * 0.03F;   // the crescent expands as it fades
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = Mth.lerp(i / (float) SEGMENTS, tail, head);
                float a1 = Mth.lerp((i + 1) / (float) SEGMENTS, tail, head);
                float w0 = (i / (float) SEGMENTS), w1 = ((i + 1) / (float) SEGMENTS);   // 0 at the tail, 1 at the head
                // thinner toward the tail: the inner radius creeps outward
                float in0 = Mth.lerp(w0, R_OUT - 0.4F, R_IN) * grow, in1 = Mth.lerp(w1, R_OUT - 0.4F, R_IN) * grow;
                float out = R_OUT * grow;
                Vec3 p0 = point(t, b, a0, in0), p1 = point(t, b, a0, out), p2 = point(t, b, a1, out), p3 = point(t, b, a1, in1);
                int alpha0 = (int) (255 * fade * Math.sqrt(w0)), alpha1 = (int) (255 * fade * Math.sqrt(w1));
                // drawn twice: additive blending makes the overlap glow
                quad(vc, m, p0, p1, p2, p3, alpha0, alpha1);
                quad(vc, m, p0, p1, p2, p3, alpha0, alpha1);
                // a hot white core along the outer edge
                Vec3 c0 = point(t, b, a0, out - 0.25F * grow), c3 = point(t, b, a1, out - 0.25F * grow);
                quadWhite(vc, m, c0, p1, p2, c3, (int) (alpha0 * 0.9F), (int) (alpha1 * 0.9F));
            }
        }
        ps.popPose();
        buffers.endBatch(RenderType.lightning());
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 in0, Vec3 out0, Vec3 out1, Vec3 in1, int a0, int a1) {
        // purple, double-sided (lightning culls back faces)
        v(vc, m, in0, 110, 20, 210, a0 / 2);
        v(vc, m, out0, 200, 80, 255, a0);
        v(vc, m, out1, 200, 80, 255, a1);
        v(vc, m, in1, 110, 20, 210, a1 / 2);
        v(vc, m, in1, 110, 20, 210, a1 / 2);
        v(vc, m, out1, 200, 80, 255, a1);
        v(vc, m, out0, 200, 80, 255, a0);
        v(vc, m, in0, 110, 20, 210, a0 / 2);
    }

    private static void quadWhite(VertexConsumer vc, Matrix4f m, Vec3 in0, Vec3 out0, Vec3 out1, Vec3 in1, int a0, int a1) {
        v(vc, m, in0, 255, 220, 255, 0);
        v(vc, m, out0, 255, 235, 255, a0);
        v(vc, m, out1, 255, 235, 255, a1);
        v(vc, m, in1, 255, 220, 255, 0);
        v(vc, m, in1, 255, 220, 255, 0);
        v(vc, m, out1, 255, 235, 255, a1);
        v(vc, m, out0, 255, 235, 255, a0);
        v(vc, m, in0, 255, 220, 255, 0);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Mth.clamp(a, 0, 255)).endVertex();
    }

    /** Soul fire scattered along the whole cut, sweep flashes, and embers flung outward. */
    public static void burst(ClientLevel level, Vec3 origin, float yaw) {
        Trail t = new Trail(origin, yaw, 0);
        Vec3[] b = basis(yaw);
        RandomSource rnd = level.getRandom();
        for (int i = 0; i <= 30; i++) {
            float a = Mth.lerp(i / 30F, A_START, A_END);
            float r = Mth.lerp(rnd.nextFloat(), R_IN, R_OUT);
            Vec3 p = point(t, b, a, r);
            Vec3 out = p.subtract(origin.add(0, 1.45, 0)).normalize().scale(0.12 + rnd.nextDouble() * 0.12);
            level.addParticle(ModParticles.SOUL_EMBER.get(), p.x, p.y, p.z, out.x, out.y + 0.04, out.z);
            if (i % 6 == 3) {
                Vec3 s = point(t, b, a, (R_IN + R_OUT) / 2);
                level.addParticle(ParticleTypes.SWEEP_ATTACK, s.x, s.y, s.z, 0, 0, 0);
            }
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, out.x * 2, out.y * 2, out.z * 2);
            }
        }
    }

    private SlashTrails() {}
}
