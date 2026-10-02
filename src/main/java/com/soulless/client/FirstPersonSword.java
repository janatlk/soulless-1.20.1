package com.soulless.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulless.Soulless;
import com.soulless.dev.DevAutoTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * First-person rendering of the greatsword. At rest it stands upright on the right with the cutting edge
 * facing away, the point tipped ~10 degrees back toward the shoulder and the hilt below the screen.
 * Attacks reuse the vanilla sword swing, slowed for a heavy blade (quick strike, slow return); Soul Crush
 * lifts it and chops it down; Soul Rend hauls it over the shoulder and cuts diagonally across the screen.
 * The item model's first-person display is identity.
 * In dev (SOULLESS_AUTOTEST) the numbers are re-read from run/fp_tune.properties for live tuning.
 */
public final class FirstPersonSword {
    private static final Properties P = new Properties();
    private static long lastLoad;

    /** Swing timing in ticks: the strike is quick, the heavy blade comes back slowly. */
    public static final int SWING_STRIKE = 5, SWING_RECOVER = 11;
    public static final int SLASH_TICKS = SWING_STRIKE + SWING_RECOVER;

    static {
        // rest pose: grip position in camera space; tilt back (rx) and sideways (rz) in camera space,
        // then spin about the blade (ry) so the edge faces forward; model scale
        def("tx", 0.62); def("ty", -1.2); def("tz", -1.0);
        def("rx", 10); def("rz", -4); def("ry", 90); def("scale", 0.48);
        // the vanilla sword swing (ItemInHandRenderer), scaled: >1 exaggerates, <1 calms it
        def("swing_amp", 0.9);
        // Soul Crush key poses: rotation about the camera X and Z axes at the grip + shift (kept subtle)
        def("low_rx", -8); def("low_rz", 4); def("low_x", 0.02); def("low_y", -0.08);
        def("raised_rx", 14); def("raised_rz", -6); def("raised_x", -0.06); def("raised_y", 0.22);
        def("struck_rx", -70); def("struck_rz", -12); def("struck_x", -0.2); def("struck_y", 0.1);
        // Soul Rend key poses (same layout): hauled over the right shoulder, end of the diagonal cut, dragging low
        def("shoulder_rx", 70); def("shoulder_rz", -30); def("shoulder_x", 0.12); def("shoulder_y", 0.4);
        def("cut_rx", -35); def("cut_rz", 115); def("cut_x", -0.8); def("cut_y", 0.35);
        def("low_drag_rx", -45); def("low_drag_rz", 30); def("low_drag_x", -0.2); def("low_drag_y", -0.15);
    }

    private static void def(String k, double v) {
        P.setProperty(k, Double.toString(v));
    }

    private static float f(String k) {
        return Float.parseFloat(P.getProperty(k));
    }

    /** {rx, rz, x, y} of a Soul Crush key pose. */
    private static float[] key(String name) {
        return new float[]{f(name + "_rx"), f(name + "_rz"), f(name + "_x"), f(name + "_y")};
    }

    private static void maybeReload() {
        if (!DevAutoTest.ENABLED || System.currentTimeMillis() - lastLoad < 1000) return;
        lastLoad = System.currentTimeMillis();
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("fp_tune.properties");
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Properties loaded = new Properties();
            loaded.load(r);
            P.putAll(loaded);
        } catch (Exception e) {
            Soulless.LOGGER.warn("fp_tune.properties: {}", e.toString());
        }
    }

    public static void render(RenderHandEvent event, LocalPlayer player, ItemStack stack) {
        maybeReload();
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();

        float equip = event.getEquipProgress();
        ps.translate(f("tx"), f("ty") - equip * 0.6F, f("tz"));

        float[] rend = RendAnim.firstPersonKeys(player);
        float[] slam = rend != null ? rend : SlamAnim.firstPersonKeys(player);
        if (slam != null) {
            float[][] keys = {new float[4], key("low"), key("raised"), key("struck"), key("shoulder"), key("cut"), key("low_drag")};
            float[] a = keys[(int) slam[0]], b = keys[(int) slam[1]];
            float k = slam[2];
            ps.translate(Mth.lerp(k, a[2], b[2]), Mth.lerp(k, a[3], b[3]), 0);
            ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(k, a[0], b[0])));
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(k, a[1], b[1])));
        } else {
            float p = swingProgress(ClientState.slashTicks(player));
            if (p > 0) applyVanillaSwing(ps, p, f("swing_amp"));
        }

        ps.mulPose(Axis.XP.rotationDegrees(f("rx")));
        ps.mulPose(Axis.ZP.rotationDegrees(f("rz")));
        ps.mulPose(Axis.YP.rotationDegrees(f("ry")));
        float sc = f("scale");
        ps.scale(sc, sc, sc);
        mc.gameRenderer.itemInHandRenderer.renderItem(player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                false, ps, event.getMultiBufferSource(), event.getPackedLight());
        ps.popPose();
    }

    /**
     * Maps ticks into the attack to vanilla's 0..1 swing progress: the first half (the strike) plays in
     * {@link #SWING_STRIKE} ticks, the second half (the return) in {@link #SWING_RECOVER}. 0 when idle.
     */
    private static float swingProgress(float t) {
        if (t < 0 || t >= SLASH_TICKS) return 0;
        return t < SWING_STRIKE ? 0.5F * t / SWING_STRIKE : 0.5F + 0.5F * (t - SWING_STRIKE) / SWING_RECOVER;
    }

    /** The vanilla first-person sword swing (ItemInHandRenderer#renderArmWithItem + applyItemArmAttackTransform). */
    private static void applyVanillaSwing(PoseStack ps, float p, float amp) {
        float sqrtSin = Mth.sin(Mth.sqrt(p) * Mth.PI);
        ps.translate(-0.4F * sqrtSin * amp, 0.2F * Mth.sin(Mth.sqrt(p) * Mth.TWO_PI) * amp, -0.2F * Mth.sin(p * Mth.PI) * amp);
        float sq = Mth.sin(p * p * Mth.PI);
        ps.mulPose(Axis.YP.rotationDegrees(45.0F + sq * -20.0F * amp));
        ps.mulPose(Axis.ZP.rotationDegrees(sqrtSin * -20.0F * amp));
        ps.mulPose(Axis.XP.rotationDegrees(sqrtSin * -80.0F * amp));
        ps.mulPose(Axis.YP.rotationDegrees(-45.0F));
    }

    private FirstPersonSword() {}
}
