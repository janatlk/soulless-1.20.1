package com.soulless.network;

import com.soulless.ability.RendManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: right click was let go during the Soul Rend wind-up. */
public record RendReleasePacket() {
    public void encode(FriendlyByteBuf buf) {}

    public static RendReleasePacket decode(FriendlyByteBuf buf) {
        return new RendReleasePacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            RendManager.release(player);
        }
    }
}
