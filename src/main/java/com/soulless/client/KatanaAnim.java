package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.item.SoulKatanaItem;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The katana's player animation (one per player, through playerAnimator). While the katana is in the main hand
 * the player stands in jodan-no-kamae. The combo:
 * <ol>
 *   <li>an attack: kesa-giri (right shoulder down to the left hip), back to jodan;</li>
 *   <li>the next attack soon after: gyaku-kesa (the other diagonal), back to jodan;</li>
 *   <li>an attack pressed during gyaku (or right after it) is queued: instead of jodan the blade is drawn in to
 *       the hip, pointing at the opponent, and the thrust (tsuki) is made with a lunge - the step does the work,
 *       the hands stay at the hip - with a short dash ({@link KatanaThrust}). Its hit lands at the lunge.</li>
 * </ol>
 * The katana never uses the vanilla arm swing (it would also snap the body around): the local player's clicks
 * start cuts directly, other players' cuts arrive from the server (KatanaAnimPacket). The server resolves the
 * hits from where the blade goes (item.KatanaCombat).
 * The poses live in {@link KatanaPoses}; switching from one cut to the next cross-fades. Elbows, knees and the
 * waist bend when bendy-lib is installed.
 */
public final class KatanaAnim implements IAnimation {
    public static final ResourceLocation ID = Soulless.id("katana");
    /** Ticks to blend into / out of the stance when the katana is drawn or put away. */
    private static final float BLEND_TICKS = 4;
    /** An attack within this many ticks after a cut ends continues the combo. */
    private static final float COMBO_GRACE = 10;
    public static final int KESA = 0, GYAKU = 1, TSUKI = 2, RITUAL = 3, PARRY = 4, PARRIED = 5;
    private static final String[] CUTS = {KatanaPoses.KESA, KatanaPoses.GYAKU, KatanaPoses.TSUKI, KatanaPoses.RITUAL,
            KatanaPoses.PARRY, KatanaPoses.PARRIED};

    private final AbstractClientPlayer player;
    private float weight, weightO;
    private int age;
    /** The move playing (or last played), -1 at rest. */
    private int step = -1;
    private int cutStart;
    /** How fast the move plays: faster under the Kitsune's blessing. */
    private float rate = 1;
    /** The local player asked for the thrust during gyaku: it starts as soon as gyaku has followed through. */
    private boolean thrustQueued;
    /** Cross-fade from the pose the previous move was in (frozen) into the new one. */
    @Nullable private String fadeFrom;
    private float fadeFromTime;
    private int fadeStart = -100;

    private KatanaAnim(AbstractClientPlayer player) {
        this.player = player;
    }

