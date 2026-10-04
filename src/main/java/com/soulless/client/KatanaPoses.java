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

    public static final String GUARD = "guard", KESA = "kesa", GYAKU = "gyaku", TSUKI = "tsuki", RITUAL = "ritual",
            PARRY = "parry", PARRIED = "parried";

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

        // the wind-ups and follow-throughs of the two cuts
        def("kesa_wind.rArm", "-153 5 16");
        def("kesa_wind.lArm", "-150 5 -12");
        def("kesa_wind.rBend", "0 -25");
        def("kesa_wind.lBend", "0 -30");
        def("kesa_wind.torso", "0 6 0");
        def("kesa_wind.item", "-32 0 0");
        def("kesa_follow.rArm", "-25 -45 0");
        def("kesa_follow.lArm", "-20 -35 0");
        def("kesa_follow.rBend", "0 -5");
        def("kesa_follow.lBend", "0 -10");
        def("kesa_follow.torso", "0 -40 0");
        def("kesa_follow.waist", "0 20");
        def("kesa_follow.rLeg", "14 0 5");
        def("kesa_follow.lLeg", "-40 0 -5");
        def("kesa_follow.rLegBend", "0 34");
        def("kesa_follow.lLegBend", "0 52");
        def("kesa_follow.item", "-30 0 0");
        def("gyaku_mid.rArm", "-118 22 -8");
        def("gyaku_mid.lArm", "-110 28 -14");
        def("gyaku_mid.rBend", "0 -18");
        def("gyaku_mid.lBend", "0 -18");
        def("gyaku_mid.torso", "0 12 0");
        def("gyaku_mid.waist", "0 8");
        def("gyaku_mid.item", "-30 0 0");
        def("gyaku_follow.rArm", "-25 45 0");
        def("gyaku_follow.lArm", "-30 52 0");
        def("gyaku_follow.rBend", "0 -10");
        def("gyaku_follow.lBend", "0 -5");
        def("gyaku_follow.torso", "0 35 0");
        def("gyaku_follow.waist", "0 20");
        def("gyaku_follow.rLeg", "14 0 5");
        def("gyaku_follow.lLeg", "-40 0 -5");
        def("gyaku_follow.rLegBend", "0 34");
        def("gyaku_follow.lLegBend", "0 52");
        def("gyaku_follow.item", "-40 0 0");

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

        // the Kitsune ritual: kneel on the right knee and turn the blade around (reverse grip, the point toward
        // the belly), raise it, drive it in - straight through, the point out of the back - bow over it, draw it out
        // and rise back into jodan. Kneeling: the back thigh upright and its shin flat on the ground, the front
        // thigh level and its shin upright, the hips lowered by a shin's length
        def("r_kneel.rArm", "-95 -10 10");
        def("r_kneel.lArm", "-90 10 -10");
        def("r_kneel.rBend", "0 -45");
        def("r_kneel.lBend", "0 -45");
        def("r_kneel.rLeg", "0 0 4");
        def("r_kneel.lLeg", "-85 0 -4");
        def("r_kneel.rLegBend", "0 90");
        def("r_kneel.lLegBend", "0 85");
        def("r_kneel.bodyPos", "0 -6 0");
        def("r_kneel.item", "150 0 0");
        def("r_kneel.head", "10 0 0");
        def("r_raise.rArm", "-140 -5 8");
        def("r_raise.lArm", "-135 5 -8");
        def("r_raise.rBend", "0 -35");
        def("r_raise.lBend", "0 -35");
        def("r_raise.rLeg", "0 0 4");
        def("r_raise.lLeg", "-85 0 -4");
        def("r_raise.rLegBend", "0 90");
        def("r_raise.lLegBend", "0 85");
        def("r_raise.bodyPos", "0 -6 0");
        def("r_raise.item", "155 0 0");
        def("r_raise.waist", "0 -6");
        def("r_raise.head", "-5 0 0");
        def("r_stab.rArm", "-45 -5 10");
        def("r_stab.lArm", "-40 5 -10");
        def("r_stab.rBend", "0 -75");
        def("r_stab.lBend", "0 -75");
        def("r_stab.rLeg", "0 0 4");
        def("r_stab.lLeg", "-85 0 -4");
        def("r_stab.rLegBend", "0 90");
        def("r_stab.lLegBend", "0 85");
        def("r_stab.bodyPos", "0 -6 0");
        def("r_stab.item", "100 0 0");
        def("r_stab.waist", "0 18");
        def("r_stab.head", "25 0 0");
        def("r_hold.rArm", "-40 -5 10");
        def("r_hold.lArm", "-35 5 -10");
        def("r_hold.rBend", "0 -80");
        def("r_hold.lBend", "0 -80");
        def("r_hold.rLeg", "0 0 4");
        def("r_hold.lLeg", "-85 0 -4");
        def("r_hold.rLegBend", "0 90");
        def("r_hold.lLegBend", "0 85");
        def("r_hold.bodyPos", "0 -6.5 0");
        def("r_hold.item", "100 0 0");
        def("r_hold.waist", "0 30");
        def("r_hold.head", "35 0 0");
        def("r_pull.rArm", "-80 -10 10");
        def("r_pull.lArm", "-75 10 -10");
        def("r_pull.rBend", "0 -40");
        def("r_pull.lBend", "0 -40");
        def("r_pull.rLeg", "0 0 4");
        def("r_pull.lLeg", "-85 0 -4");
        def("r_pull.rLegBend", "0 90");
        def("r_pull.lLegBend", "0 85");
        def("r_pull.bodyPos", "0 -6 0");
        def("r_pull.item", "100 0 0");
        def("r_pull.waist", "0 10");
        def("r_pull.head", "10 0 0");
        // first person: the view bows (KatanaRitualFx); the blade turns point-down toward the belly, rises, plunges
        // toward the camera, is held, drawn out, and rises back to the guard
        def("fr_turn.fp", "0.1 -0.3 -0.95 -45 90 180");
        def("fr_raise.fp", "0.1 -0.1 -1.0 -35 90 180");
        def("fr_stab.fp", "0.1 -0.6 -0.45 -62 90 180");
        def("fr_hold.fp", "0.1 -0.65 -0.42 -64 90 180");
        def("fr_pull.fp", "0.2 -0.55 -0.85 -45 90 180");
        def("ritual.keys", "guard@0 r_kneel@8:inout r_raise@15:inout r_raise@17 r_stab@19:in r_hold@30 r_pull@37:inout guard@46:inout");
        def("ritual.fpkeys", "guard@0 fr_turn@8:inout fr_raise@15:inout fr_raise@17 fr_stab@19:in fr_hold@30 fr_pull@37:inout guard@46:inout");
        def("ritual.stab", "19");
        def("ritual.chain", "46");
        def("ritual.fade", "3");

        // parry: the blade comes down from jodan and across in front of the chest, held level, edge out; on a parry
        // it is knocked back a little (recoil) and returns to jodan
        def("p_block.rArm", "-75 -25 0");
        def("p_block.lArm", "-70 35 0");
        def("p_block.rBend", "0 -50");
        def("p_block.lBend", "0 -55");
        def("p_block.torso", "0 -12 0");
        def("p_block.rLeg", "14 0 6");
        def("p_block.lLeg", "-30 0 -6");
        def("p_block.rLegBend", "0 35");
        def("p_block.lLegBend", "0 45");
        def("p_block.bodyPos", "0 -1 0");
        def("p_block.item", "-90 0 0");
        def("p_block.fp", "0.25 -0.45 -0.95 0 90 88");
        def("p_recoil.rArm", "-90 -30 0");
        def("p_recoil.lArm", "-85 40 0");
        def("p_recoil.rBend", "0 -70");
        def("p_recoil.lBend", "0 -75");
        def("p_recoil.torso", "-6 -18 0");
        def("p_recoil.rLeg", "20 0 6");
        def("p_recoil.lLeg", "-26 0 -6");
        def("p_recoil.rLegBend", "0 40");
        def("p_recoil.lLegBend", "0 45");
        def("p_recoil.bodyPos", "0 -1 -1.5");
        def("p_recoil.item", "-95 0 0");
        def("p_recoil.fp", "0.3 -0.3 -0.8 12 90 80");
        def("parry.keys", "guard@0 p_block@2.5:out p_block@9 guard@16:inout");
        def("parry.fpkeys", "guard@0 p_block@2.5:out p_block@9 guard@16:inout");
        def("parried.keys", "p_block@0 p_recoil@2:out p_block@5:inout guard@12:inout");
        def("parried.fpkeys", "p_block@0 p_recoil@2:out p_block@5:inout guard@12:inout");
        def("parry.chain", "10");
        def("parried.chain", "4");
        def("parry.fade", "1.5");
        def("parried.fade", "0.5");

        def("kesa.keys", "guard@0 kesa_wind@3:out kesa_mid@5:in kesa_end@8:lin kesa_follow@11:out kesa_follow@13 guard@20:inout");
        def("gyaku.keys", "guard@0 gyaku_wind@4:out gyaku_mid@6:in gyaku_end@9:lin gyaku_follow@12:out gyaku_follow@14 guard@21:inout");
        def("tsuki.keys", "tsuki_wind@0 tsuki_load@6:inout tsuki_load@8 tsuki_end@9.5:out tsuki_end@13 guard@22:inout");
        // the next attack is only allowed (and starts the next cut) once this much of the current one has played
        def("kesa.chain", "12");
        def("gyaku.chain", "13");
        def("tsuki.chain", "18");
        // the thrust takes over once gyaku has followed through; the lunge (dash) and its hit, in thrust ticks
        def("tsuki.from", "14");
        // gyaku must have run this long before a press queues the thrust (the press that started gyaku must not)
        def("tsuki.queueAfter", "5");
        def("tsuki.dash", "8");
        def("tsuki.hit", "9");
        // cross-fade into each cut (ticks): long for the thrust, which starts from wherever gyaku left the blade
        def("kesa.fade", "2");
        def("gyaku.fade", "2");
        def("tsuki.fade", "5");
        // first person: its own simpler timelines, one clean arc per cut with the grip low (out of the way of the
        // camera) and the blade turned flat to the view so its edge leads the swing; after each cut the blade drops
        // out of view and rises back into the guard. fp = tx ty tz (camera space) rx ry rz (degrees):
        // rz leans the blade (0 up, + to the left), rx tips it back toward the camera, ry spins it about itself
        def("fk_wind.fp", "0.75 -0.45 -1.1 20 90 -30");
        def("fk_end.fp", "-0.2 -0.95 -1.2 -5 90 45");
        def("fk_follow.fp", "-0.42 -1.1 -1.2 -8 90 58");
        def("fk_low.fp", "0.5 -1.6 -1.1 0 90 25");
        def("fg_wind.fp", "-0.05 -0.45 -1.1 20 90 30");
        def("fg_end.fp", "0.9 -0.95 -1.2 -5 90 -45");
        def("fg_follow.fp", "1.08 -1.1 -1.2 -8 90 -58");
        def("fg_low.fp", "0.62 -1.6 -1.1 0 90 -25");
        def("ft_chamber.fp", "0.42 -0.62 -0.9 -82 90 14");
        def("ft_load.fp", "0.48 -0.7 -0.62 -80 90 16");
        def("ft_end.fp", "0.22 -0.48 -1.4 -88 90 6");
        def("ft_low.fp", "0.5 -1.5 -1.1 -30 90 0");
        def("kesa.fpkeys", "guard@0 fk_wind@3:out fk_end@8:inout fk_follow@12:out fk_low@16:in guard@20:out");
        def("gyaku.fpkeys", "guard@0 fg_wind@4:out fg_end@9:inout fg_follow@13:out fg_low@17:in guard@21:out");
        def("tsuki.fpkeys", "ft_chamber@0 ft_chamber@6 ft_load@8:inout ft_end@9.5:out ft_end@13 ft_low@17:in guard@22:out");
        def("fp.scale", "0.45");
        validate();
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
            validate();
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

    /**
     * Every pose a third-person timeline names must exist (at least its arms): a missing pose would silently
     * sample as all zeros - arms hanging down mid-cut, which reads as a jerk.
     */
    private static void validate() {
        for (String cut : new String[]{KESA, GYAKU, TSUKI, RITUAL, PARRY, PARRIED}) {
            for (Key k : timeline(cut)) {
                if (!P.containsKey(k.pose + ".rArm")) Soulless.LOGGER.error("katana pose '{}' (in {}) is not defined", k.pose, cut);
            }
            for (Key k : timeline(cut + "#fp")) {
                if (!P.containsKey(k.pose + ".fp")) Soulless.LOGGER.error("katana first-person pose '{}' (in {}) is not defined", k.pose, cut);
            }
        }
    }

    /** A timeline by name; "name#fp" is its first-person version (name.fpkeys), when it has one. */
    private static List<Key> timeline(String name) {
        return TIMELINES.computeIfAbsent(name, n -> {
            List<Key> keys = new ArrayList<>();
            String prop = n.endsWith("#fp") ? n.substring(0, n.length() - 3) + ".fpkeys" : n + ".keys";
            for (String tok : P.getProperty(prop, GUARD + "@0").trim().split("\\s+")) {
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
        List<Key> keys = timeline(field.equals("fp") && P.containsKey(name + ".fpkeys") ? name + "#fp" : name);
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
