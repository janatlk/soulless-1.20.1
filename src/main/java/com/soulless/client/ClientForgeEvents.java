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
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import com.soulless.ability.RendTimeline;
import com.soulless.ability.SlamTimeline;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.client.event.RenderLevelStageEvent;
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

    /** True while the local player is in Soul Crush or Soul Rend: the sword cannot be switched or dropped. */
    public static boolean abilityLocked(Player p) {
        return ClientState.rendTicks(p) >= 0 || ClientState.slamTicks(p) >= 0;
    }

    /**
     * Runs before vanilla handles key presses this tick: while an ability plays, swallow hotbar, drop and
     * offhand-swap presses so the sword stays in hand.
     */
    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !abilityLocked(mc.player)) return;
        for (KeyMapping key : mc.options.keyHotbarSlots) {
            while (key.consumeClick()) { }
        }
        while (mc.options.keyDrop.consumeClick()) { }
        while (mc.options.keySwapOffhand.consumeClick()) { }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && abilityLocked(mc.player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        while (ClientModEvents.SLAM_KEY.consumeClick()) {
            if (player.getMainHandItem().getItem() instanceof SoulForgedItem item && ClientState.rendTicks(player) < 0) {
                float left = player.getCooldowns().getCooldownPercent(item, 0);
                if (left > 0) {
                    int seconds = Mth.ceil(left * com.soulless.ability.SlamManager.COOLDOWN_TICKS / 20F);
                    player.displayClientMessage(Component.translatable("message.soulless.slam_cooldown", seconds), true);
                } else if (!player.onGround() && !com.soulless.item.SoulRing.worn(player)) {   // the ring allows it in mid-air
                    player.displayClientMessage(Component.translatable("message.soulless.slam_ground"), true);
                } else {
                    ModNetwork.sendToServer(new SlamRequestPacket());
                }
            }
        }

        // Soul Rend charges while right click is held; letting go cuts early (weaker)
        float rend = ClientState.rendTicks(player);
        if (rend >= 0 && rend < RendTimeline.WINDUP && !ClientState.rendReleaseSent && !mc.options.keyUse.isDown()) {
            ClientState.rendReleaseSent = true;
            ModNetwork.sendToServer(new com.soulless.network.RendReleasePacket());
        }

        // a held Soul Forged sheds a few souls along its blade
        if (level.getGameTime() % 4 == 0) {
            for (Player p : level.players()) {
                if (!(p.getMainHandItem().getItem() instanceof SoulForgedItem) || p.isInvisible() || p.distanceToSqr(player) > 32 * 32) continue;
                boolean firstPerson = p == player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
                spawnHeldSouls(level, p, firstPerson, level.getRandom());
            }
        }

        // a dropped Soul Forged smoulders with souls
        if (level.getGameTime() % 3 == 0) {
            for (Entity e : level.entitiesForRendering()) {
                if (e instanceof ItemEntity item && item.getItem().getItem() instanceof SoulForgedItem && e.distanceToSqr(player) < 32 * 32) {
                    level.addParticle(level.getRandom().nextInt(3) == 0 ? ParticleTypes.SOUL : ModParticles.SOUL_EMBER.get(),
                            e.getX() + (level.getRandom().nextDouble() - 0.5) * 0.4, e.getY() + 0.3 + level.getRandom().nextDouble() * 1.0,
                            e.getZ() + (level.getRandom().nextDouble() - 0.5) * 0.4, 0, 0.02 + level.getRandom().nextDouble() * 0.02, 0);
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
        // Soul Rend wind-up: the blade over the shoulder drinks in soul fire, faster and faster.
        Iterator<Int2LongMap.Entry> rends = ClientState.RENDS.int2LongEntrySet().iterator();
        while (rends.hasNext()) {
            Int2LongMap.Entry e = rends.next();
            Entity entity = level.getEntity(e.getIntKey());
            if (entity == null || ClientState.rendTicks(entity) < 0) {
                rends.remove();
                continue;
            }
            spawnRendEffects(level, entity, (int) (now - e.getLongValue()), rnd);
        }
        // Stunned creatures: sparks circling their heads.
        Iterator<Int2LongMap.Entry> stuns = ClientState.STUNNED.int2LongEntrySet().iterator();
        while (stuns.hasNext()) {
            Int2LongMap.Entry e = stuns.next();
            Entity entity = level.getEntity(e.getIntKey());
            if (entity == null || !entity.isAlive() || e.getLongValue() <= now) {
                stuns.remove();
                continue;
            }
            for (int i = 0; i < 3; i++) {
                double a = now * 0.35 + i * Math.PI * 2 / 3;
                double r = 0.3 + entity.getBbWidth() * 0.4;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, entity.getX() + Math.cos(a) * r,
                        entity.getY() + entity.getBbHeight() + 0.25, entity.getZ() + Math.sin(a) * r, 0, 0, 0);
            }
        }
    }

    /**
     * One soul drifting up from somewhere along the held blade. Third person: along the raised blade in front
     * of the body. First person: off to the right of the view, where the blade stands, so it never blocks aim.
     */
    private static void spawnHeldSouls(ClientLevel level, Player p, boolean firstPerson, RandomSource rnd) {
        Vec3 at;
        if (firstPerson) {
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            at = p.getEyePosition().add(flat.scale(0.9 + rnd.nextDouble() * 0.4)).add(right.scale(0.6 + rnd.nextDouble() * 0.2))
                    .add(0, -0.6 + rnd.nextDouble() * 1.1, 0);
        } else {
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            Vec3 hand = p.position().add(0, p.isCrouching() ? 0.85 : 1.05, 0).add(forward.scale(0.55)).add(right.scale(-0.05));
            Vec3 blade = forward.scale(0.5).add(0, 0.866, 0);
            at = hand.add(blade.scale(0.4 + rnd.nextDouble() * 1.6)).add(right.scale((rnd.nextDouble() - 0.5) * 0.3));
        }
        if (rnd.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SOUL, at.x, at.y, at.z, 0, 0.015 + rnd.nextDouble() * 0.015, 0);
        } else {
            level.addParticle(ModParticles.SOUL_EMBER.get(), at.x, at.y, at.z, 0, 0.01 + rnd.nextDouble() * 0.02, 0);
        }
    }

    private static void spawnRendEffects(ClientLevel level, Entity e, int t, RandomSource rnd) {
        float yaw = (e instanceof LivingEntity l ? l.yBodyRot : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (t >= RendTimeline.WINDUP) return;
        float k = t / (float) RendTimeline.WINDUP;
        Vec3 blade = e.position().add(0, 1.9, 0).add(right.scale(0.4)).add(forward.scale(-0.7));
        int n = 1 + (int) (k * 5);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2.0 + rnd.nextDouble() * 1.2;
            Vec3 from = blade.add(Math.cos(a) * r, (rnd.nextDouble() - 0.5) * 2.4, Math.sin(a) * r);
            Vec3 v = blade.subtract(from).scale(0.13);
            level.addParticle(ModParticles.SOUL_EMBER.get(), from.x, from.y, from.z, v.x, v.y, v.z);
        }
        // a slowly turning ring of soul fire around the feet, tightening as the strike nears
        double ringR = 1.6 - 0.6 * k;
        for (int i = 0; i < 2; i++) {
            double a = t * 0.22 + i * Math.PI;
            level.addParticle(ModParticles.SOUL_EMBER.get(), e.getX() + Math.cos(a) * ringR, e.getY() + 0.05,
                    e.getZ() + Math.sin(a) * ringR, 0, 0.01, 0);
        }
        if (t == RendTimeline.WINDUP - 20) {
            // fully charged: the blade flares
            level.addParticle(ParticleTypes.FLASH, blade.x, blade.y, blade.z, 0, 0, 0);
            for (int i = 0; i < 40; i++) {
                level.addParticle(ModParticles.SOUL_EMBER.get(), blade.x, blade.y, blade.z,
                        (rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.3) * 0.5, (rnd.nextDouble() - 0.5) * 0.5);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        SlashTrails.render(event);
        ZoneRenderer.render(event);
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
        // both hands are on the greatsword: whatever is in the off hand is put away while it renders
        hiddenOffhand = ItemStack.EMPTY;
        if (p.getMainHandItem().getItem() instanceof SoulForgedItem && !p.getOffhandItem().isEmpty()) {
            hiddenOffhand = p.getOffhandItem();
            p.getInventory().offhand.set(0, ItemStack.EMPTY);
        }
        SlamAnim.Pose pose = SoulArmPose.current(p);
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
        if (!hiddenOffhand.isEmpty()) {
            event.getEntity().getInventory().offhand.set(0, hiddenOffhand);
            hiddenOffhand = ItemStack.EMPTY;
        }
        ClientState.renderingPlayer = null;
    }

    /** Every attack swing with the sword starts the slow first-person slash (also on misses). */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof SoulForgedItem)) return;
        if (ClientState.rendTicks(mc.player) >= 0) {
            // no attacks from the Soul Rend wind-up until its recovery ends
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        float t = ClientState.slashTicks(mc.player);
        if (t < 0 || t >= FirstPersonSword.SLASH_TICKS * 0.7F) {
            ClientState.slashStart = ClientState.gameTime();
        }
    }

    /** First person: the greatsword is drawn by {@link FirstPersonSword} (held pose, slash, Soul Crush). */
    private static ItemStack hiddenOffhand = ItemStack.EMPTY;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() == InteractionHand.OFF_HAND && mc.player != null
                && mc.player.getMainHandItem().getItem() instanceof SoulForgedItem) {
            event.setCanceled(true);   // the off hand is on the greatsword's grip
            return;
        }
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
