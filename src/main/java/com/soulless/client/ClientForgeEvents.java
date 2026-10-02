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
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import com.soulless.ability.SlamTimeline;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
        // Slam effects per phase; finished slams are forgotten.
        Iterator<Int2LongMap.Entry> slams = ClientState.SLAMS.int2LongEntrySet().iterator();
        while (slams.hasNext()) {
            Int2LongMap.Entry e = slams.next();
            Entity entity = level.getEntity(e.getIntKey());
            if (entity == null || ClientState.slamTicks(entity) < 0 || (ClientState.impactTicks(entity) >= 0
                    && ClientState.impactTicks(entity) > SlamTimeline.KNEEL + SlamTimeline.RECOVER)) {
                ClientState.IMPACTS.remove(e.getIntKey());
                slams.remove();
                continue;
            }
            spawnSlamEffects(level, entity, rnd);
        }
    }

    private static void spawnSlamEffects(ClientLevel level, Entity e, RandomSource rnd) {
        float t = ClientState.slamTicks(e);
        float ti = ClientState.impactTicks(e);
        float yaw = e instanceof LivingEntity l ? l.yBodyRot * Mth.DEG_TO_RAD : e.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 c = e.position().add(0, 0.9, 0);
        if (ti >= 0) {
            if (ti < SlamTimeline.KNEEL) {
                // the buried blade keeps venting soul fire
                Vec3 tip = e.position().add(forward.scale(1.1));
                for (int i = 0; i < 2; i++) {
                    level.addParticle(ModParticles.SOUL_EMBER.get(), tip.x + (rnd.nextDouble() - 0.5) * 0.3, tip.y + 0.1,
                            tip.z + (rnd.nextDouble() - 0.5) * 0.3, 0, 0.12 + rnd.nextDouble() * 0.12, 0);
                }
            }
            return;
        }
        if (t < SlamTimeline.CHARGE) {
            // soul fire is drawn in from all around and gathers in the blade
            for (int i = 0; i < 5; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 1.8 + rnd.nextDouble() * 0.8;
                Vec3 from = c.add(Math.cos(a) * r, (rnd.nextDouble() - 0.4) * 1.6, Math.sin(a) * r);
                Vec3 v = c.add(forward.scale(0.5)).subtract(from).scale(0.14);
                level.addParticle(ModParticles.SOUL_EMBER.get(), from.x, from.y, from.z, v.x, v.y, v.z);
            }
        } else if (t < SlamTimeline.PLUNGE) {
            // a burning arc traced by the blade during the flip
            float u = (t - SlamTimeline.CHARGE) / (SlamTimeline.PLUNGE - SlamTimeline.CHARGE);
            double ang = u * Math.PI * 2;
            for (int i = 0; i < 4; i++) {
                double a = ang - i * 0.18;
                Vec3 p = c.add(forward.scale(Math.sin(a) * 1.6)).add(0, Math.cos(a) * 1.6, 0);
                level.addParticle(ModParticles.SOUL_EMBER.get(), p.x, p.y, p.z, 0, 0.01, 0);
            }
        } else {
            // diving: a comet tail of soul fire
            for (int i = 0; i < 6; i++) {
                level.addParticle(ModParticles.SOUL_EMBER.get(), e.getX() + (rnd.nextDouble() - 0.5) * 0.6,
                        e.getY() + rnd.nextDouble() * 1.8, e.getZ() + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.5, 0);
            }
        }
    }

    // ------------------------------------------------------------------ whole-body slam motion

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ClientState.renderingPlayer = p;
        SlamAnim.Pose pose = SlamAnim.sample(p);
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        if (pose == null) return;
        float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        // pitch axis = the player's right/left line; positive turns the head forward (front flip, lean)
        Vector3f axis = new Vector3f(Mth.cos(yaw), 0, Mth.sin(yaw));
        float pitch = (pose.flip + pose.lean) * Mth.DEG_TO_RAD;
        ps.translate(0, -pose.lower, 0);
        ps.translate(0, 0.9F, 0);
        ps.mulPose(new Quaternionf().rotateAxis(pitch, axis));
        ps.translate(0, -0.9F, 0);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        event.getPoseStack().popPose();
        ClientState.renderingPlayer = null;
    }

    /** Every attack swing with the sword starts the slow first-person slash (also on misses). */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof SoulForgedItem)) return;
        float t = ClientState.slashTicks(mc.player);
        if (t < 0 || t >= FirstPersonSword.SLASH_TICKS * 0.7F) {
            ClientState.slashStart = ClientState.gameTime();
        }
    }

    /** First person: the greatsword is drawn by {@link FirstPersonSword} (held pose, slash, Soul Crush). */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.player == null
                || !(event.getItemStack().getItem() instanceof SoulForgedItem)) return;
        event.setCanceled(true);
        if (mc.getCameraEntity() == mc.player) {   // not while spectating through another entity
            FirstPersonSword.render(event, mc.player, event.getItemStack());
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
