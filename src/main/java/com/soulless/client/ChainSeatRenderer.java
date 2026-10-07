package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.soulless.boss.ChainSeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Four chains (the vanilla chain block, link after link) shooting up out of the floor round the seated player and
 * binding them at the chest; in the first half second they grow from the anchors.
 */
public class ChainSeatRenderer extends EntityRenderer<ChainSeatEntity> {
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final float GROW = 10;

    public ChainSeatRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0;
    }

    @Override
    public void render(ChainSeatEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float grow = Mth.clamp(age / GROW, 0, 1);
        grow = 1 - (1 - grow) * (1 - grow);
        Vec3 chest = new Vec3(0, 0.85, 0);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2 + e.getId() * 0.37;
            Vec3 anchor = new Vec3(Math.cos(a) * 1.7, -0.05, Math.sin(a) * 1.7);
            Vec3 to = anchor.add(chest.subtract(anchor).scale(grow));
            chain(pose, buffers, light, anchor, to);
        }
    }

    /** Chain links from {@code a} to {@code b} (entity-relative, blocks). */
    private static void chain(PoseStack pose, MultiBufferSource buffers, int light, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        float len = (float) d.length();
        if (len < 0.05F) return;
        var blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.translate(a.x, a.y, a.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize()));
        float step = 0.75F;
        for (float s = 0; s < len; s += step) {
            pose.pushPose();
            pose.translate(0, s, 0);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(((int) (s / step)) % 2 == 0 ? 0 : 90));
            float k = Math.min(1, (len - s) / step);
            pose.scale(1, k, 1);
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(CHAIN, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRender(ChainSeatEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ChainSeatEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
