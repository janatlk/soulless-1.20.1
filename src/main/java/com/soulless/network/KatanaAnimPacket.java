package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> other clients: this player started a katana cut (0 kesa, 1 gyaku, 2 tsuki) - play it on their model. */
public record KatanaAnimPacket(int entityId, int step) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(step);
    }

    public static KatanaAnimPacket decode(FriendlyByteBuf buf) {
        return new KatanaAnimPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.KatanaAnim.remoteCut(entityId, step));
    }
}
