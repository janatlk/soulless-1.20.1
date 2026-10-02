package com.soulless.network;

import com.soulless.Soulless;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Soulless.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SlamRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SlamRequestPacket::encode).decoder(SlamRequestPacket::decode)
                .consumerMainThread(SlamRequestPacket::handle).add();
        CHANNEL.messageBuilder(SlamStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlamStartPacket::encode).decoder(SlamStartPacket::decode)
                .consumerMainThread(SlamStartPacket::handle).add();
        CHANNEL.messageBuilder(SlamImpactPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlamImpactPacket::encode).decoder(SlamImpactPacket::decode)
                .consumerMainThread(SlamImpactPacket::handle).add();
        CHANNEL.messageBuilder(SoulCurseSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SoulCurseSyncPacket::encode).decoder(SoulCurseSyncPacket::decode)
                .consumerMainThread(SoulCurseSyncPacket::handle).add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendTrackingAndSelf(Entity entity, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), msg);
    }

    public static void sendNear(ServerLevel level, Vec3 pos, double radius, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, radius, level.dimension())), msg);
    }

    private ModNetwork() {}
}
