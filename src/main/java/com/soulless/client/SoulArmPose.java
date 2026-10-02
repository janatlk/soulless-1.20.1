package com.soulless.client;

import com.soulless.ability.SlamManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Third-person two-handed greatsword stance. Both hands meet on the long grip with the blade raised
 * in front; during a slam the arms lift the sword overhead and drive it into the ground.
 */
public final class SoulArmPose {
    public static final HumanoidModel.ArmPose GREATSWORD = HumanoidModel.ArmPose.create("SOULLESS_GREATSWORD", true, SoulArmPose::apply);

    private static final float WINDUP = SlamManager.WINDUP_TICKS;
    private static final float STRIKE_END = WINDUP + 3;
    private static final float HOLD_END = STRIKE_END + 8;
    private static final float RECOVER_END = HOLD_END + 8;

    /** Forces class init (and so {@link #GREATSWORD} creation) during mod construction. */
    public static void init() {}

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart off = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;

        // Guard stance; a little of the head pitch so the blade follows where the player looks.
        float look = Mth.clamp(model.head.xRot, -0.6F, 0.6F) * 0.35F;
        float mainX = -0.95F + look, offX = -1.1F + look;
        float mainY = -0.3F * side, offY = 0.55F * side;

        float t = ClientState.slamTicks(entity);
        if (t >= 0) {
            float raised = -2.95F, struck = -0.35F;
            float x;
            if (t < WINDUP) {
                x = Mth.lerp(smooth(t / WINDUP), mainX, raised);
            } else if (t < STRIKE_END) {
                x = Mth.lerp(easeIn((t - WINDUP) / (STRIKE_END - WINDUP)), raised, struck);
            } else if (t < HOLD_END) {
                x = struck;
            } else {
                x = Mth.lerp(smooth(Math.min(1, (t - HOLD_END) / (RECOVER_END - HOLD_END))), struck, mainX);
            }
            float narrow = t < HOLD_END ? 1 : 1 - smooth(Math.min(1, (t - HOLD_END) / (RECOVER_END - HOLD_END)));
            mainX = x;
            offX = x - 0.1F;
            mainY = Mth.lerp(narrow, mainY, -0.12F * side);
            offY = Mth.lerp(narrow, offY, 0.35F * side);
        }

        main.xRot = mainX;
        main.yRot = mainY;
        main.zRot = 0;
        off.xRot = offX;
        off.yRot = offY;
        off.zRot = 0;
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static float easeIn(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k;
    }

    private SoulArmPose() {}
}
