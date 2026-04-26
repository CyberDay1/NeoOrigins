package net.minecraft.client.model;

import net.minecraft.client.animation.definitions.BreezeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BreezeModel<T extends Breeze> extends HierarchicalModel<T> {
    private static final float WIND_TOP_SPEED = 0.6F;
    private static final float WIND_MIDDLE_SPEED = 0.8F;
    private static final float WIND_BOTTOM_SPEED = 1.0F;
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart eyes;
    private final ModelPart wind;
    private final ModelPart windTop;
    private final ModelPart windMid;
    private final ModelPart windBottom;
    private final ModelPart rods;

    public BreezeModel(ModelPart p_312152_) {
        super(RenderType::entityTranslucent);
        this.root = p_312152_;
        this.wind = p_312152_.getChild("wind_body");
        this.windBottom = this.wind.getChild("wind_bottom");
        this.windMid = this.windBottom.getChild("wind_mid");
        this.windTop = this.windMid.getChild("wind_top");
        this.head = p_312152_.getChild("body").getChild("head");
        this.eyes = this.head.getChild("eyes");
        this.rods = p_312152_.getChild("body").getChild("rods");
    }

    public static LayerDefinition createBodyLayer(int p_316814_, int p_316468_) {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition partdefinition1 = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition partdefinition2 = partdefinition1.addOrReplaceChild("rods", CubeListBuilder.create(), PartPose.offset(0.0F, 8.0F, 0.0F));
        partdefinition2.addOrReplaceChild(
            "rod_1",
            CubeListBuilder.create().texOffs(0, 17).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2.5981F, -3.0F, 1.5F, -2.7489F, -1.0472F, 3.1416F)
        );
        partdefinition2.addOrReplaceChild(
            "rod_2",
            CubeListBuilder.create().texOffs(0, 17).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-2.5981F, -3.0F, 1.5F, -2.7489F, 1.0472F, 3.1416F)
        );
        partdefinition2.addOrReplaceChild(
            "rod_3",
            CubeListBuilder.create().texOffs(0, 17).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, -3.0F, -3.0F, 0.3927F, 0.0F, 0.0F)
        );
        PartDefinition partdefinition3 = partdefinition1.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(4, 24)
                .addBox(-5.0F, -5.0F, -4.2F, 10.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 4.0F, 0.0F)
        );
        partdefinition3.addOrReplaceChild(
            "eyes",
            CubeListBuilder.create()
                .texOffs(4, 24)
                .addBox(-5.0F, -5.0F, -4.2F, 10.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        PartDefinition partdefinition4 = partdefinition.addOrReplaceChild("wind_body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition partdefinition5 = partdefinition4.addOrReplaceChild(
            "wind_bottom",
            CubeListBuilder.create().texOffs(1, 83).addBox(-2.5F, -7.0F, -2.5F, 5.0F, 7.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        PartDefinition partdefinition6 = partdefinition5.addOrReplaceChild(
            "wind_mid",
            CubeListBuilder.create()
                .texOffs(74, 28)
                .addBox(-6.0F, -6.0F, -6.0F, 12.0F, 6.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(78, 32)
                .addBox(-4.0F, -6.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(49, 71)
                .addBox(-2.5F, -6.0F, -2.5F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -7.0F, 0.0F)
        );
        partdefinition6.addOrReplaceChild(
            "wind_top",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-9.0F, -8.0F, -9.0F, 18.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
                .texOffs(6, 6)
                .addBox(-6.0F, -8.0F, -6.0F, 12.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(105, 57)
                .addBox(-2.5F, -8.0F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -6.0F, 0.0F)
        );
        return LayerDefinition.create(meshdefinition, p_316814_, p_316468_);
    }

    public void setupAnim(T p_312040_, float p_311926_, float p_312463_, float p_311788_, float p_311860_, float p_312017_) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float f = p_311788_ * (float) Math.PI * -0.1F;
        this.windTop.x = Mth.cos(f) * 1.0F * 0.6F;
        this.windTop.z = Mth.sin(f) * 1.0F * 0.6F;
        this.windMid.x = Mth.sin(f) * 0.5F * 0.8F;
        this.windMid.z = Mth.cos(f) * 0.8F;
        this.windBottom.x = Mth.cos(f) * -0.25F * 1.0F;
        this.windBottom.z = Mth.sin(f) * -0.25F * 1.0F;
        this.head.y = 4.0F + Mth.cos(f) / 4.0F;
        this.rods.yRot = p_311788_ * (float) Math.PI * 0.1F;
        this.animate(p_312040_.shoot, BreezeAnimation.SHOOT, p_311788_);
        this.animate(p_312040_.slide, BreezeAnimation.SLIDE, p_311788_);
        this.animate(p_312040_.slideBack, BreezeAnimation.SLIDE_BACK, p_311788_);
        this.animate(p_312040_.longJump, BreezeAnimation.JUMP, p_311788_);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    public ModelPart head() {
        return this.head;
    }

    public ModelPart eyes() {
        return this.eyes;
    }

    public ModelPart rods() {
        return this.rods;
    }

    public ModelPart wind() {
        return this.wind;
    }
}
