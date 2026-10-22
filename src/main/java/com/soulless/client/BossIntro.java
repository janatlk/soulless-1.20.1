package com.soulless.client;

import com.soulless.Soulless;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A boss's freeze-frame intro (network.BossIntroPacket): the camera swings onto the boss - frozen mid-motion on the
 * server - and zooms in at a Dutch angle; the picture flashes, cools and darkens at the edges, letterbox bars close
 * in and the title card - the boss's name and a line under it - slides across. The player cannot move meanwhile.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class BossIntro {
    private static final int IN = 10, OUT = 12;
    private static final float ROLL = -16, ZOOM = 0.2F;

    private static int id = -1, length;
    /** A client-only stand the camera rides during the shot: from the player's eyes out to a point before the boss. */
    private static net.minecraft.world.entity.decoration.ArmorStand cam;
    private static Vec3 from, to;
    private static long startedAt;
    private static String key = "";

    public static void start(int entityId, int ticks, String nameKey) {
        id = entityId;
        length = ticks;
        key = nameKey;
        startedAt = ClientState.gameTime();
        var mc = Minecraft.getInstance();
        Entity b = boss();
        if (mc.player != null && b != null) {
            // the shot: in front of the boss on the player's side, a little below its head, a fair way off
            Vec3 toPlayer = mc.player.position().subtract(b.position()).multiply(1, 0, 1);
            Vec3 dir = toPlayer.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toPlayer.normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            from = mc.player.getEyePosition();
            to = b.position().add(dir.scale(9)).add(side.scale(2.5)).add(0, b.getBbHeight() * 0.45, 0);
            cam = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, mc.level);
            cam.setInvisible(true);
            cam.setNoGravity(true);
            cam.moveTo(from.x, from.y - cam.getEyeHeight(), from.z, mc.player.getYRot(), mc.player.getXRot());
            mc.level.putNonPlayerEntity(-778_001, cam);
            mc.setCameraEntity(cam);
        }
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5F, 0.4F);
    }

    private static float time(float partial) {
        return ClientState.gameTime() - startedAt + partial;
    }

    public static boolean active() {
        return id >= 0 && ClientState.gameTime() - startedAt < length;
    }

    /** 0..1: how far into the shot (eased in, eased out). */
    private static float blend(float partial) {
        float t = time(partial);
        float in = Mth.clamp(t / IN, 0, 1), out = Mth.clamp((length - t) / OUT, 0, 1);
        float b = Math.min(in, out);
        return b * b * (3 - 2 * b);
    }

    private static Entity boss() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getEntity(id);
    }

    /** Moves the camera stand along the shot each frame, and gives the view back when it ends. */
    @SubscribeEvent
    public static void onRender(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START || cam == null) return;
        var mc = Minecraft.getInstance();
        if (!active() || mc.level == null || mc.player == null) {
            if (mc.getCameraEntity() == cam && mc.player != null) mc.setCameraEntity(mc.player);
            if (mc.level != null) mc.level.removeEntity(cam.getId(), Entity.RemovalReason.DISCARDED);
            cam = null;
            return;
        }
        float k = blend(event.renderTickTime);
        Vec3 at = from.lerp(to, k);
        cam.setPos(at.x, at.y - cam.getEyeHeight(), at.z);
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();
        cam.xOld = cam.getX();
        cam.yOld = cam.getY();
        cam.zOld = cam.getZ();
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active()) return;
        Entity b = boss();
        if (b == null) return;
        float k = blend((float) event.getPartialTick());
        Vec3 eye = event.getCamera().getPosition();
        Vec3 head = b.position().add(0, b.getBbHeight() * 0.72, 0);
        Vec3 d = head.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        event.setYaw(Mth.rotLerp(k, event.getYaw(), yaw));
        event.setPitch(Mth.lerp(k, event.getPitch(), pitch));
        event.setRoll(ROLL * k);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!active()) return;
        event.setFOV(event.getFOV() * (1 - ZOOM * blend((float) event.getPartialTick())));
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }

    /** Nothing of the HUD shows during the shot but the card. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHud(RenderGuiOverlayEvent.Pre event) {
        if (active() && event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCard(RenderGuiOverlayEvent.Post event) {
        if (!active() || event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;
        GuiGraphics g = event.getGuiGraphics();
        var mc = Minecraft.getInstance();
        float partial = event.getPartialTick();
        float t = time(partial), k = blend(partial);
        int w = g.guiWidth(), h = g.guiHeight();
        // the frozen frame: a cold, darker picture with heavy edges
        g.fill(0, 0, w, h, alpha(0x1A2A38, 0.35F * k));
        for (int i = 0; i < 10; i++) {
            int a = Math.round(18 * k * (10 - i) / 10F);
            g.fill(0, i * h / 40, w, i * h / 40 + h / 40, a << 24);
            g.fill(0, h - (i + 1) * h / 40, w, h - i * h / 40, a << 24);
        }
        // the flash at the moment it freezes
        if (t < 6) g.fill(0, 0, w, h, alpha(0xFFFFFF, 0.55F * (1 - t / 6)));
        // letterbox
        int bar = Math.round(h * 0.13F * k);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // the card slides in from the left, a soul-blue rule under the name
        float slide = Mth.clamp((t - 6) / 10, 0, 1);
        slide = 1 - (1 - slide) * (1 - slide) * (1 - slide);
        float fade = Mth.clamp((length - t) / OUT, 0, 1);
        if (slide <= 0) return;
        Component name = Component.translatable(key + ".title");
        Component sub = Component.translatable(key + ".subtitle");
        int x = Math.round(-200 + (w * 0.12F + 200) * slide);
        int y = Math.round(h * 0.62F);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(3.2F, 3.2F, 1);
        g.drawString(mc.font, name, 0, 0, alpha(0xF2F6FF, fade), true);
        pose.popPose();
        int lineW = Math.round((mc.font.width(name) * 3.2F + 30) * Mth.clamp((t - 12) / 10, 0, 1));
        g.fill(x, y + 34, x + lineW, y + 36, alpha(0x6FE6F5, fade));
        float subA = Mth.clamp((t - 18) / 8, 0, 1) * fade;
        if (subA > 0.02F) {
            pose.pushPose();
            pose.translate(x + 2, y + 42, 0);
            pose.scale(1.4F, 1.4F, 1);
            g.drawString(mc.font, sub.copy().withStyle(net.minecraft.ChatFormatting.ITALIC), 0, 0, alpha(0xB8C8D0, subA), true);
            pose.popPose();
        }
    }

    private static int alpha(int rgb, float a) {
        return (Math.round(Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private BossIntro() {}
}
