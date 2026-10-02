package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulless.Soulless;
import com.soulless.item.SoulForgedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib renderer for the 3D sword (model and animations come from art/soul_forged.bbmodel).
 * Inventory slots get a flat pixel-art icon; during "Soul Crush"
 * the blade is turned point-down in third person so it can be driven into the ground.
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
        if ((context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                && ClientState.renderingPlayer != null) {
            SlamAnim.Pose pose = SlamAnim.sample(ClientState.renderingPlayer);
            if (pose != null && pose.itemPitch != 0) {
                // item +x points down the arm, +y along the blade: turning about z swings the point toward the ground
                poseStack.translate(0.5F, 0.51F, 0.5F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(pose.itemPitch));
                poseStack.translate(-0.5F, -0.51F, -0.5F);
            }
        }
        super.renderByItem(stack, context, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }

}
