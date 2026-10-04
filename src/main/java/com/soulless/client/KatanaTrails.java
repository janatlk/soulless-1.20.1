package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soulless.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The katana's effects, drawn on every client:
 * <ul>
 *   <li>the smear of kesa and gyaku - a red arc traced by the blade's tip through the active part of the cut
 *       (upper right down to lower left, gyaku mirrored), a deep red body with a hot bright edge;</li>
 *   <li>the thrust - a short pale streak straight ahead;</li>
 *   <li>the lunge's dash - pale wind lines stretched from where the lunge began to the player, thinning and
 *       fading behind them, with a puff of dust at the start;</li>
 *   <li>soft sounds: the whoosh as the blade sweeps, and the gust of the dash (the hit's "shing" comes from the
 *       server, item.KatanaCombat).</li>
 * </ul>
 * Everything rides with its player, so a dashing thrust carries its streak.
 */
public final class KatanaTrails {
    /** kind: 0 kesa, 1 gyaku, 2 tsuki; {@code start}: game time (ticks) the blade begins the traced part. */
    private record Trail(int entityId, int kind, float start, float yaw) {}

    /** The dash: where the lunge began. */
    private record Gust(int entityId, Vec3 from, float start) {}

    private static final List<Trail> TRAILS = new ArrayList<>();
    private static final List<Gust> GUSTS = new ArrayList<>();
    /** Ticks into each cut when the traced part starts, and how long the blade takes over it (KatanaPoses timelines). */
    private static final int[] DELAY = {3, 4, 8};
    private static final float[] SWEEP = {5, 5, 1.5F};
    private static final float LIFE_AFTER = 6, GUST_LIFE = 10;
    private static final float A_START = -75, A_END = 105;
    /** The arc's outer edge (the tip's path) and the inner edge of its widest part. */
    private static final float R_OUT = 2.9F, R_BODY = 2.35F;
    private static final int SEGMENTS = 36;
    /** The katana's sounds are soft. */
    private static final float SWING_VOLUME = 0.32F, DASH_VOLUME = 0.38F;

    public static int sweepDelay(int kind) {
        return DELAY[kind];
    }

    /** A cut started on this player's model now. */
    public static void add(Entity player, int kind) {
        TRAILS.add(new Trail(player.getId(), kind, ClientState.gameTime() + DELAY[kind], player.getYRot()));
    }

    /** The blade starts its sweep: a quiet whoosh (a whistle for the thrust), slightly varied. */
    public static void whoosh(Entity player, int kind) {
        RandomSource rnd = player.level().random;
        player.level().playLocalSound(player.getX(), player.getEyeY() - 0.4, player.getZ(),
                kind == 2 ? ModSounds.KATANA_THRUST.get() : ModSounds.KATANA_SWING.get(), SoundSource.PLAYERS,
                SWING_VOLUME, (kind == 1 ? 1.06F : 0.97F) + rnd.nextFloat() * 0.08F, false);
    }

