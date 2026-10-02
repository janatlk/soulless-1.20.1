package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an entity started Soul Rend (play the wind-up, block attacks client-side). */
public record RendStartPacket(int entityId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static RendStartPacket decode(FriendlyByteBuf buf) {
        return new RendStartPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.rendStart(entityId));
    }
}
