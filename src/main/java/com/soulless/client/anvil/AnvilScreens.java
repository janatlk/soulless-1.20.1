package com.soulless.client.anvil;

import com.soulless.forge.SoulAnvilBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Opens the Soul Anvil screen the server asked for (network.AnvilOpenPacket). */
public final class AnvilScreens {
    public static void open(BlockPos pos, int screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(screen == SoulAnvilBlock.SCREEN_UPGRADE ? new UpgradeScreen(pos) : new ForgeScreen(pos));
    }

    private AnvilScreens() {}
}
