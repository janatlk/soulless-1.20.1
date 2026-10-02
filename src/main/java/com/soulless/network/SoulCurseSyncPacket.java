package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: an entity's Soul Curse started (duration > 0) or ended (0).
 * Vanilla only syncs effects of the local player, so mobs need this to show the purple flame.
 */
public record SoulCurseSyncPacket(int entityId, int duration) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(duration);
    }

    public static SoulCurseSyncPacket decode(FriendlyByteBuf buf) {
        return new SoulCurseSyncPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.curseSync(entityId, duration));
    }
}
