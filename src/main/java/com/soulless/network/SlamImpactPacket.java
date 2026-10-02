package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> nearby clients: a slam landed here (camera shake + shockwave particles). */
public record SlamImpactPacket(double x, double y, double z, float radius) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(radius);
    }

    public static SlamImpactPacket decode(FriendlyByteBuf buf) {
        return new SlamImpactPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.slamImpact(x, y, z, radius));
    }
}
