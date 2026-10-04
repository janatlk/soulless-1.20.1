package com.soulless.client;

import com.soulless.Soulless;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Kitsune ritual as the player sees it: they cannot move while kneeling, the first-person view bows to look
 * down at the blade, the screen flashes red as it goes in, and blue fox fire bursts from the wound (on every
 * client). The animation itself is KatanaAnim's "ritual"; the cost and the blessing are server-side
 * (item.KitsuneRitual).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class KatanaRitualFx {
    /** Game time the local player's blade went in (for the red flash). */
    private static float stabTime = -1000;
    private static final float FLASH_TICKS = 14;
    /** How far the view bows at the deepest point of the ritual (degrees). */
    private static final float BOW = 38;

    /** The blade goes in on this player's model. */
    public static void stab(AbstractClientPlayer player) {
        if (player.isLocalPlayer()) stabTime = ClientState.gameTime();
        var level = player.level();
        // a ring of fox fire bursting outward from the wound (kept low and away from the player's own eyes)
        for (int i = 0; i < 18; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, r = 0.55 + level.random.nextDouble() * 0.25;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, player.getX() + Math.cos(a) * r, player.getY() + 0.3 + level.random.nextDouble() * 0.4,
                    player.getZ() + Math.sin(a) * r, Math.cos(a) * 0.06, 0.02 + level.random.nextDouble() * 0.04, Math.sin(a) * 0.06);
        }
    }

    private static KatanaAnim ritual() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        KatanaAnim anim = KatanaAnim.of(p);
        return anim != null && anim.inRitual() ? anim : null;
    }

    /** Kneeling: no walking, no jumping. Parrying: no jumping. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        KatanaAnim a = p != null ? KatanaAnim.of(p) : null;
        if (a != null && a.inParry()) event.getInput().jumping = false;
        if (ritual() == null) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** First person: the view bows down to the blade, then lifts as the blade comes out. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        KatanaAnim anim = ritual();
        Minecraft mc = Minecraft.getInstance();
        if (anim == null || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != mc.player) return;
        float t = anim.cutTime((float) event.getPartialTick());
        float total = KatanaPoses.length(KatanaPoses.RITUAL);
        // down over the kneel and the turn of the blade, held through the stab, up again at the end
        float k = t < 12 ? Mth.clamp(t / 12, 0, 1) : t < 32 ? 1 : Mth.clamp(1 - (t - 32) / (total - 32), 0, 1);
        k = k * k * (3 - 2 * k);
        event.setPitch(Mth.lerp(k, event.getPitch(), Math.max(event.getPitch(), BOW)));
    }

    /** While parrying: a soft white glow around the edges of the screen, strongest while the window is open. */
    private static void parryGlow(RenderGuiEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        KatanaAnim anim = p != null ? KatanaAnim.of(p) : null;
        if (anim == null || !anim.inParry()) return;
        float t = anim.cutTime(event.getPartialTick());
        float total = KatanaPoses.length(anim.parried() ? KatanaPoses.PARRIED : KatanaPoses.PARRY);
        float k = anim.parried() ? 1 - t / total : Mth.clamp(t / 2, 0, 1) * Mth.clamp((total - t) / 6, 0, 1);
        if (k <= 0) return;
        var g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        int depth = Math.max(12, Math.min(w, h) / 7);
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            // nested bands: opaque-ish at the very edge, fading toward the middle
            float f = 1 - i / (float) steps;
            int a = (int) (k * 110 * f * f);
            if (a <= 0) continue;
            int c = a << 24 | 0xF4F8FF;
            int d0 = depth * i / steps, d1 = depth * (i + 1) / steps;
            g.fill(d0, d0, w - d0, d1, c);
            g.fill(d0, h - d1, w - d0, h - d0, c);
            g.fill(d0, d1, d1, h - d1, c);
            g.fill(w - d1, d1, w - d0, h - d1, c);
        }
    }

    /** The red flash as the blade goes in, fading out. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        parryGlow(event);
        float age = ClientState.gameTime() + event.getPartialTick() - stabTime;
        if (age < 0 || age > FLASH_TICKS) return;
        float a = (1 - age / FLASH_TICKS);
        a = a * a * 0.55F;
        int alpha = (int) (a * 255);
        var g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        g.fill(0, 0, w, h, alpha << 24 | 0x9A0010);
        // a darker rim, like a vignette
        int rim = Math.max(8, w / 10);
        int rimAlpha = Math.min(255, (int) (alpha * 1.3F)) << 24 | 0x400008;
        g.fill(0, 0, w, rim / 2, rimAlpha);
        g.fill(0, h - rim / 2, w, h, rimAlpha);
        g.fill(0, 0, rim / 2, h, rimAlpha);
        g.fill(w - rim / 2, 0, w, h, rimAlpha);
    }

    private KatanaRitualFx() {}
}
