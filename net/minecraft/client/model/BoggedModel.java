package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.monster.Bogged;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BoggedModel extends SkeletonModel<Bogged> {
    private final ModelPart mushrooms;

    public BoggedModel(ModelPart p_331727_) {
        super(p_331727_);
        this.mushrooms = p_331727_.getChild("head").getChild("mushrooms");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();
        SkeletonModel.createDefaultSkeletonMesh(partdefinition);
        PartDefinition partdefinition1 = partdefinition.getChild("head").addOrReplaceChild("mushrooms", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition1.addOrReplaceChild(
            "red_mushroom_1",
            CubeListBuilder.create().texOffs(50, 16).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 4.0F, 0.0F),
            PartPose.offsetAndRotation(3.0F, -8.0F, 3.0F, 0.0F, (float) (Math.PI / 4), 0.0F)
        );
        partdefinition1.addOrReplaceChild(
            "red_mushroom_2",
            CubeListBuilder.create().texOffs(50, 16).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 4.0F, 0.0F),
            PartPose.offsetAndRotation(3.0F, -8.0F, 3.0F, 0.0F, (float) (Math.PI * 3.0 / 4.0), 0.0F)
        );
        partdefinition1.addOrReplaceChild(
            "brown_mushroom_1",
            CubeListBuilder.create().texOffs(50, 22).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 4.0F, 0.0F),
            PartPose.offsetAndRotation(-3.0F, -8.0F, -3.0F, 0.0F, (float) (Math.PI / 4), 0.0F)
        );
        partdefinition1.addOrReplaceChild(
            "brown_mushroom_2",
            CubeListBuilder.create().texOffs(50, 22).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 4.0F, 0.0F),
            PartPose.offsetAndRotation(-3.0F, -8.0F, -3.0F, 0.0F, (float) (Math.PI * 3.0 / 4.0), 0.0F)
        );
        partdefinition1.addOrReplaceChild(
            "brown_mushroom_3",
            CubeListBuilder.create().texOffs(50, 28).addBox(-3.0F, -4.0F, 0.0F, 6.0F, 4.0F, 0.0F),
            PartPose.offsetAndRotation(-2.0F, -1.0F, 4.0F, (float) (-Math.PI / 2), 0.0F, (float) (Math.PI / 4))
        );
        partdefinition1.addOrReplaceChild(
            "brown_mushroom_4",
            CubeListBuilder.create().texOffs(50, 28).addBox(-3.0F, -4.0F, 0.0F, 6.0F, 4.0F, 0.0F),
            PartPose.offsetAndRotation(-2.0F, -1.0F, 4.0F, (float) (-Math.PI / 2), 0.0F, (float) (Math.PI * 3.0 / 4.0))
        );
        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    public void prepareMobModel(Bogged p_331102_, float p_330960_, float p_331747_, float p_330221_) {
        this.mushrooms.visible = !p_331102_.isSheared();
        super.prepareMobModel(p_331102_, p_330960_, p_331747_, p_330221_);
    }
}
