package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.item.SoulForgedItem;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

/**
 * Soul Rend while swimming. Vanilla's swimming stroke overwrites the arm pose (SoulArmPose) after it is set, so a
 * player swimming with Soul Forged showed no Soul Rend at all. This playerAnimator layer runs after vanilla and puts
 * the Rend's arm pose (RendAnim) back on top of the stroke - the body stays in its swimming pose, the arms haul the
 * blade over the head and cut.
 */
public final class SwimRendAnim implements IAnimation {
    private final AbstractClientPlayer player;

    private SwimRendAnim(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(Soulless.id("swim_rend"), 900, SwimRendAnim::new);
    }

    @Override
    public boolean isActive() {
        return player.isVisuallySwimming() && player.getMainHandItem().getItem() instanceof SoulForgedItem && RendAnim.sample(player) != null;
    }

    @Override
    public void setupAnim(float tickDelta) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v0) {
        if (type != TransformType.ROTATION || !(part.equals("rightArm") || part.equals("leftArm"))) return v0;
        SlamAnim.Pose pose = RendAnim.sample(player);
        if (pose == null) return v0;
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        boolean main = part.equals("rightArm") == rightHanded;
        float side = rightHanded ? 1 : -1;
        return main ? new Vec3f(pose.mainX, pose.mainY * side, 0) : new Vec3f(pose.offX, pose.offY * side, 0);
    }
}
