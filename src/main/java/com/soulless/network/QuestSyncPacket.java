package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: how far the sensei's training has gone (npc.SenseiQuest.stage) and the monsters slain, for tooltips. */
public record QuestSyncPacket(int stage, int kills) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(stage);
        buf.writeVarInt(kills);
    }

    public static QuestSyncPacket decode(FriendlyByteBuf buf) {
        return new QuestSyncPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.soulless.client.ClientState.questStage = stage;
            com.soulless.client.ClientState.questKills = kills;
        });
    }
}
