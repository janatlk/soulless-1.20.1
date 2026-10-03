package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.soulless.Soulless;
import com.soulless.ability.SoulSlashEntity;
import com.soulless.boss.SoulBoltEntity;
import com.soulless.boss.WailingReaperEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderers for the Wailing Reaper and its projectiles, and the Soul Rend slash wave. */
public final class BossRenderers {

    public static class Reaper extends GeoEntityRenderer<WailingReaperEntity> {
        public Reaper(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(Soulless.id("wailing_reaper")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            shadowRadius = 1.4F;
        }

        @Override
        public RenderType getRenderType(WailingReaperEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
            return RenderType.entityTranslucent(texture);   // see-through rib gaps and flame edges
        }

        @Override
        protected float getDeathMaxRotation(WailingReaperEntity animatable) {
            return 0;   // the death animation handles the fall
        }
    }

    public static class AbyssHead extends WitherSkullRenderer {
        private static final ResourceLocation TEX = Soulless.id("textures/entity/abyss_head.png");

        public AbyssHead(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(WitherSkull entity) {
            return TEX;
        }
    }

    /** A glowing camera-facing orb. */
    public static class Bolt extends EntityRenderer<SoulBoltEntity> {
        private static final ResourceLocation TEX = Soulless.id("textures/entity/soul_bolt.png");

        public Bolt(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(SoulBoltEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            ps.pushPose();
            ps.translate(0, 0.3, 0);
            ps.mulPose(entityRenderDispatcher.cameraOrientation());
            float s = 0.9F + Mth.sin((e.tickCount + pt) * 0.8F) * 0.1F;
            ps.scale(s, s, s);
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEX));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            quad(vc, m, n, -0.5F, -0.5F, 0.5F, 0.5F);
            ps.popPose();
        }

        private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float x1, float y1) {
            v(vc, m, n, x0, y0, 0, 1);
            v(vc, m, n, x1, y0, 1, 1);
            v(vc, m, n, x1, y1, 1, 0);
            v(vc, m, n, x0, y1, 0, 0);
        }

        private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv) {
            vc.vertex(m, x, y, 0).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        }

