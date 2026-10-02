package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulless.Soulless;
import com.soulless.ability.SlamManager;
import com.soulless.item.SoulForgedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib renderer for the 3D sword. Inventory slots get a flat pixel-art icon instead
 * (a 3D greatsword squeezed into a slot reads badly). First person plays the slam swing.
 */
public class SoulForgedRenderer extends GeoItemRenderer<SoulForgedItem> {
    public static final ResourceLocation ICON_MODEL = Soulless.id("item/soul_forged_icon");

    public SoulForgedRenderer() {
        super(new DefaultedItemGeoModel<>(Soulless.id("soul_forged")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (context == ItemDisplayContext.GUI) {
            BakedModel icon = mc.getModelManager().getModel(ICON_MODEL);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            mc.getItemRenderer().render(stack, context, false, poseStack, buffers, light, overlay, icon);
            poseStack.popPose();
            return;
        }

        poseStack.pushPose();
        if (context.firstPerson() && mc.player != null) {
            float t = ClientState.slamTicks(mc.player);
            if (t >= 0) {
                float windup = SlamManager.WINDUP_TICKS;
                float angle;
                if (t < windup) {
                    angle = Mth.lerp(t / windup, 0, 55);           // heave it back over the shoulder
                } else if (t < windup + 3) {
                    angle = Mth.lerp((t - windup) / 3, 55, -75);   // drive it down
                } else if (t < windup + 11) {
                    angle = -75;
                } else {
                    angle = Mth.lerp(Math.min(1, (t - windup - 11) / 8), -75, 0);
                }
                poseStack.translate(0.5F, 0.3F, 0.5F);
                poseStack.mulPose(Axis.XP.rotationDegrees(angle));
                poseStack.translate(-0.5F, -0.3F, -0.5F);
            }
        }
        super.renderByItem(stack, context, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }

    @Override
    public RenderType getRenderType(SoulForgedItem animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        // translucent so the flame tongues fade out at their tips
        return RenderType.entityTranslucent(texture);
    }
}
