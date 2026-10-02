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