    /** The lunge begins: wind lines from here, a puff of dust at the feet, a gust of air. */
    public static void dash(Entity player) {
        GUSTS.add(new Gust(player.getId(), player.position(), ClientState.gameTime()));
        player.level().playLocalSound(player.getX(), player.getY() + 0.5, player.getZ(), ModSounds.KATANA_DASH.get(),
                SoundSource.PLAYERS, DASH_VOLUME, 0.95F + player.level().random.nextFloat() * 0.1F, false);
        float y = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(y), 0, -Mth.cos(y));
        RandomSource rnd = player.level().random;
        for (int i = 0; i < 10; i++) {
            Vec3 v = back.scale(0.05 + rnd.nextDouble() * 0.08).add((rnd.nextDouble() - 0.5) * 0.08, rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.08);
            player.level().addParticle(ParticleTypes.CLOUD, player.getX() + (rnd.nextDouble() - 0.5) * 0.6, player.getY() + 0.05,
                    player.getZ() + (rnd.nextDouble() - 0.5) * 0.6, v.x, v.y, v.z);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAILS.isEmpty() && GUSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAILS.clear();
            GUSTS.clear();
            return;
        }
        float partial = event.getPartialTick();
        float now = ClientState.gameTime() + partial;
        TRAILS.removeIf(t -> now - t.start > SWEEP[t.kind] + LIFE_AFTER || mc.level.getEntity(t.entityId) == null);
        GUSTS.removeIf(g -> now - g.start > GUST_LIFE || mc.level.getEntity(g.entityId) == null);
        if (TRAILS.isEmpty() && GUSTS.isEmpty()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        // the red body blends normally (additive would turn red into pink over the sky); the bright edge glows
        VertexConsumer body = buffers.getBuffer(RenderType.debugQuads());
        for (Trail t : TRAILS) {
            if (t.kind != 2) draw(t, mc, now, partial, body, m, false);
        }
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (Trail t : TRAILS) draw(t, mc, now, partial, glow, m, true);
        for (Gust g : GUSTS) gust(g, mc, now, partial, glow, m);
        ps.popPose();
        buffers.endBatch(RenderType.lightning());
    }

    private static void draw(Trail t, Minecraft mc, float now, float partial, VertexConsumer vc, Matrix4f m, boolean glow) {
        float age = now - t.start;
        if (age < 0) return;
        Vec3 origin = mc.level.getEntity(t.entityId).getPosition(partial);
        float sweep = SWEEP[t.kind];
        float k = Mth.clamp(age / sweep, 0, 1);
        k = 1 - (1 - k) * (1 - k);                                     // the tip slows into the follow-through
        float fade = 1 - Mth.clamp((age - sweep) / LIFE_AFTER, 0, 1);
        if (t.kind == 2) {
            streak(vc, m, origin, t.yaw, k, fade);
        } else {
            arc(vc, m, origin, t.yaw, t.kind == 1, k, fade, glow);
        }
    }

