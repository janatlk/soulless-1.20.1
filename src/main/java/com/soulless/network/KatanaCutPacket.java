package com.soulless.network;

import com.soulless.item.KatanaCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the katana attack about to be made is this step of the combo (0 kesa, 1 gyaku, 2 tsuki). */
public record KatanaCutPacket(int step) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(step);
    }

    public static KatanaCutPacket decode(FriendlyByteBuf buf) {
        return new KatanaCutPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            KatanaCombat.announce(player, step);
        }
    }
}
