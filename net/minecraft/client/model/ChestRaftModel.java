package net.minecraft.client.model;

import com.google.common.collect.ImmutableList.Builder;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChestRaftModel extends RaftModel {
    private static final String CHEST_BOTTOM = "chest_bottom";
    private static final String CHEST_LID = "chest_lid";
    private static final String CHEST_LOCK = "chest_lock";

    public ChestRaftModel(ModelPart p_248562_) {
        super(p_248562_);
    }

    @Override
    protected Builder<ModelPart> createPartsBuilder(ModelPart p_251688_) {
        Builder<ModelPart> builder = super.createPartsBuilder(p_251688_);
        builder.add(p_251688_.getChild("chest_bottom"));
        builder.add(p_251688_.getChild("chest_lid"));
        builder.add(p_251688_.getChild("chest_lock"));
        return builder;
    }

    public static LayerDefinition createBodyModel() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        RaftModel.createChildren(partdefinition);
        partdefinition.addOrReplaceChild(
            "chest_bottom",
            CubeListBuilder.create().texOffs(0, 76).addBox(0.0F, 0.0F, 0.0F, 12.0F, 8.0F, 12.0F),
            PartPose.offsetAndRotation(-2.0F, -10.1F, -6.0F, 0.0F, (float) (-Math.PI / 2), 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "chest_lid",
            CubeListBuilder.create().texOffs(0, 59).addBox(0.0F, 0.0F, 0.0F, 12.0F, 4.0F, 12.0F),
            PartPose.offsetAndRotation(-2.0F, -14.1F, -6.0F, 0.0F, (float) (-Math.PI / 2), 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "chest_lock",
            CubeListBuilder.create().texOffs(0, 59).addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(-1.0F, -11.1F, -1.0F, 0.0F, (float) (-Math.PI / 2), 0.0F)
        );
        return LayerDefinition.create(meshdefinition, 128, 128);
    }
}
