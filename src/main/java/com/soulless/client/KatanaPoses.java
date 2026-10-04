package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.dev.DevAutoTest;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Key poses and timelines of the katana stance (jodan-no-kamae) and its two cuts, shared by the third-person
 * player animation ({@link KatanaAnim}) and the first-person view ({@link KatanaFirstPerson}).
 * <p>
 * A pose is a set of fields, each a few numbers: rotations in degrees (x, y, z; model space, x- raises an arm
 * forward), bends as (axis, angle) in degrees, positions in pixels. Fields:
 * rArm/lArm, rBend/lBend (elbows), torso, waist (bend at the waist, carries the arms and head), body (whole body),
 * rLeg/lLeg (added to the walking legs), rLegBend/lLegBend (knees), item/itemPos (blade in the hand), and
 * fp = tx, ty, tz, rx, ry, rz for the first-person view.
 * <p>
 * A timeline lists key poses with their time in ticks and the easing into each key:
 * {@code guard@0 kesa_end@3:in kesa_end@5 guard@12:inout}.
 * In dev (SOULLESS_AUTOTEST) everything is re-read from run/katana_tune.properties for live tuning.
 */
public final class KatanaPoses {
    private static final Properties P = new Properties();
    private static long lastLoad;
    private static final Map<String, float[]> CACHE = new HashMap<>();
    private static final Map<String, List<Key>> TIMELINES = new HashMap<>();

    public static final String GUARD = "guard", KESA = "kesa", GYAKU = "gyaku", TSUKI = "tsuki";

