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
 * The katana's player animation (one per player, through playerAnimator): while the katana is in the main hand
 * the player stands in jodan-no-kamae, and every attack swing plays a diagonal cut - kesa-giri (right shoulder
 * to left hip), and if the next attack follows quickly, gyaku-kesa back the other way, alternating.
 * The poses themselves live in {@link KatanaPoses}. Elbows, knees and the waist bend when bendy-lib is installed.
 */
public final class KatanaAnim implements IAnimation {
    public static final ResourceLocation ID = Soulless.id("katana");
    /** Ticks to blend into / out of the stance when the katana is drawn or put away. */
    private static final float BLEND_TICKS = 4;
    /** An attack within this many ticks after a cut ends continues the combo with the opposite diagonal. */
    private static final float COMBO_GRACE = 10;

    private final AbstractClientPlayer player;
    private float weight, weightO;
    private int age;
    /** The cut playing (or last played): KESA / GYAKU, null before the first. */
    @Nullable private String cut;
    private int cutStart;
    private boolean wasSwinging;
    private int lastSwingTime;
    private float tickDelta;
    private final float[] tmp3 = new float[3], tmp2 = new float[2];

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
            cut = null;
        } else if (swing) {
            startCut();
        }
    }

    private void startCut() {
        if (cut != null) {
            int since = age - cutStart;
            if (since < KatanaPoses.number(cut + ".chain")) return;   // still in the strike
            if (since < KatanaPoses.length(cut) + COMBO_GRACE) {
                cut = cut.equals(KatanaPoses.KESA) ? KatanaPoses.GYAKU : KatanaPoses.KESA;
                cutStart = age;
                return;
            }
        }
        cut = KatanaPoses.KESA;
        cutStart = age;
    }

    /** The cut playing right now (null at rest) and its time in ticks. */
    @Nullable
    public String currentCut(float partial) {
        return cut != null && cutTime(partial) < KatanaPoses.length(cut) ? cut : null;
    }

    public float cutTime(float partial) {
        return age - cutStart + partial;
    }

    /** How far the stance is blended in, 0..1. */
    public float weight(float partial) {
        float w = Mth.lerp(partial, weightO, weight);
        return w * w * (3 - 2 * w);
    }

    @Override
    public boolean isActive() {
        // playerAnimator only ticks active layers, so drawing the katana has to switch the layer on by itself
        return weight > 0 || weightO > 0 || stanceAllowed();
    }

    @Override
    public void setupAnim(float tickDelta) {
        this.tickDelta = tickDelta;
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v0) {
        float w = weight(tickDelta);
        if (w <= 0) return v0;
        String seq = currentCut(tickDelta);
        float t = seq != null ? cutTime(tickDelta) : 0;
        return switch (type) {
            case ROTATION -> rotation(part, seq, t, w, v0);
            case BEND -> bend(part, seq, t, w, v0);
            case POSITION -> position(part, seq, t, w, v0);
        };
    }

    private float[] rot(String seq, float t, String field) {
        float[] r = KatanaPoses.sample(seq, t, field, tmp3);
        r[0] *= Mth.DEG_TO_RAD;
        r[1] *= Mth.DEG_TO_RAD;
        r[2] *= Mth.DEG_TO_RAD;
        return r;
    }

    private Vec3f rotation(String part, String seq, float t, float w, Vec3f v0) {
        switch (part) {
            case "rightArm", "leftArm" -> {
                // the arms follow the torso's twist and lean
                float[] torso = rot(seq, t, "torso");
                float tx = torso[0], ty = torso[1];
                float[] r = rot(seq, t, part.equals("rightArm") ? "rArm" : "lArm");
                return mix(v0, r[0] + tx, r[1] + ty, r[2], w);
            }
            case "torso" -> {
                float[] r = rot(seq, t, "torso");
                // keep vanilla's sneaking lean (its attack twist is replaced by the cut)
                return mix(v0, r[0] + (player.isCrouching() ? 0.5F : 0), r[1], r[2], w);
            }
            case "rightLeg", "leftLeg" -> {
                // stance offsets on top of the walking legs
                float[] r = rot(seq, t, part.equals("rightLeg") ? "rLeg" : "lLeg");
                return new Vec3f(v0.getX() + r[0] * w, v0.getY() + r[1] * w, v0.getZ() + r[2] * w);
            }
            case "body", "rightItem" -> {
                float[] r = rot(seq, t, part.equals("body") ? "body" : "item");
                return new Vec3f(v0.getX() + r[0] * w, v0.getY() + r[1] * w, v0.getZ() + r[2] * w);
            }
            default -> {
                return v0;
            }
        }
    }

    private Vec3f bend(String part, String seq, float t, float w, Vec3f v0) {
        String field = switch (part) {
            case "rightArm" -> "rBend";
            case "leftArm" -> "lBend";
            case "rightLeg" -> "rLegBend";
            case "leftLeg" -> "lLegBend";
            case "body" -> "waist";
            default -> null;
        };
        if (field == null) return v0;
        float[] b = KatanaPoses.sample(seq, t, field, tmp2);
        return new Vec3f(b[0] * Mth.DEG_TO_RAD, b[1] * Mth.DEG_TO_RAD * w, 0);
    }

    private Vec3f position(String part, String seq, float t, float w, Vec3f v0) {
        switch (part) {
            case "rightArm", "leftArm" -> {
                // shoulders turn with the torso (as in vanilla's attack swing)
                float twist = rot(seq, t, "torso")[1];
                float side = part.equals("rightArm") ? -1 : 1;
                float x = side * Mth.cos(twist) * 5, z = -side * Mth.sin(twist) * 5;
                return new Vec3f(Mth.lerp(w, v0.getX(), x), v0.getY(), Mth.lerp(w, v0.getZ(), z));
            }
            case "rightItem" -> {
                float[] p = KatanaPoses.sample(seq, t, "itemPos", tmp3);
                return new Vec3f(v0.getX() + p[0] * w, v0.getY() + p[1] * w, v0.getZ() + p[2] * w);
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
