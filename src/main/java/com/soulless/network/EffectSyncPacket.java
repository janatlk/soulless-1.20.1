package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: one of our effects on an entity started (duration > 0) or ended (0).
 * Vanilla only syncs effects of the local player, so mobs need this for the purple flame / stun stars.
 */
public record EffectSyncPacket(int entityId, int kind, int duration) {
    public static final int SOUL_CURSE = 0;
    public static final int STUN = 1;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(kind);
        buf.writeVarInt(duration);
    }

    public static EffectSyncPacket decode(FriendlyByteBuf buf) {
        return new EffectSyncPacket(buf.readVarInt(), buf.readByte(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.effectSync(entityId, kind, duration));
    }
}
