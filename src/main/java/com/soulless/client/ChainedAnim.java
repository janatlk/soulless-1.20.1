package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.boss.ChainSeatEntity;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Chained by the Renegade (boss.ChainSeatEntity): on both knees, the arms dragged back and out by the chains, the
 * head hanging; every few seconds a tug against the chains - the head comes up, the shoulders heave, the arms jerk
 * forward and are pulled back. Over the game's riding pose (the seat is only there to hold the player down).
 * Angles in degrees as in KatanaPoses: x+ swings an arm or leg back, head x+ looks down, torso x+ bows forward.
 */
public final class ChainedAnim implements IAnimation {
    /** The fall onto the knees (ticks). */
    private static final float FALL = 6;
    /** A tug against the chains every this many ticks, lasting TUG. */
    private static final float EVERY = 64, TUG = 16;

    private final AbstractClientPlayer player;
    private int since = -1;

    private ChainedAnim(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(Soulless.id("chained"), 1100, ChainedAnim::new);
    }

    @Override
    public boolean isActive() {
        boolean on = player.getVehicle() instanceof ChainSeatEntity;
        if (!on) since = -1;
        else if (since < 0) since = player.tickCount;
        return on;
    }

    @Override
    public void tick() {}

    @Override
    public void setupAnim(float tickDelta) {}

    private static float bump(float x) {
        return x <= 0 || x >= 1 ? 0 : Mth.sin(x * Mth.PI) * Mth.sin(x * Mth.PI);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v0) {
        if (since < 0) return v0;
        float t = player.tickCount - since + tickDelta;
        float fall = Mth.clamp(t / FALL, 0, 1);
        fall = 1 - (1 - fall) * (1 - fall);
        float tug = bump(((t - 20) % EVERY) / TUG) * (t > 20 ? 1 : 0);              // the struggle
        float jerk = tug * Mth.sin(t * 2.6F) * 0.5F;                                  // ...and the chains snatching back
        float breath = Mth.sin(t * 0.18F);
        float d = Mth.DEG_TO_RAD;
        switch (type) {
            case ROTATION -> {
                switch (part) {
                    case "rightLeg" -> {
                        return new Vec3f(4 * d, 6 * d, 3 * d);
                    }
                    case "leftLeg" -> {
                        return new Vec3f(4 * d, -6 * d, -3 * d);
                    }
                    case "rightArm" -> {
                        return new Vec3f((48 - 38 * tug + 6 * jerk) * d * fall, 8 * d, (30 - 8 * tug) * d * fall);
                    }
                    case "leftArm" -> {
                        return new Vec3f((48 - 38 * tug - 6 * jerk) * d * fall, -8 * d, (-30 + 8 * tug) * d * fall);
                    }
                    case "head" -> {
                        return new Vec3f((32 - 50 * tug + 2 * breath) * d * fall, v0.getY() * 0.3F * tug, 4 * jerk * d);
                    }
                    case "torso" -> {
                        return new Vec3f((18 - 22 * tug + 2 * breath) * d * fall, 3 * jerk * d, 0);
                    }
                    default -> {
                        return v0;
                    }
                }
            }
            case BEND -> {
                return switch (part) {
                    case "rightLeg", "leftLeg" -> new Vec3f(0, 100 * d * fall, 0);
                    case "rightArm", "leftArm" -> new Vec3f(0, -(18 + 22 * tug) * d * fall, 0);
                    default -> v0;
                };
            }
            case POSITION -> {
                if (part.equals("body")) {
                    // down onto the knees (the seat already holds the player a little low), rising a touch in the tug
                    return new Vec3f(v0.getX(), v0.getY() + (-2.6F + 1.2F * tug) / 16 * fall, v0.getZ());
                }
                return v0;
            }
            default -> {
                return v0;
            }
        }
    }
}