    static {
        // jodan-no-kamae: both hands on the grip above the forehead, the blade raised back over the head;
        // left foot forward, knees a little bent
        def("guard.rArm", "-150 0 14");
        def("guard.lArm", "-138 0 -14");
        def("guard.rBend", "0 -22");
        def("guard.lBend", "0 -28");
        def("guard.torso", "0 -8 0");
        def("guard.rLeg", "8 0 5");
        def("guard.lLeg", "-28 0 -5");
        def("guard.rLegBend", "0 30");
        def("guard.lLegBend", "0 38");
        def("guard.item", "-40 0 0");
        def("guard.fp", "0.62 -1.0 -1.0 10 90 -4");

        // kesa-giri: from over the right shoulder down across to the left hip
        def("kesa_mid.rArm", "-120 -15 10");
        def("kesa_mid.lArm", "-110 -10 -10");
        def("kesa_mid.rBend", "0 -20");
        def("kesa_mid.lBend", "0 -20");
        def("kesa_mid.torso", "0 -15 0");
        def("kesa_mid.waist", "0 8");
        def("kesa_mid.item", "-30 0 0");
        def("kesa_mid.fp", "0.55 -0.55 -1.2 10 90 -45");
        def("kesa_end.rArm", "-45 -30 0");
        def("kesa_end.lArm", "-40 -20 0");
        def("kesa_end.rBend", "0 -8");
        def("kesa_end.lBend", "0 -15");
        def("kesa_end.torso", "0 -30 0");
        def("kesa_end.waist", "0 15");
        def("kesa_end.rLeg", "12 0 5");
        def("kesa_end.lLeg", "-36 0 -5");
        def("kesa_end.rLegBend", "0 34");
        def("kesa_end.lLegBend", "0 48");
        def("kesa_end.item", "-60 0 0");
        def("kesa_end.fp", "-0.05 -0.75 -1.3 -10 90 120");

        // gyaku-kesa: the blade swings over to the left shoulder, then cuts down across to the right hip
        def("gyaku_wind.rArm", "-160 35 -20");
        def("gyaku_wind.lArm", "-150 40 -40");
        def("gyaku_wind.rBend", "0 -40");
        def("gyaku_wind.lBend", "0 -40");
        def("gyaku_wind.torso", "0 28 0");
        def("gyaku_wind.rLeg", "8 0 5");
        def("gyaku_wind.lLeg", "-28 0 -5");
        def("gyaku_wind.rLegBend", "0 30");
        def("gyaku_wind.lLegBend", "0 38");
        def("gyaku_wind.item", "-35 0 0");
        def("gyaku_wind.fp", "-0.25 -0.85 -1.1 15 90 30");
        def("gyaku_end.rArm", "-35 30 0");
        def("gyaku_end.lArm", "-40 38 0");
        def("gyaku_end.rBend", "0 -15");
        def("gyaku_end.lBend", "0 -8");
        def("gyaku_end.torso", "0 25 0");
        def("gyaku_end.waist", "0 15");
        def("gyaku_end.rLeg", "12 0 5");
        def("gyaku_end.lLeg", "-36 0 -5");
        def("gyaku_end.rLegBend", "0 34");
        def("gyaku_end.lLegBend", "0 48");
        def("gyaku_end.item", "-80 0 0");
        def("gyaku_end.fp", "0.45 -0.75 -1.3 -10 90 -120");

        // tsuki (queued during gyaku): instead of jodan the blade is drawn in to the right hip, both hands on the
        // grip at the belt, the point aimed at the opponent (wind); the body coils - knees deep, hips low and back
        // (load); then the lunge explodes out with the dash - the front foot steps far out, the back leg straightens,
        // the hips drop and drive forward while the hands stay at the hip (end)
        def("tsuki_load.rArm", "-15 -15 0");
        def("tsuki_load.lArm", "-30 30 0");
        def("tsuki_load.rBend", "0 -70");
        def("tsuki_load.lBend", "0 -70");
        def("tsuki_load.torso", "0 -28 0");
        def("tsuki_load.waist", "0 10");
        def("tsuki_load.rLeg", "18 0 8");
        def("tsuki_load.lLeg", "-42 0 -8");
        def("tsuki_load.rLegBend", "0 60");
        def("tsuki_load.lLegBend", "0 75");
        def("tsuki_load.bodyPos", "0 -3.5 -1.5");
        def("tsuki_load.item", "-60 0 0");
        def("tsuki_load.fp", "0.5 -0.75 -0.95 -72 90 14");
        def("kesa_wind.fp", "0.7 -0.85 -1.1 15 90 -18");
        def("kesa_follow.fp", "-0.35 -0.9 -1.3 -10 90 140");
        def("gyaku_mid.fp", "-0.15 -0.55 -1.2 10 90 45");
        def("gyaku_follow.fp", "0.7 -0.9 -1.3 -10 90 -140");
        def("tsuki_wind.rArm", "-20 -15 0");
        def("tsuki_wind.lArm", "-35 30 0");
        def("tsuki_wind.rBend", "0 -60");
        def("tsuki_wind.lBend", "0 -60");
        def("tsuki_wind.torso", "0 -20 0");
        def("tsuki_wind.waist", "0 5");
        def("tsuki_wind.rLeg", "10 0 6");
        def("tsuki_wind.lLeg", "-30 0 -6");
        def("tsuki_wind.rLegBend", "0 40");
        def("tsuki_wind.lLegBend", "0 50");
        def("tsuki_wind.item", "-62 0 0");
        def("tsuki_wind.fp", "0.45 -0.7 -1.1 -75 90 12");
        def("tsuki_wind.bodyPos", "0 -1.5 0");
        def("tsuki_end.rArm", "-50 -8 0");
        def("tsuki_end.lArm", "-58 22 0");
        def("tsuki_end.rBend", "0 -30");
        def("tsuki_end.lBend", "0 -30");
        def("tsuki_end.torso", "0 -10 0");
        def("tsuki_end.waist", "0 28");
        def("tsuki_end.rLeg", "55 0 6");
        def("tsuki_end.lLeg", "-75 0 -6");
        def("tsuki_end.rLegBend", "0 0");
        def("tsuki_end.lLegBend", "0 80");
        def("tsuki_end.bodyPos", "0 -4 4");
        def("tsuki_end.item", "-55 0 0");
        def("tsuki_end.fp", "0.3 -0.55 -1.4 -80 90 10");

        def("kesa.keys", "guard@0 kesa_wind@3:out kesa_mid@5:in kesa_end@8:lin kesa_follow@11:out kesa_follow@13 guard@20:inout");
        def("gyaku.keys", "guard@0 gyaku_wind@4:out gyaku_mid@6:in gyaku_end@9:lin gyaku_follow@12:out gyaku_follow@14 guard@21:inout");
        def("tsuki.keys", "tsuki_wind@0 tsuki_load@6:inout tsuki_load@8 tsuki_end@9.5:out tsuki_end@13 guard@22:inout");
        // the next attack is only allowed (and starts the next cut) once this much of the current one has played
        def("kesa.chain", "12");
        def("gyaku.chain", "13");
        def("tsuki.chain", "18");
        // the thrust takes over once gyaku has followed through; the lunge (dash) and its hit, in thrust ticks
        def("tsuki.from", "14");
        def("tsuki.dash", "8");
        def("tsuki.hit", "9");
        // cross-fade into each cut (ticks): long for the thrust, which starts from wherever gyaku left the blade
        def("kesa.fade", "2");
        def("gyaku.fade", "2");
        def("tsuki.fade", "5");
        def("fp.scale", "0.45");
    }

