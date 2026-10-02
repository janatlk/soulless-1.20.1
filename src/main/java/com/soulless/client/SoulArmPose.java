package com.soulless.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Third-person two-handed greatsword stance: both hands on the long grip, blade raised in front.
 * During "Soul Crush" the arms and legs follow {@link SlamAnim}; the whole-body flip, lean and kneel
 * height are applied to the pose stack in {@link ClientForgeEvents}.
 */
public final class SoulArmPose {
    public static final HumanoidModel.ArmPose GREATSWORD = HumanoidModel.ArmPose.create("SOULLESS_GREATSWORD", true, SoulArmPose::apply);

    /** Forces class init (and so {@link #GREATSWORD} creation) during mod construction. */
    public static void init() {}

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart off = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;

        SlamAnim.Pose pose = SlamAnim.sample(entity);
        float look = Mth.clamp(model.head.xRot, -0.6F, 0.6F) * 0.35F;
        if (pose == null) {
            pose = SlamAnim.GUARD;
        } else {
            look = 0;
            // legs: the whole body is leaned on the pose stack, so counter-rotate the legs to keep them planted
            float lean = pose.lean * Mth.DEG_TO_RAD;
            ModelPart front = right ? model.leftLeg : model.rightLeg;
            ModelPart back = right ? model.rightLeg : model.leftLeg;
            front.xRot = pose.frontLeg - lean;
            back.xRot = pose.backLeg - lean;
            front.yRot = back.yRot = 0;
            front.zRot = -0.05F * side;
            back.zRot = 0.05F * side;
        }
        main.xRot = pose.mainX + look;
        main.yRot = pose.mainY * side;
        main.zRot = 0;
        off.xRot = pose.offX + look;
        off.yRot = pose.offY * side;
        off.zRot = 0;
    }

    private SoulArmPose() {}
}
