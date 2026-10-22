package com.soulless.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open a Soul Anvil screen (forge.SoulAnvilBlock.SCREEN_FORGE / SCREEN_UPGRADE) for the anvil at pos. */
public record AnvilOpenPacket(BlockPos pos, int screen) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(screen);
    }

    public static AnvilOpenPacket decode(FriendlyByteBuf buf) {
        return new AnvilOpenPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.anvil.AnvilScreens.open(pos, screen));
    }
}
