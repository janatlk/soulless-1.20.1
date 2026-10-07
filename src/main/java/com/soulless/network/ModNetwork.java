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
        CHANNEL.messageBuilder(EffectSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EffectSyncPacket::encode).decoder(EffectSyncPacket::decode)
                .consumerMainThread(EffectSyncPacket::handle).add();
        CHANNEL.messageBuilder(RendStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RendStartPacket::encode).decoder(RendStartPacket::decode)
                .consumerMainThread(RendStartPacket::handle).add();
        CHANNEL.messageBuilder(RendHitPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RendHitPacket::encode).decoder(RendHitPacket::decode)
                .consumerMainThread(RendHitPacket::handle).add();
        CHANNEL.messageBuilder(RendReleasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RendReleasePacket::encode).decoder(RendReleasePacket::decode)
                .consumerMainThread(RendReleasePacket::handle).add();
        CHANNEL.messageBuilder(KatanaCutPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KatanaCutPacket::encode).decoder(KatanaCutPacket::decode)
                .consumerMainThread(KatanaCutPacket::handle).add();
        CHANNEL.messageBuilder(KatanaAnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KatanaAnimPacket::encode).decoder(KatanaAnimPacket::decode)
                .consumerMainThread(KatanaAnimPacket::handle).add();
        CHANNEL.messageBuilder(ClientControlPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientControlPacket::encode).decoder(ClientControlPacket::decode)
                .consumerMainThread(ClientControlPacket::handle).add();
        CHANNEL.messageBuilder(DialogueShowPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DialogueShowPacket::encode).decoder(DialogueShowPacket::decode)
                .consumerMainThread(DialogueShowPacket::handle).add();
        CHANNEL.messageBuilder(QuestSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestSyncPacket::encode).decoder(QuestSyncPacket::decode)
                .consumerMainThread(QuestSyncPacket::handle).add();
        CHANNEL.messageBuilder(DialogueChoicePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialogueChoicePacket::encode).decoder(DialogueChoicePacket::decode)
                .consumerMainThread(DialogueChoicePacket::handle).add();
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
