package com.soulless.network;

import com.soulless.ability.SlamManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed the ability key (R): Soul Crush with Soul Forged, the Kitsune ritual with the katana. */
public record SlamRequestPacket() {
    public void encode(FriendlyByteBuf buf) {}

    public static SlamRequestPacket decode(FriendlyByteBuf buf) {
        return new SlamRequestPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            if (player.getMainHandItem().getItem() instanceof com.soulless.item.SoulKatanaItem) {
                com.soulless.item.KitsuneRitual.tryStart(player);
            } else {
                SlamManager.tryStart(player);
            }
        }
    }
}