        @Override
        public ResourceLocation getTextureLocation(SoulBoltEntity e) {
            return TEX;
        }
    }

    /**
     * Ice shard of the ultimate: a frost rune on the ground (slowly turning, its inner fill growing to the rim
     * at the moment of impact, flickering in the last half second), then a faceted ice spike plunging from the
     * sky, stuck in the ground for a moment and melting away.
     */
    public static class IceShard extends EntityRenderer<com.soulless.boss.IceShardEntity> {
        private static final ResourceLocation RUNE = Soulless.id("textures/entity/ice_rune.png");
        private static final ResourceLocation FILL = Soulless.id("textures/entity/ice_fill.png");
        private static final ResourceLocation SHARD = Soulless.id("textures/entity/ice_shard.png");
        private static final int SIDES = 6;
        private static final float BASE_Y = 2.3F, TOP_Y = 3.4F, WIDTH = 0.45F;

        public IceShard(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(com.soulless.boss.IceShardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float age = e.tickCount + pt;
            int impact = e.impactTick();
            // --- the warning rune
            float after = age - impact;
            float runeAlpha = after < 0 ? Math.min(1, age / 6F) : Mth.clamp(1 - after / 6F, 0, 1);
            if (after < 0 && impact - age < 10) runeAlpha *= 0.75F + 0.25F * Mth.sin(age * 2.2F);   // urgent flicker
            if (runeAlpha > 0) {
                float grow = 1 - (float) Math.pow(1 - Math.min(1, age / 8F), 3);   // ease-out pop in
                float r = (float) com.soulless.boss.IceShardEntity.RADIUS * (0.4F + 0.6F * grow);
                ps.pushPose();
                ps.translate(0, 0.03, 0);
                ps.mulPose(Axis.YP.rotationDegrees(age * 1.5F));
                ground(buffers.getBuffer(RenderType.entityTranslucentEmissive(RUNE)), ps, r, runeAlpha);
                float fill = e.warnProgress(pt);
                if (after < 0) {
                    ps.translate(0, 0.01, 0);
                    ground(buffers.getBuffer(RenderType.entityTranslucentEmissive(FILL)), ps, r * fill, runeAlpha * (0.35F + 0.5F * fill));
                }
                ps.popPose();
            }
            // --- the spike
            float tip = e.tipHeight(pt);
            if (tip >= 0) {
                float melt = tip > 0 ? 1 : e.meltScale(pt);
                if (melt > 0) {
                    ps.pushPose();
                    ps.translate(0, tip > 0 ? tip : -0.9F, 0);
                    ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 37 % 360));
                    ps.mulPose(Axis.XP.rotationDegrees(6 + e.getId() % 7));
                    ps.scale(melt, melt, melt);
                    spike(buffers.getBuffer(RenderType.entityTranslucentEmissive(SHARD)), ps);
                    ps.popPose();
                }
            }
        }

        /** A horizontal textured square of half-size r, lying on the ground. */
        private static void ground(VertexConsumer vc, PoseStack ps, float r, float alpha) {
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            int a = (int) (255 * Mth.clamp(alpha, 0, 1));
            gv(vc, m, n, -r, -r, 0, 0, a);
            gv(vc, m, n, -r, r, 0, 1, a);
            gv(vc, m, n, r, r, 1, 1, a);
            gv(vc, m, n, r, -r, 1, 0, a);
        }

        private static void gv(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, int a) {
            vc.vertex(m, x, 0, z).color(255, 255, 255, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        }

        /** A hexagonal ice crystal, point down: a long lower cone (tip at the origin) and a short upper cap. */
        private static void spike(VertexConsumer vc, PoseStack ps) {
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            for (int i = 0; i < SIDES; i++) {
                float a0 = Mth.TWO_PI * i / SIDES, a1 = Mth.TWO_PI * (i + 1) / SIDES;
                float x0 = Mth.cos(a0) * WIDTH, z0 = Mth.sin(a0) * WIDTH, x1 = Mth.cos(a1) * WIDTH, z1 = Mth.sin(a1) * WIDTH;
                float nx = Mth.cos((a0 + a1) / 2), nz = Mth.sin((a0 + a1) / 2);
                int shade = 190 + (int) (65 * Math.abs(Mth.cos((a0 + a1) / 2 + 0.6F)));   // faceted look
                float u0 = i / (float) SIDES, u1 = (i + 1) / (float) SIDES;
                // lower cone: the long blade down to the point
                sv(vc, m, n, 0, 0, 0, (u0 + u1) / 2, 1, shade, nx, -0.2F, nz);
                sv(vc, m, n, 0, 0, 0, (u0 + u1) / 2, 1, shade, nx, -0.2F, nz);
                sv(vc, m, n, x1, BASE_Y, z1, u1, 0.3F, shade, nx, -0.2F, nz);
                sv(vc, m, n, x0, BASE_Y, z0, u0, 0.3F, shade, nx, -0.2F, nz);
                // upper cap
                sv(vc, m, n, x0, BASE_Y, z0, u0, 0.3F, shade, nx, 0.4F, nz);
                sv(vc, m, n, x1, BASE_Y, z1, u1, 0.3F, shade, nx, 0.4F, nz);
                sv(vc, m, n, 0, TOP_Y, 0, (u0 + u1) / 2, 0, shade, nx, 0.4F, nz);
                sv(vc, m, n, 0, TOP_Y, 0, (u0 + u1) / 2, 0, shade, nx, 0.4F, nz);
            }
        }

        private static void sv(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int shade,
                               float nx, float ny, float nz) {
            vc.vertex(m, x, y, z).color(shade, shade, 255, 225).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0).normal(n, nx, ny, nz).endVertex();
        }

        @Override
        public boolean shouldRender(com.soulless.boss.IceShardEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
            return true;   // the spike starts far above the entity's box
        }

        @Override
        public ResourceLocation getTextureLocation(com.soulless.boss.IceShardEntity e) {
            return SHARD;
        }
    }

    /** Nothing to draw: the entity is pure particles. */
    public static class Invisible<T extends net.minecraft.world.entity.Entity> extends EntityRenderer<T> {
        public Invisible(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(T e) {
            return SoulForgedRenderer.ICON_MODEL;
        }
    }

    /**
     * The flying Soul Rend wave: the same diagonal crescent as the cut, upright across its flight (from upper
     * right to lower left), bowing forward, additive purple with a white leading edge; fades near its range.
     */
    public static class Slash extends EntityRenderer<SoulSlashEntity> {
        public Slash(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(SoulSlashEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float age = e.tickCount + pt;
            float life = (float) (SoulSlashEntity.RANGE / SoulSlashEntity.SPEED);
            float fade = Mth.clamp(Math.min(age / 2F, (life - age) / 5F), 0, 1) * (0.5F + 0.5F * e.power());
            if (fade <= 0) return;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-e.flightYaw()));
            ps.mulPose(Axis.XP.rotationDegrees(e.flightPitch()));   // tilted along the line of sight
            ps.translate(0, -1.65, 0);                               // the entity sits at the crescent's centre
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            int seg = 24;
            double w = SoulSlashEntity.HALF_WIDTH;
            for (int i = 0; i < seg; i++) {
                float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
                Vec3 c0 = centre(t0, w), c1 = centre(t1, w);
                float th0 = thickness(t0), th1 = thickness(t1);
                int a0 = (int) (220 * fade * Mth.sin(t0 * Mth.PI)), a1 = (int) (220 * fade * Mth.sin(t1 * Mth.PI));
                // body of the wave, both sides
                ribbon(vc, m, c0, c1, th0, th1, 120, 30, 220, a0 / 2, 200, 90, 255, a0, a1);
                // bright leading edge
                ribbon(vc, m, c0.add(0, 0, 0.05), c1.add(0, 0, 0.05), th0 * 0.25F, th1 * 0.25F, 255, 230, 255, 0, 255, 240, 255, a0, a1);
            }
            ps.popPose();
        }

        /** Crescent centre line in local space: x across (+x right), y up, z forward. */
        private static Vec3 centre(float t, double w) {
            double x = Mth.lerp(t, w, -w);
            double y = Mth.lerp(t, 2.9, 0.4);
            double z = Math.sin(t * Math.PI) * 0.9;
            return new Vec3(x, y, z);
        }

        private static float thickness(float t) {
            return 0.15F + 0.45F * Mth.sin(t * Mth.PI);
        }

        /** A quad strip segment widened perpendicular to the crescent, in the plane facing forward. */
        private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float ta, float tb,
                                   int ri, int gi, int bi, int innerAlpha, int ro, int go, int bo, int aa, int ab) {
            Vec3 along = b.subtract(a).normalize();
            Vec3 perp = new Vec3(-along.y, along.x, 0).normalize();   // in the x/y plane
            Vec3 a0 = a.subtract(perp.scale(ta)), a1 = a.add(perp.scale(ta));
            Vec3 c0 = b.subtract(perp.scale(tb)), c1 = b.add(perp.scale(tb));
            int ia = Math.min(aa, innerAlpha), ib = Math.min(ab, innerAlpha);
            for (int side = 0; side < 2; side++) {
                Vec3[] q = side == 0 ? new Vec3[]{a0, a1, c1, c0} : new Vec3[]{c0, c1, a1, a0};
                int[] al = side == 0 ? new int[]{ia, aa, ab, ib} : new int[]{ib, ab, aa, ia};
                for (int k = 0; k < 4; k++) {
                    boolean outer = (q[k] == a1 || q[k] == c1);
                    vc.vertex(m, (float) q[k].x, (float) q[k].y, (float) q[k].z)
                            .color(outer ? ro : ri, outer ? go : gi, outer ? bo : bi, Mth.clamp(al[k], 0, 255)).endVertex();
                }
            }
        }

        @Override
        public boolean shouldRender(SoulSlashEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
            return true;
        }

        @Override
        public ResourceLocation getTextureLocation(SoulSlashEntity e) {
            return SoulForgedRenderer.ICON_MODEL;
        }
    }

    private BossRenderers() {}
}
