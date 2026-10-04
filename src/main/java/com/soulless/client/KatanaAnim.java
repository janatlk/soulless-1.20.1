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
import net.minecraft.world.InteractionHand;
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
 * The poses live in {@link KatanaPoses}; switching from one cut to the next cross-fades. Elbows, knees and the
 * waist bend when bendy-lib is installed.
 */
public final class KatanaAnim implements IAnimation {
    public static final ResourceLocation ID = Soulless.id("katana");
    /** Ticks to blend into / out of the stance when the katana is drawn or put away. */
    private static final float BLEND_TICKS = 4;
    /** An attack within this many ticks after a cut ends continues the combo. */
    private static final float COMBO_GRACE = 10;
    public static final int KESA = 0, GYAKU = 1, TSUKI = 2;
    private static final String[] CUTS = {KatanaPoses.KESA, KatanaPoses.GYAKU, KatanaPoses.TSUKI};

    private final AbstractClientPlayer player;
    private float weight, weightO;
    private int age;
    /** The cut playing (or last played), -1 before the first. */
    private int step = -1;
    private int cutStart;
    /** The step the local player's click already announced to the server (-1: none). */
    private int announced = -1;
    /** The local player asked for the thrust during gyaku: it starts as soon as gyaku has followed through. */
    private boolean thrustQueued;
    /** Cross-fade from the pose the previous cut was in (frozen) into the new cut. */
    @Nullable private String fadeFrom;
    private float fadeFromTime;
    private int fadeStart = -100;
    private boolean wasSwinging;
    private int lastSwingTime;

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
        age++;
        weightO = weight;
        boolean allowed = stanceAllowed();
        weight = Mth.approach(weight, allowed ? 1 : 0, 1 / BLEND_TICKS);

        // a new attack swing: the vanilla swing restarts (swingTime drops) or begins
        boolean swing = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND
                && (!wasSwinging || player.swingTime < lastSwingTime);
        wasSwinging = player.swinging;
        lastSwingTime = player.swingTime;
        if (!allowed) {
            step = -1;
            thrustQueued = false;
            announced = -1;
            return;
        }
        if (swing) onSwing();

        int t = age - cutStart;
        if (thrustQueued && (step != GYAKU || t >= KatanaPoses.number("tsuki.from") || currentCut(0) == null)) {
            thrustQueued = false;
            start(TSUKI, 0);
        }
        if (step == TSUKI && player.isLocalPlayer()) {
            if (t == (int) KatanaPoses.number("tsuki.dash")) KatanaThrust.dash(player);
            if (t == (int) KatanaPoses.number("tsuki.hit")) KatanaThrust.strike(player);
        }
    }

    private void onSwing() {
        if (!canAttack()) return;   // still in a cut (the thrust's own swing lands here too)
        if (player.isLocalPlayer()) {
            if (announced < 0) return;   // not one of our katana attacks (e.g. punching a block)
            start(announced, 0);
            announced = -1;
            return;
        }
        // another player: a swing right after gyaku is their thrust landing - join it at the lunge
        if (step == GYAKU && age - cutStart < KatanaPoses.length(CUTS[GYAKU]) + COMBO_GRACE) {
            start(TSUKI, (int) KatanaPoses.number("tsuki.hit"));
        } else {
            start(nextStep(), 0);
        }
    }

    private void start(int next, int skip) {
        fadeFrom = currentCut(0);
        fadeFromTime = fadeFrom != null ? cutTime(0) : 0;
        fadeStart = age;
        step = next;
        cutStart = age - skip;
    }

    /** The cut a normal attack made now plays: gyaku right after kesa, otherwise kesa. */
    public int nextStep() {
        return step == KESA && age - cutStart < KatanaPoses.length(CUTS[KESA]) + COMBO_GRACE ? GYAKU : KESA;
    }

    /** True when an attack now would be the thrust: during gyaku or right after it, and not already queued. */
    public boolean thrustNext() {
        return step == GYAKU && !thrustQueued && age - cutStart < KatanaPoses.length(CUTS[GYAKU]) + COMBO_GRACE;
    }

    public void queueThrust() {
        thrustQueued = true;
    }

    /** The local player clicked a normal attack: this step was sent to the server and is the one that plays. */
    public void announce(int step) {
        announced = step;
    }

    /** False while a cut (or a queued thrust) is under way: clicks then do nothing. */
    public boolean canAttack() {
        return !thrustQueued && (step < 0 || age - cutStart >= KatanaPoses.number(CUTS[step] + ".chain"));
    }

    /** The cut playing right now, null at rest. */
    @Nullable
    public String currentCut(float partial) {
        return step >= 0 && cutTime(partial) < KatanaPoses.length(CUTS[step]) ? CUTS[step] : null;
    }

    public float cutTime(float partial) {
        return age - cutStart + partial;
    }

    /** How far the stance is blended in, 0..1. */
    public float weight(float partial) {
        float w = Mth.lerp(partial, weightO, weight);
        return w * w * (3 - 2 * w);
    }

    /** A pose field right now: the current cut (or jodan), cross-faded from the previous cut's pose. */
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
