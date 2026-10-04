package com.soulless.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** The katana's arms and stance come from playerAnimator ({@link KatanaAnim}); this only supplies the 3D renderer. */
public class KatanaClientExtensions implements IClientItemExtensions {
    private KatanaRenderer renderer;

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            renderer = new KatanaRenderer();
        }
        return renderer;
    }
}
