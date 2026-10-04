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

    public static final String GUARD = "guard", KESA = "kesa", GYAKU = "gyaku";

    static {
        // jodan-no-kamae: both hands on the grip above the forehead, the blade raised back over the head;
        // left foot forward, knees a little bent
        def("guard.rArm", "-172 0 22");
        def("guard.lArm", "-158 0 -22");
        def("guard.rBend", "0 -22");
        def("guard.lBend", "0 -28");
        def("guard.torso", "0 -8 0");
        def("guard.rLeg", "8 0 5");
        def("guard.lLeg", "-28 0 -5");
        def("guard.rLegBend", "0 30");
        def("guard.lLegBend", "0 38");
        def("guard.item", "-80 0 0");
        def("guard.fp", "0.55 -0.55 -1.0 35 60 32");

        // kesa-giri: from over the right shoulder down across to the left hip
        def("kesa_mid.rArm", "-120 -15 10");
        def("kesa_mid.lArm", "-110 -10 -10");
        def("kesa_mid.rBend", "0 -20");
        def("kesa_mid.lBend", "0 -20");
        def("kesa_mid.torso", "0 -15 0");
        def("kesa_mid.waist", "0 8");
        def("kesa_mid.item", "-30 0 0");
        def("kesa_mid.fp", "0.45 -0.2 -1.0 30 60 -25");
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
        def("kesa_end.fp", "-0.3 -0.42 -1.0 -10 60 110");

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
        def("gyaku_wind.item", "-60 0 0");
        def("gyaku_wind.fp", "-0.1 -0.25 -1.0 35 60 35");
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
        def("gyaku_end.fp", "0.55 -0.45 -1.0 -10 60 -110");

        def("kesa.keys", "guard@0 kesa_mid@1.5:in kesa_end@3 kesa_end@5 guard@12:inout");
        def("gyaku.keys", "guard@0 gyaku_wind@2:out gyaku_end@5:in gyaku_end@7 guard@14:inout");
        // the next attack can start a new cut once this much of the current one has played
        def("kesa.chain", "4");
        def("gyaku.chain", "6");
        def("fp.scale", "0.3");
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