    /** Registers the layer for every player; call during client setup, before any player exists. */
    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ID, 1000, KatanaAnim::new);
    }

    @Nullable
    public static KatanaAnim of(AbstractClientPlayer player) {
        return PlayerAnimationAccess.getPlayerAssociatedData(player).get(ID) instanceof KatanaAnim a ? a : null;
    }

    private boolean stanceAllowed() {
        return player.getMainHandItem().getItem() instanceof SoulKatanaItem && !player.isSpectator()
                && !player.isSleeping() && !player.isFallFlying() && !player.isVisuallySwimming()
                && !player.isPassenger() && player.getPose() != Pose.SPIN_ATTACK && !player.isUsingItem();
    }

    @Override
    public void tick() {
        KatanaPoses.maybeReload();
        float before = step >= 0 ? cutTime(0) : -1;
        age++;
        weightO = weight;
        boolean allowed = stanceAllowed();
        weight = Mth.approach(weight, allowed ? 1 : 0, 1 / BLEND_TICKS);
        if (!allowed) {
            step = -1;
            thrustQueued = false;
            return;
        }

        float t = cutTime(0);
        if (thrustQueued && step != RITUAL && (step != GYAKU || t >= KatanaPoses.number("tsuki.from") || currentCut(0) == null)) {
            thrustQueued = false;
            start(TSUKI, 0, localRate());
            if (player.isLocalPlayer()) {
                com.soulless.network.ModNetwork.sendToServer(new com.soulless.network.KatanaCutPacket(TSUKI));
            }
            before = -1;
            t = cutTime(0);
        }
        if (step == TSUKI && crossed(before, t, KatanaPoses.number("tsuki.dash"))) {
            if (player.isLocalPlayer()) KatanaThrust.dash(player, rate);
            KatanaTrails.dash(player);   // every client draws the gust behind whoever lunges
        }
        if (step >= KESA && step <= TSUKI && crossed(before, t, KatanaTrails.sweepDelay(step))) KatanaTrails.whoosh(player, step);
        if (step == RITUAL && crossed(before, t, KatanaPoses.number("ritual.stab"))) KatanaRitualFx.stab(player);
        if (currentCut(0) != null && step <= TSUKI) {
            // the body turns into the cut smoothly (vanilla would snap it on an arm swing)
            player.yBodyRot += Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot) * 0.25F;
        }
    }

    private static boolean crossed(float before, float now, float at) {
        return before < at && now >= at;
    }

    private float localRate() {
        return com.soulless.item.KatanaCombat.rate(player);
    }

    /** The local player's click started a cut (the server was told, KatanaCutPacket). */
    public void localCut(int next) {
        // a riposte: the first cut right after a parry is much faster
        boolean riposte = step == PARRIED && age - cutStart < 60;
        start(next, 0, localRate() * (riposte ? com.soulless.item.KatanaParry.RIPOSTE_RATE : 1));
    }

    /** A move sent by the server: another player's cut, anyone's ritual, or the ritual broken off (-1). */
    public static void remoteMove(int entityId, int move, float rate) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer p)) return;
        KatanaAnim anim = of(p);
        if (anim == null) return;
        if (move == -1) {
            if (anim.step == RITUAL) anim.stop();
        } else if (move >= RITUAL || (move >= KESA && move <= TSUKI && p != mc.player)) {
            anim.thrustQueued = false;
            anim.start(move, 0, rate);
        }
    }

    private void start(int next, int skip, float rate) {
        fadeFrom = currentCut(0);
        fadeFromTime = fadeFrom != null ? cutTime(0) : 0;
        fadeStart = age;
        step = next;
        this.rate = next >= RITUAL ? 1 : rate;
        cutStart = age - skip;
        if (skip == 0 && next <= TSUKI) KatanaTrails.add(player, next, this.rate);
    }

    /** Back to the guard from wherever the move is (the ritual broken off). */
    private void stop() {
        fadeFrom = currentCut(0);
        fadeFromTime = fadeFrom != null ? cutTime(0) : 0;
        fadeStart = age;
        step = -1;
    }

    /** The cut a normal attack made now plays: gyaku right after kesa, otherwise kesa. */
    public int nextStep() {
        return step == KESA && cutTime(0) < KatanaPoses.length(CUTS[KESA]) + COMBO_GRACE ? GYAKU : KESA;
    }

    /** True when an attack now would be the thrust: during gyaku or right after it, and not already queued. */
    public boolean thrustNext() {
        float t = cutTime(0);
        return step == GYAKU && !thrustQueued && t >= KatanaPoses.number("tsuki.queueAfter")
                && t < KatanaPoses.length(CUTS[GYAKU]) + COMBO_GRACE;
    }

    public void queueThrust() {
        thrustQueued = true;
    }

    /** False while a move (or a queued thrust) is under way: clicks then do nothing. */
    public boolean canAttack() {
        if (thrustQueued) return false;
        if (step == RITUAL || step == PARRY) return currentCut(0) == null;
        return step < 0 || cutTime(0) >= KatanaPoses.number(CUTS[step] + ".chain");
    }

    /** The parry caught something (the recoil is playing). */
    public boolean parried() {
        return step == PARRIED;
    }

    /** The parry stance is held (deep knees: no jumping). */
    public boolean inParry() {
        return (step == PARRY || step == PARRIED) && currentCut(0) != null;
    }

    /** The ritual is under way: the player kneels and cannot move. */
    public boolean inRitual() {
        return step == RITUAL && currentCut(0) != null;
    }

    /** The move playing right now, null at rest. */
    @Nullable
    public String currentCut(float partial) {
        return step >= 0 && cutTime(partial) < KatanaPoses.length(CUTS[step]) ? CUTS[step] : null;
    }

    /** Time into the move, in the move's own ticks (faster under the blessing). */
    public float cutTime(float partial) {
        return (age - cutStart + partial) * rate;
    }

    /** How far the stance is blended in, 0..1. */
    public float weight(float partial) {
        float w = Mth.lerp(partial, weightO, weight);
        return w * w * (3 - 2 * w);
    }

    /** A pose field right now: the current move (or jodan), cross-faded from the previous move's pose. */
    public float[] pose(String field, int size, float partial) {
        String cut = currentCut(partial);
        float[] out = KatanaPoses.sample(cut, cut != null ? cutTime(partial) : 0, field, new float[size]);
        float fade = cut != null ? KatanaPoses.number(cut + ".fade") : 3;
        float k = fade > 0 ? (age + partial - fadeStart) / fade : 1;
        if (k < 1) {
            float[] from = KatanaPoses.sample(fadeFrom, fadeFromTime, field, new float[size]);
            k = k * k * (3 - 2 * k);
            for (int i = 0; i < size; i++) out[i] = Mth.lerp(k, from[i], out[i]);
        }
        return out;
    }

    @Override
    public boolean isActive() {
        // playerAnimator only ticks active layers, so drawing the katana has to switch the layer on by itself
        return weight > 0 || weightO > 0 || stanceAllowed();
    }

    @Override
    public void setupAnim(float tickDelta) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v0) {
        float w = weight(tickDelta);
        if (w <= 0) return v0;
        return switch (type) {
            case ROTATION -> rotation(part, tickDelta, w, v0);
            case BEND -> bend(part, tickDelta, w, v0);
            case POSITION -> position(part, tickDelta, w, v0);
        };
    }

    private float[] rot(String field, float partial) {
        float[] r = pose(field, 3, partial);
        r[0] *= Mth.DEG_TO_RAD;
        r[1] *= Mth.DEG_TO_RAD;
        r[2] *= Mth.DEG_TO_RAD;
        return r;
    }

    private Vec3f rotation(String part, float partial, float w, Vec3f v0) {
        switch (part) {
            case "rightArm", "leftArm" -> {
                // the arms follow the torso's twist and lean
                float[] torso = rot("torso", partial);
                float[] r = rot(part.equals("rightArm") ? "rArm" : "lArm", partial);
                return mix(v0, r[0] + torso[0], r[1] + torso[1], r[2], w);
            }
            case "torso" -> {
                float[] r = rot("torso", partial);
                // keep vanilla's sneaking lean (its attack twist is replaced by the cut)
                return mix(v0, r[0] + (player.isCrouching() ? 0.5F : 0), r[1], r[2], w);
            }
            case "rightLeg", "leftLeg" -> {
                // stance offsets on top of the walking legs
                float[] r = rot(part.equals("rightLeg") ? "rLeg" : "lLeg", partial);
                return new Vec3f(v0.getX() + r[0] * w, v0.getY() + r[1] * w, v0.getZ() + r[2] * w);
            }
            case "head" -> {
                float[] r = rot("head", partial);
                return new Vec3f(v0.getX() + r[0] * w, v0.getY() + r[1] * w, v0.getZ() + r[2] * w);
            }
            case "body", "rightItem" -> {
                float[] r = rot(part.equals("body") ? "body" : "item", partial);
                return new Vec3f(v0.getX() + r[0] * w, v0.getY() + r[1] * w, v0.getZ() + r[2] * w);
            }
            default -> {
                return v0;
            }
        }
    }

    private Vec3f bend(String part, float partial, float w, Vec3f v0) {
        String field = switch (part) {
            case "rightArm" -> "rBend";
            case "leftArm" -> "lBend";
            case "rightLeg" -> "rLegBend";
            case "leftLeg" -> "lLegBend";
            case "body" -> "waist";
            default -> null;
        };
        if (field == null) return v0;
        float[] b = pose(field, 2, partial);
        return new Vec3f(b[0] * Mth.DEG_TO_RAD, b[1] * Mth.DEG_TO_RAD * w, 0);
    }

    private Vec3f position(String part, float partial, float w, Vec3f v0) {
        switch (part) {
            case "rightArm", "leftArm" -> {
                // shoulders turn with the torso (as in vanilla's attack swing)
                float twist = rot("torso", partial)[1];
                float side = part.equals("rightArm") ? -1 : 1;
                float x = side * Mth.cos(twist) * 5, z = -side * Mth.sin(twist) * 5;
                return new Vec3f(Mth.lerp(w, v0.getX(), x), v0.getY(), Mth.lerp(w, v0.getZ(), z));
            }
            case "rightItem" -> {
                float[] p = pose("itemPos", 3, partial);
                return new Vec3f(v0.getX() + p[0] * w, v0.getY() + p[1] * w, v0.getZ() + p[2] * w);
            }
            case "body" -> {
                // the whole body (pixels; y up, z forward): the lunge drops and drives the hips forward
                float[] p = pose("bodyPos", 3, partial);
                return new Vec3f(v0.getX() + p[0] / 16 * w, v0.getY() + p[1] / 16 * w, v0.getZ() - p[2] / 16 * w);
            }
            default -> {
                return v0;
            }
        }
    }

    private static Vec3f mix(Vec3f v0, float x, float y, float z, float w) {
        return new Vec3f(Mth.lerp(w, v0.getX(), x), Mth.lerp(w, v0.getY(), y), Mth.lerp(w, v0.getZ(), z));
    }
}
