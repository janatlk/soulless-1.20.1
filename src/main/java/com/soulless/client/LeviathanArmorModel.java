package com.soulless.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/** The Leviathan armour's model: vanilla armour boxes with the knight set's details (pauldrons, collar, leg
 * plates, the helmet). Written by tools/armor/armor_atlas.py from the Blockbench models - edit those, not this. */
public class LeviathanArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation OUTER = new ModelLayerLocation(com.soulless.Soulless.id("leviathan_armor"), "outer");
    public static final ModelLayerLocation INNER = new ModelLayerLocation(com.soulless.Soulless.id("leviathan_armor"), "inner");
    private static final float D = (float) (Math.PI / 180);

    public LeviathanArmorModel(ModelPart root) {
        super(root);
    }

    /** The helmet, chestplate and boots (texture 1). */
    public static LayerDefinition outer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(0.75F);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, 0, 0));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, d), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4, d), PartPose.offset(-5, 2, 0));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4, d), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(-1.9F, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(1.9F, 12, 0));
        head.addOrReplaceChild("d0", CubeListBuilder.create().texOffs(84, 32).addBox(-4F, -2F, -3.5F, 10, 3, 10), PartPose.offsetAndRotation(-1F, -8.25F, -0.5F, 0F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d1", CubeListBuilder.create().texOffs(0, 45).addBox(-1F, -0.75F, -4.25F, 2, 7, 12), PartPose.offsetAndRotation(0F, -13.75F, 1.5F, -22.5F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d2", CubeListBuilder.create().texOffs(28, 45).addBox(0F, -1F, 0F, 10, 6, 2), PartPose.offsetAndRotation(-5F, -6.25F, 4F, 0F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d3", CubeListBuilder.create().texOffs(52, 45).addBox(-1F, 6.5F, -4.25F, 2, 3, 5), PartPose.offsetAndRotation(0F, -13.75F, 1.5F, -22.5F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d4", CubeListBuilder.create().texOffs(66, 45).addBox(-1F, -5.5F, -6F, 1, 6, 8), PartPose.offsetAndRotation(5F, -1.75F, 2F, 0F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d5", CubeListBuilder.create().texOffs(84, 45).addBox(0F, -5.5F, -6F, 1, 6, 8), PartPose.offsetAndRotation(-5F, -1.75F, 2F, 0F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d6", CubeListBuilder.create().texOffs(0, 64).addBox(-5.75F, -4.5F, -2F, 12, 7, 3), PartPose.offsetAndRotation(0F, -3.75F, 5.181F, 22.5F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d7", CubeListBuilder.create().texOffs(30, 64).addBox(-1F, -4.5F, -1F, 2, 7, 2), PartPose.offsetAndRotation(4.75F, -7.5F, -4.25F, -22.5F * D, 0F * D, 0F * D));
        head.addOrReplaceChild("d8", CubeListBuilder.create().texOffs(38, 64).addBox(-1F, -4.5F, -1F, 2, 7, 2), PartPose.offsetAndRotation(-4.75F, -7.5F, -4.25F, -22.5F * D, 0F * D, 0F * D));
        body.addOrReplaceChild("d0", CubeListBuilder.create().texOffs(28, 32).addBox(-3.5F, -2F, -3.5F, 7, 2, 7), PartPose.offsetAndRotation(0F, 1F, 0F, 0F * D, 0F * D, 0F * D));
        rightArm.addOrReplaceChild("d0", CubeListBuilder.create().texOffs(0, 32).addBox(-2F, -1.5F, -3.5F, 7, 3, 7), PartPose.offsetAndRotation(-3F, -2.5F, 0F, 0F * D, 0F * D, 45F * D));
        leftArm.addOrReplaceChild("d0", CubeListBuilder.create().texOffs(56, 32).addBox(-5F, -1.5F, -3.5F, 7, 3, 7), PartPose.offsetAndRotation(3F, -2.5F, 0F, 0F * D, 0F * D, -45F * D));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** The leggings (texture 2). */
    public static LayerDefinition inner() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(0.5F);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, d), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(-1.9F, 12, 0));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(1.9F, 12, 0));
        rightLeg.addOrReplaceChild("d0", CubeListBuilder.create().texOffs(0, 32).addBox(0F, -2F, -2F, 1, 6, 7), PartPose.offsetAndRotation(-3.35F, -0.5F, -1.5F, 0F * D, 0F * D, 22.5F * D));
        leftLeg.addOrReplaceChild("d0", CubeListBuilder.create().texOffs(16, 32).addBox(-1F, -2F, -2F, 1, 6, 7), PartPose.offsetAndRotation(3.35F, -0.5F, -1.5F, 0F * D, 0F * D, -22.5F * D));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
