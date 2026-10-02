package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.soulless.Soulless;
import com.soulless.item.SoulForgedItem;
import com.soulless.network.ModNetwork;
import com.soulless.network.SlamRequestPacket;
import com.soulless.registry.ModParticles;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final ResourceLocation FIRE_0 = Soulless.id("block/soul_curse_fire_0");
    private static final ResourceLocation FIRE_1 = Soulless.id("block/soul_curse_fire_1");

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        while (ClientModEvents.SLAM_KEY.consumeClick()) {
            if (player.getMainHandItem().getItem() instanceof SoulForgedItem item) {
                float left = player.getCooldowns().getCooldownPercent(item, 0);
                if (left > 0) {
                    int seconds = Mth.ceil(left * com.soulless.ability.SlamManager.COOLDOWN_TICKS / 20F);
                    player.displayClientMessage(Component.translatable("message.soulless.slam_cooldown", seconds), true);
                } else {
                    ModNetwork.sendToServer(new SlamRequestPacket());
                }
            }
        }

        ClientState.shake *= 0.8F;
        if (ClientState.shake < 0.01F) ClientState.shake = 0;

        long now = level.getGameTime();
        RandomSource rnd = level.getRandom();

        // Cursed entities smoulder with soul embers.
        Iterator<Int2LongMap.Entry> it = ClientState.CURSED.int2LongEntrySet().iterator();
        while (it.hasNext()) {
            Int2LongMap.Entry e = it.next();
            Entity entity = level.getEntity(e.getIntKey());
            if (entity == null || !entity.isAlive() || e.getLongValue() <= now) {
                it.remove();
                continue;
            }
            if (rnd.nextInt(2) == 0) {
                level.addParticle(ModParticles.SOUL_EMBER.get(),
                        entity.getRandomX(0.6), entity.getRandomY(), entity.getRandomZ(0.6),
                        0, 0.02 + rnd.nextDouble() * 0.03, 0);
            }
        }
        ClientState.SLAMS.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > ClientState.SLAM_ANIM_TICKS + 5);

        // The blade burns constantly: embers rise off it for everyone holding the sword.
        for (Player p : level.players()) {
            if (!(p.getMainHandItem().getItem() instanceof SoulForgedItem) || p.isInvisible()) continue;
            if (p == player && mc.options.getCameraType() == CameraType.FIRST_PERSON) continue;
            if (p.distanceToSqr(player) > 48 * 48) continue;
            spawnBladeEmbers(level, p, rnd);
        }
    }

    private static void spawnBladeEmbers(ClientLevel level, Player p, RandomSource rnd) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 hand = p.position().add(0, p.isCrouching() ? 0.85 : 1.05, 0).add(forward.scale(0.55)).add(right.scale(-0.05));
        Vec3 blade = forward.scale(0.5).add(0, 0.866, 0);
        for (int i = 0; i < 2; i++) {
            double along = 0.35 + rnd.nextDouble() * 1.45;
            Vec3 at = hand.add(blade.scale(along)).add(right.scale((rnd.nextDouble() - 0.5) * 0.25));
            level.addParticle(ModParticles.SOUL_EMBER.get(), at.x, at.y, at.z, 0, 0.015 + rnd.nextDouble() * 0.02, 0);
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity.isInvisible() || !ClientState.isCursed(entity)) return;
        renderSoulFlame(event.getPoseStack(), event.getMultiBufferSource(), entity);
    }

    /** Same shape as vanilla's burning overlay (EntityRenderDispatcher#renderFlame), with soul-fire sprites. */
    private static void renderSoulFlame(PoseStack poseStack, MultiBufferSource buffers, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        var atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite s0 = atlas.getSprite(FIRE_0);
        TextureAtlasSprite s1 = atlas.getSprite(FIRE_1);

        poseStack.pushPose();
        float scale = entity.getBbWidth() * 1.4F;
        poseStack.scale(scale, scale, scale);
        float halfWidth = 0.5F;
        float height = entity.getBbHeight() / scale;
        float y = 0.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(-mc.gameRenderer.getMainCamera().getYRot()));
        poseStack.translate(0.0F, 0.0F, -0.3F + (int) height * 0.02F);
        float z = 0.0F;
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; height > 0.0F; i++) {
            TextureAtlasSprite s = i % 2 == 0 ? s0 : s1;
            float u0 = s.getU0(), v0 = s.getV0(), u1 = s.getU1(), v1 = s.getV1();
            if (i / 2 % 2 == 0) {
                float tmp = u1;
                u1 = u0;
                u0 = tmp;
            }
            fireVertex(pose, vc, halfWidth, -y, z, u1, v1);
            fireVertex(pose, vc, -halfWidth, -y, z, u0, v1);
            fireVertex(pose, vc, -halfWidth, 1.4F - y, z, u0, v0);
            fireVertex(pose, vc, halfWidth, 1.4F - y, z, u1, v0);
            height -= 0.45F;
            y -= 0.45F;
            halfWidth *= 0.9F;
            z += 0.03F;
        }
        poseStack.popPose();
    }

    private static void fireVertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v) {
        vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(0, 10).uv2(240)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = ClientState.shake;
        if (s <= 0) return;
        double t = (ClientState.gameTime() + event.getPartialTick()) * 2.7;
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 3.1) * 4.0 * s));
        event.setYaw(event.getYaw() + (float) (Math.cos(t * 2.3) * 2.5 * s));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 4.7) * 3.0 * s));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.clear();
    }

    private ClientForgeEvents() {}
}
