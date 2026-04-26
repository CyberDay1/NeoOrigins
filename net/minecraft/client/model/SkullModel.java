package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SkullModel extends SkullModelBase {
    private final ModelPart root;
    protected final ModelPart head;

    public SkullModel(ModelPart p_170945_) {
        this.root = p_170945_;
        this.head = p_170945_.getChild("head");
    }

    public static MeshDefinition createHeadModel() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
        return meshdefinition;
    }

    public static LayerDefinition createHumanoidHeadLayer() {
        MeshDefinition meshdefinition = createHeadModel();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.getChild("head")
            .addOrReplaceChild(
                "hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.25F)), PartPose.ZERO
            );
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    public static LayerDefinition createMobHeadLayer() {
        MeshDefinition meshdefinition = createHeadModel();
        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    @Override
    public void setupAnim(float p_103811_, float p_103812_, float p_103813_) {
        this.head.yRot = p_103812_ * (float) (Math.PI / 180.0);
        this.head.xRot = p_103813_ * (float) (Math.PI / 180.0);
    }

    @Override
    public void renderToBuffer(PoseStack p_103815_, VertexConsumer p_103816_, int p_103817_, int p_103818_, int p_350840_) {
        this.root.render(p_103815_, p_103816_, p_103817_, p_103818_, p_350840_);
    }
}