    private static void def(String k, String v) {
        P.setProperty(k, v);
    }

    /** Dev live tuning: re-reads run/katana_tune.properties at most once a second. */
    public static void maybeReload() {
        if (!DevAutoTest.ENABLED || System.currentTimeMillis() - lastLoad < 1000) return;
        lastLoad = System.currentTimeMillis();
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("katana_tune.properties");
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Properties loaded = new Properties();
            loaded.load(r);
            P.putAll(loaded);
            CACHE.clear();
            TIMELINES.clear();
        } catch (Exception e) {
            Soulless.LOGGER.warn("katana_tune.properties: {}", e.toString());
        }
    }

    /** A pose field's numbers (zeros when the pose leaves the field out). */
    public static float[] field(String pose, String field, int size) {
        String k = pose + "." + field;
        float[] cached = CACHE.get(k);
        if (cached != null) return cached;
        float[] out = new float[size];
        String s = P.getProperty(k);
        if (s != null) {
            String[] parts = s.trim().split("[\\s,]+");
            for (int i = 0; i < Math.min(size, parts.length); i++) out[i] = Float.parseFloat(parts[i]);
        } else if (!pose.equals(GUARD) && (field.endsWith("Leg") || field.endsWith("LegBend"))) {
            out = field(GUARD, field, size);   // a pose that leaves the legs out keeps the guard's footing
        }
        CACHE.put(k, out);
        return out;
    }

    public static float number(String key) {
        return Float.parseFloat(P.getProperty(key, "0").trim());
    }

    private record Key(String pose, float time, String ease) {}

    private static List<Key> timeline(String name) {
        return TIMELINES.computeIfAbsent(name, n -> {
            List<Key> keys = new ArrayList<>();
            for (String tok : P.getProperty(n + ".keys", GUARD + "@0").trim().split("\\s+")) {
                String[] at = tok.split("@");
                String[] te = at[1].split(":");
                keys.add(new Key(at[0], Float.parseFloat(te[0]), te.length > 1 ? te[1] : "smooth"));
            }
            return keys;
        });
    }

    /** Length of a timeline in ticks. */
    public static float length(String name) {
        List<Key> keys = timeline(name);
        return keys.get(keys.size() - 1).time;
    }

    /**
     * The field sampled from a timeline at {@code t} ticks (null timeline: the guard). Writes into {@code out}.
     */
    public static float[] sample(String name, float t, String field, float[] out) {
        if (name == null) {
            System.arraycopy(field(GUARD, field, out.length), 0, out, 0, out.length);
            return out;
        }
        List<Key> keys = timeline(name);
        Key a = keys.get(0), b = a;
        for (int i = 1; i < keys.size(); i++) {
            b = keys.get(i);
            if (t <= b.time) break;
            a = b;
        }
        float k = b.time > a.time ? Mth.clamp((t - a.time) / (b.time - a.time), 0, 1) : 1;
        k = ease(b.ease, k);
        float[] pa = field(a.pose, field, out.length), pb = field(b.pose, field, out.length);
        for (int i = 0; i < out.length; i++) out[i] = Mth.lerp(k, pa[i], pb[i]);
        return out;
    }

    private static float ease(String name, float k) {
        return switch (name) {
            case "lin" -> k;
            case "in" -> k * k * k;                                     // a cut: slow start, fastest at the end
            case "out" -> 1 - (1 - k) * (1 - k) * (1 - k);
            case "inout" -> k < 0.5F ? 4 * k * k * k : 1 - (float) Math.pow(-2 * k + 2, 3) / 2;
            default -> k * k * (3 - 2 * k);
        };
    }

    private KatanaPoses() {}
}
