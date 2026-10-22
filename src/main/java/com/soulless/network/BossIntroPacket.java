package com.soulless.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a boss's intro begins - the camera turns on entity {@code id} for {@code ticks}; {@code key} names its title card. */
public record BossIntroPacket(int id, int ticks, String key) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeVarInt(ticks);
        buf.writeUtf(key, 64);
    }

    public static BossIntroPacket decode(FriendlyByteBuf buf) {
        return new BossIntroPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.soulless.client.BossIntro.start(id, ticks, key));
    }
}
