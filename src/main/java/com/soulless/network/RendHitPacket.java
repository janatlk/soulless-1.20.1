package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> nearby clients: the Soul Rend cut connected. Carries the swing origin and facing for the slash
 * trail, and how many creatures were struck.
 */
public record RendHitPacket(int entityId, double x, double y, double z, float yaw, int struck) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeVarInt(struck);
    }

    public static RendHitPacket decode(FriendlyByteBuf buf) {
        return new RendHitPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.rendHit(this));
    }
}
