package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a soul left a body at (x, y, z); {@code to} is the entity whose vessel caught it, -1: it got away. */
public record SoulWispPacket(double x, double y, double z, int to) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(to);
    }

    public static SoulWispPacket decode(FriendlyByteBuf buf) {
        return new SoulWispPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.SoulWisp.spawn(x, y, z, to));
    }
}
