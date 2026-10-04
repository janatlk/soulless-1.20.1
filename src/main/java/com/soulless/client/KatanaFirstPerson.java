package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * First-person katana: at rest the blade is raised to the upper right in the jodan guard; the two cuts sweep it
 * diagonally across the screen (kesa: upper right to lower left, gyaku: upper left to lower right). The poses are
 * the "fp" field of {@link KatanaPoses} and play in step with the third-person cut ({@link KatanaAnim}).
 */
public final class KatanaFirstPerson {
    private static final float[] FP = new float[6];

    public static void render(RenderHandEvent event, LocalPlayer player, ItemStack stack) {
        KatanaAnim anim = KatanaAnim.of(player);
        float partial = event.getPartialTick();
        String cut = anim != null ? anim.currentCut(partial) : null;
        float[] p = KatanaPoses.sample(cut, cut != null ? anim.cutTime(partial) : 0, "fp", FP);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(p[0], p[1] - event.getEquipProgress() * 0.6F, p[2]);
        ps.mulPose(Axis.XP.rotationDegrees(p[3]));
        ps.mulPose(Axis.ZP.rotationDegrees(p[5]));
        ps.mulPose(Axis.YP.rotationDegrees(p[4]));
        float sc = KatanaPoses.number("fp.scale");
        ps.scale(sc, sc, sc);
        Minecraft.getInstance().gameRenderer.itemInHandRenderer.renderItem(player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                false, ps, event.getMultiBufferSource(), event.getPackedLight());
        ps.popPose();
    }

    private KatanaFirstPerson() {}
}
