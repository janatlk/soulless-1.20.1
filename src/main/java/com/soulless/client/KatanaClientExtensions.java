package com.soulless.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** The katana's arms and stance come from playerAnimator ({@link KatanaAnim}); this only supplies the 3D renderer. */
public class KatanaClientExtensions implements IClientItemExtensions {
    private final boolean training;
    private KatanaRenderer renderer;

    public KatanaClientExtensions(boolean training) {
        this.training = training;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            renderer = new KatanaRenderer(training);
        }
        return renderer;
    }
}
