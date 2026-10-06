package com.soulless.network;

import com.soulless.npc.dialogue.DialogueManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player chose the shown option {@code index}, or -1: closed the dialogue window. */
public record DialogueChoicePacket(int index) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    public static DialogueChoicePacket decode(FriendlyByteBuf buf) {
        return new DialogueChoicePacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) DialogueManager.choose(player, index);
    }
}
