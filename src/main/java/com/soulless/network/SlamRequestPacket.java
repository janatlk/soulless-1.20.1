package com.soulless.network;

import com.soulless.ability.SlamManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed the slam key. The server validates everything. */
public record SlamRequestPacket() {
    public void encode(FriendlyByteBuf buf) {}

    public static SlamRequestPacket decode(FriendlyByteBuf buf) {
        return new SlamRequestPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            SlamManager.tryStart(player);
        }
    }
}
