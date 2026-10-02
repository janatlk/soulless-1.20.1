package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an entity started a slam, so play the arm/first-person animation. */
public record SlamStartPacket(int entityId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static SlamStartPacket decode(FriendlyByteBuf buf) {
        return new SlamStartPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.slamStart(entityId));
    }
}
