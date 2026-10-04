package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: a player's katana move to play on their model - a cut (0 kesa, 1 gyaku, 2 tsuki, to the
 * others watching), the Kitsune ritual (3, to everyone including the player) or the ritual broken off (-1).
 * {@code rate}: how fast the move plays (the Kitsune's blessing, a riposte).
 */
public record KatanaAnimPacket(int entityId, int step, float rate) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(step);
        buf.writeFloat(rate);
    }

    public static KatanaAnimPacket decode(FriendlyByteBuf buf) {
        return new KatanaAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.KatanaAnim.remoteMove(entityId, step, rate));
    }
}
