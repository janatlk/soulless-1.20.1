package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: an entity's Soul Rend timeline is now {@code elapsed} ticks in. 0 = the wind-up began;
 * WINDUP = right click was released, cut now; TOTAL = aborted.
 */
public record RendStartPacket(int entityId, int elapsed) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(elapsed);
    }

    public static RendStartPacket decode(FriendlyByteBuf buf) {
        return new RendStartPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.rendStart(entityId, elapsed));
    }
}
