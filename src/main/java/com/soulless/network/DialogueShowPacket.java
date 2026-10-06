package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: show a dialogue node (the speaker, their accent colour, the pages of text, the options the
 * player may choose), or with {@code entityId} -1 close the dialogue window.
 */
public record DialogueShowPacket(int entityId, Component name, int accent, List<Component> pages, List<Component> options) {
    public static DialogueShowPacket close() {
        return new DialogueShowPacket(-1, Component.empty(), 0, List.of(), List.of());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeComponent(name);
        buf.writeInt(accent);
        buf.writeCollection(pages, FriendlyByteBuf::writeComponent);
        buf.writeCollection(options, FriendlyByteBuf::writeComponent);
    }

    public static DialogueShowPacket decode(FriendlyByteBuf buf) {
        return new DialogueShowPacket(buf.readVarInt(), buf.readComponent(), buf.readInt(),
                buf.readList(FriendlyByteBuf::readComponent), buf.readList(FriendlyByteBuf::readComponent));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.DialogueScreen.show(this));
    }
}
