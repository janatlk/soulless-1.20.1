package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed the armour's key (V): Breath of the Deep, if the Scaled Chestplate is worn. */
public record DeepDashPacket() {
    public void encode(FriendlyByteBuf buf) {}

    public static DeepDashPacket decode(FriendlyByteBuf buf) {
        return new DeepDashPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) com.soulless.ability.DeepDash.tryStart(player);
    }
}
