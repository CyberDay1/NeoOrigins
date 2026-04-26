package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FishingHookRenderer extends EntityRenderer<FishingHook> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);
    private static final double VIEW_BOBBING_SCALE = 960.0;

    public FishingHookRenderer(EntityRendererProvider.Context p_174117_) {
        super(p_174117_);
    }

    public void render(FishingHook p_114705_, float p_114706_, float p_114707_, PoseStack p_114708_, MultiBufferSource p_114709_, int p_114710_) {
        Player player = p_114705_.getPlayerOwner();
        if (player != null) {
            p_114708_.pushPose();
            p_114708_.pushPose();
            p_114708_.scale(0.5F, 0.5F, 0.5F);
            p_114708_.mulPose(this.entityRenderDispatcher.cameraOrientation());
            PoseStack.Pose posestack$pose = p_114708_.last();
            VertexConsumer vertexconsumer = p_114709_.getBuffer(RENDER_TYPE);
            vertex(vertexconsumer, posestack$pose, p_114710_, 0.0F, 0, 0, 1);
            vertex(vertexconsumer, posestack$pose, p_114710_, 1.0F, 0, 1, 1);
            vertex(vertexconsumer, posestack$pose, p_114710_, 1.0F, 1, 1, 0);
            vertex(vertexconsumer, posestack$pose, p_114710_, 0.0F, 1, 0, 0);
            p_114708_.popPose();
            float f = player.getAttackAnim(p_114707_);
            float f1 = Mth.sin(Mth.sqrt(f) * (float) Math.PI);
            Vec3 vec3 = this.getPlayerHandPos(player, f1, p_114707_);
            Vec3 vec31 = p_114705_.getPosition(p_114707_).add(0.0, 0.25, 0.0);
            float f2 = (float)(vec3.x - vec31.x);
            float f3 = (float)(vec3.y - vec31.y);
            float f4 = (float)(vec3.z - vec31.z);
            VertexConsumer vertexconsumer1 = p_114709_.getBuffer(RenderType.lineStrip());
            PoseStack.Pose posestack$pose1 = p_114708_.last();
            int i = 16;

            for (int j = 0; j <= 16; j++) {
                stringVertex(f2, f3, f4, vertexconsumer1, posestack$pose1, fraction(j, 16), fraction(j + 1, 16));
            }

            p_114708_.popPose();
            super.render(p_114705_, p_114706_, p_114707_, p_114708_, p_114709_, p_114710_);
        }
    }

    private Vec3 getPlayerHandPos(Player p_340935_, float p_340872_, float p_341261_) {
        int i = p_340935_.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        ItemStack itemstack = p_340935_.getMainHandItem();
        if (!itemstack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST)) {
            i = -i;
        }

        if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && p_340935_ == Minecraft.getInstance().player) {
            double d4 = 960.0 / (double)this.entityRenderDispatcher.options.fov().get().intValue();
            Vec3 vec3 = this.entityRenderDispatcher
                .camera
                .getNearPlane()
                .getPointOnPlane((float)i * 0.525F, -0.1F)
                .scale(d4)
                .yRot(p_340872_ * 0.5F)
                .xRot(-p_340872_ * 0.7F);
            return p_340935_.getEyePosition(p_341261_).add(vec3);
        } else {
            float f = Mth.lerp(p_341261_, p_340935_.yBodyRotO, p_340935_.yBodyRot) * (float) (Math.PI / 180.0);
            double d0 = (double)Mth.sin(f);
            double d1 = (double)Mth.cos(f);
            float f1 = p_340935_.getScale();
            double d2 = (double)i * 0.35 * (double)f1;
            double d3 = 0.8 * (double)f1;
            float f2 = p_340935_.isCrouching() ? -0.1875F : 0.0F;
            return p_340935_.getEyePosition(p_341261_).add(-d1 * d2 - d0 * d3, (double)f2 - 0.45 * (double)f1, -d0 * d2 + d1 * d3);
        }
    }

    private static float fraction(int p_114691_, int p_114692_) {
        return (float)p_114691_ / (float)p_114692_;
    }

    private static void vertex(VertexConsumer p_254464_, PoseStack.Pose p_323724_, int p_254296_, float p_253632_, int p_254132_, int p_254171_, int p_254026_) {
        p_254464_.addVertex(p_323724_, p_253632_ - 0.5F, (float)p_254132_ - 0.5F, 0.0F)
            .setColor(-1)
            .setUv((float)p_254171_, (float)p_254026_)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(p_254296_)
            .setNormal(p_323724_, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(
        float p_174119_, float p_174120_, float p_174121_, VertexConsumer p_174122_, PoseStack.Pose p_174123_, float p_174124_, float p_174125_
    ) {
        float f = p_174119_ * p_174124_;
        float f1 = p_174120_ * (p_174124_ * p_174124_ + p_174124_) * 0.5F + 0.25F;
        float f2 = p_174121_ * p_174124_;
        float f3 = p_174119_ * p_174125_ - f;
        float f4 = p_174120_ * (p_174125_ * p_174125_ + p_174125_) * 0.5F + 0.25F - f1;
        float f5 = p_174121_ * p_174125_ - f2;
        float f6 = Mth.sqrt(f3 * f3 + f4 * f4 + f5 * f5);
        f3 /= f6;
        f4 /= f6;
        f5 /= f6;
        p_174122_.addVertex(p_174123_, f, f1, f2).setColor(-16777216).setNormal(p_174123_, f3, f4, f5);
    }

    public ResourceLocation getTextureLocation(FishingHook p_114703_) {
        return TEXTURE_LOCATION;
    }
}
