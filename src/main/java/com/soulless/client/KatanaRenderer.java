package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.soulless.Soulless;
import com.soulless.item.SoulKatanaItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * GeckoLib renderer for the 3D katana (tools/gen_katana.py); inventory slots get the flat pixel icon. The Training
 * Katana is the same model in wood (tools/gen_quest_items.py).
 */
public class KatanaRenderer extends GeoItemRenderer<SoulKatanaItem> {
    public static final ResourceLocation ICON_MODEL = Soulless.id("item/soul_katana_icon");
    public static final ResourceLocation TRAINING_ICON_MODEL = Soulless.id("item/training_katana_icon");
    private static final ResourceLocation TRAINING_TEXTURE = Soulless.id("textures/item/training_katana.png");
    private final boolean training;

    public KatanaRenderer(boolean training) {
        super(new DefaultedItemGeoModel<SoulKatanaItem>(Soulless.id("soul_katana")) {
            @Override
            public ResourceLocation getTextureResource(SoulKatanaItem item) {
                return training ? TRAINING_TEXTURE : super.getTextureResource(item);
            }
        });
        this.training = training;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (context == ItemDisplayContext.GUI) {
            Minecraft mc = Minecraft.getInstance();
            BakedModel icon = mc.getModelManager().getModel(training ? TRAINING_ICON_MODEL : ICON_MODEL);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            mc.getItemRenderer().render(stack, context, false, poseStack, buffers, light, overlay, icon);
            poseStack.popPose();
            return;
        }
        super.renderByItem(stack, context, poseStack, buffers, light, overlay);
    }
}
