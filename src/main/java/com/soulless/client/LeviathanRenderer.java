package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulless.boss.LeviathanEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Leviathan: its photophores, eyes and tentacle tips glow (leviathan_glowmask.png); the whole body pitches as it swims. */
public class LeviathanRenderer extends GeoEntityRenderer<LeviathanEntity> {
    public LeviathanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LeviathanModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0;
    }

    @Override
    protected void applyRotations(LeviathanEntity e, PoseStack ps, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(e, ps, ageInTicks, rotationYaw, partialTick);
        ps.translate(0, 1.5, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-e.getViewXRot(partialTick)));
        ps.translate(0, -1.5, 0);
    }

    @Override
    public boolean shouldShowName(LeviathanEntity e) {
        return false;
    }
}
