package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> one client, from {@code /soulless}: toggle the hit-zone preview, or clear the Soul Rend cooldown. */
public record ClientControlPacket(int action, boolean value) {
    public static final int SHOW_ZONES = 0;
    public static final int RESET_COOLDOWNS = 1;
    public static final int PARRY_KNOWN = 2;
    public static final int RITUAL_KNOWN = 3;

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBoolean(value);
    }

    public static ClientControlPacket decode(FriendlyByteBuf buf) {
        return new ClientControlPacket(buf.readByte(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.ClientPacketHandler.control(action, value));
    }
}