    /** The cut's arc in its diagonal plane: forward, and the "upper right" (or upper left for gyaku) direction. */
    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 origin, float yaw, boolean mirror, float k, float fade, boolean glow) {
        float y = yaw * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 right = new Vec3(-forward.z, 0, forward.x).scale(mirror ? -1 : 1);
        Vec3 up = right.scale(0.9).add(0, 1, 0).normalize();
        Vec3 shoulder = origin.add(0, 1.35, 0);
        float head = Mth.lerp(k, A_START, A_END);
        float tail = Math.max(A_START, head - 110);
        if (head - tail < 1) return;
        for (int i = 0; i < SEGMENTS; i++) {
            float w0 = i / (float) SEGMENTS, w1 = (i + 1) / (float) SEGMENTS;   // 0 at the tail, 1 at the head
            float a0 = Mth.lerp(w0, tail, head), a1 = Mth.lerp(w1, tail, head);
            int al0 = (int) (fade * w0 * w0 * 255), al1 = (int) (fade * w1 * w1 * 255);
            if (glow) {
                // the hot edge along the tip's path
                Vec3 p0 = at(shoulder, forward, up, a0, R_OUT - 0.08F), p1 = at(shoulder, forward, up, a0, R_OUT);
                Vec3 p2 = at(shoulder, forward, up, a1, R_OUT), p3 = at(shoulder, forward, up, a1, R_OUT - 0.08F);
                quad(vc, m, p0, p1, p2, p3, 255, 120, 105, al0 * 3 / 4, al1 * 3 / 4, 1);
            } else {
                // a slim crimson ribbon along the tip's path, a little wider toward the head
                float in0 = Mth.lerp(w0, R_OUT - 0.1F, R_BODY), in1 = Mth.lerp(w1, R_OUT - 0.1F, R_BODY);
                Vec3 p0 = at(shoulder, forward, up, a0, in0), p1 = at(shoulder, forward, up, a0, R_OUT);
                Vec3 p2 = at(shoulder, forward, up, a1, R_OUT), p3 = at(shoulder, forward, up, a1, in1);
                quad(vc, m, p0, p1, p2, p3, 200, 16, 28, al0 * 3 / 4, al1 * 3 / 4, 4);
            }
        }
    }

    private static Vec3 at(Vec3 shoulder, Vec3 forward, Vec3 up, float a, float r) {
        double rad = a * Mth.DEG_TO_RAD;
        return shoulder.add(forward.scale(Math.cos(rad) * r)).add(up.scale(-Math.sin(rad) * r));
    }

    /** The thrust: a straight pale streak ahead at chest height, its tip running out to the reach. */
    private static void streak(VertexConsumer vc, Matrix4f m, Vec3 origin, float yaw, float k, float fade) {
        float y = yaw * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 chest = origin.add(0, 1.2, 0);
        Vec3 a = chest.add(forward.scale(0.8)), b = chest.add(forward.scale(0.8 + 3.0 * k));
        int al = (int) (170 * fade);
        for (Vec3 off : new Vec3[]{side.scale(0.09), new Vec3(0, 0.09, 0)}) {
            quad(vc, m, a.subtract(off), a.add(off), b.add(off.scale(0.2)), b.subtract(off.scale(0.2)), 225, 222, 255, al / 3, al, 3);
        }
    }

    /** The dash: wind lines from the lunge's start to just behind the player, their back ends catching up. */
    private static void gust(Gust g, Minecraft mc, float now, float partial, VertexConsumer vc, Matrix4f m) {
        float age = now - g.start;
        Vec3 to = mc.level.getEntity(g.entityId).getPosition(partial);
        Vec3 d = to.subtract(g.from);
        double len = d.horizontalDistance();
        if (len < 0.3) return;
        Vec3 dir = new Vec3(d.x, 0, d.z).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float fade = 1 - Mth.clamp(age / GUST_LIFE, 0, 1);
        float tail = Mth.clamp(age / GUST_LIFE, 0, 1) * 0.85F;          // the line's back end catches up as it fades
        double[][] lines = {{0.25, 0.3}, {0.95, -0.28}, {1.55, 0.18}, {0.6, -0.05}, {1.25, 0.4}};
        for (int i = 0; i < lines.length; i++) {
            double h = lines[i][0], s = lines[i][1];
            double lag = 0.35 + 0.12 * i;                               // each line stops a little behind the player
            Vec3 a = g.from.add(d.scale(tail)).add(side.scale(s)).add(0, h, 0);
            Vec3 b = to.subtract(dir.scale(lag)).add(side.scale(s)).add(0, h, 0);
            if (b.subtract(a).dot(dir) <= 0.05) continue;
            Vec3 w = new Vec3(0, 0.05, 0);
            int al = (int) (230 * fade);
            quad(vc, m, a.subtract(w), a.add(w), b.add(w.scale(1.6)), b.subtract(w.scale(1.6)), 235, 240, 255, 0, al, 3);
        }
    }

    /** A double-sided quad; the alpha runs from a0 (first edge) to a1 (second); inner edges dimmed by {@code innerDim}. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 in0, Vec3 out0, Vec3 out1, Vec3 in1, int r, int g, int b,
                             int a0, int a1, int innerDim) {
        int i0 = innerDim > 0 ? a0 / innerDim : a0, i1 = innerDim > 0 ? a1 / innerDim : a1;
        v(vc, m, in0, r, g, b, i0);
        v(vc, m, out0, r, g, b, a0);
        v(vc, m, out1, r, g, b, a1);
        v(vc, m, in1, r, g, b, i1);
        v(vc, m, in1, r, g, b, i1);
        v(vc, m, out1, r, g, b, a1);
        v(vc, m, out0, r, g, b, a0);
        v(vc, m, in0, r, g, b, i0);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Mth.clamp(a, 0, 255)).endVertex();
    }

    private KatanaTrails() {}
}
