package com.soulless.client.anvil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulless.forge.SoulAnvilBlock;
import com.soulless.forge.SoulAnvilBlockEntity;
import com.soulless.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * What lies on the Soul Anvil: an artifact laid on to be worked rests flat on its face; an artifact being forged
 * hovers above it, turning (what it still needs: AnvilHud).
 */
public class SoulAnvilRenderer implements BlockEntityRenderer<SoulAnvilBlockEntity> {
    private final Font font;

    public SoulAnvilRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(SoulAnvilBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level.getGameTime() % 100000) + partial;
        var facing = be.getBlockState().getValue(SoulAnvilBlock.FACING);
        if (!be.held().isEmpty()) {
            ps.pushPose();
            ps.translate(0.5, 1.02, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.scale(1.3F, 1.3F, 1.3F);
            ghost(mc, be.held(), ps, buffers);
            ps.popPose();
            return;
        }
        var r = be.recipe();
        if (r == null) return;
        // the ghost, turning and bobbing
        ps.pushPose();
        ps.translate(0.5, 1.35 + Mth.sin(time * 0.08F) * 0.05, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(time * 2));
        ps.scale(1.5F, 1.5F, 1.5F);
        ghost(mc, new ItemStack(r.item()), ps, buffers);
        ps.popPose();
        if (mc.level.random.nextInt(4) == 0) {
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, be.getBlockPos().getX() + 0.3 + mc.level.random.nextFloat() * 0.4,
                    be.getBlockPos().getY() + 1.05, be.getBlockPos().getZ() + 0.3 + mc.level.random.nextFloat() * 0.4, 0, 0.02, 0);
        }

        // what it still needs is shown beside the crosshair while the anvil is looked at (AnvilHud)
    }

    /**
     * An item in the world. The mod's 3D items draw through GeckoLib, which comes out black here; for those the flat
     * icon model ("item/<name>_icon") is drawn instead, thickened like a dropped item.
     */
    private static void ghost(Minecraft mc, ItemStack stack, PoseStack ps, MultiBufferSource buffers) {
        var ir = mc.getItemRenderer();
        var model = ir.getModel(stack, mc.level, null, 0);
        if (model.isCustomRenderer()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            var icon = mc.getModelManager().getModel(new net.minecraft.resources.ResourceLocation(key.getNamespace(), "item/" + key.getPath() + "_icon"));
            if (icon != mc.getModelManager().getMissingModel()) {
                ir.render(stack, ItemDisplayContext.GROUND, false, ps, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY, icon);
                return;
            }
        }
        ir.renderStatic(stack, ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(SoulAnvilBlockEntity be) {
        return true;
    }
}
