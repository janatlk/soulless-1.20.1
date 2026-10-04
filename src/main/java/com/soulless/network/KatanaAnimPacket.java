package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: a player's katana move to play on their model - a cut (0 kesa, 1 gyaku, 2 tsuki, to the
 * others watching), the Kitsune ritual (3, to everyone including the player) or the ritual broken off (-1).
 * {@code fast}: under the Kitsune's blessing (the move plays faster).
 */
public record KatanaAnimPacket(int entityId, int step, boolean fast) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(step);
        buf.writeBoolean(fast);
    }

    public static KatanaAnimPacket decode(FriendlyByteBuf buf) {
        return new KatanaAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.KatanaAnim.remoteMove(entityId, step, fast));
    }
}
