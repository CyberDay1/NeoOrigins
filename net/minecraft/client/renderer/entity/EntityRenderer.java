package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public abstract class EntityRenderer<T extends Entity> {
    protected static final float NAMETAG_SCALE = 0.025F;
    public static final int LEASH_RENDER_STEPS = 24;
    protected final EntityRenderDispatcher entityRenderDispatcher;
    private final Font font;
    protected float shadowRadius;
    protected float shadowStrength = 1.0F;

    protected EntityRenderer(EntityRendererProvider.Context p_174008_) {
        this.entityRenderDispatcher = p_174008_.getEntityRenderDispatcher();
        this.font = p_174008_.getFont();
    }

    public final int getPackedLightCoords(T p_114506_, float p_114507_) {
        BlockPos blockpos = BlockPos.containing(p_114506_.getLightProbePosition(p_114507_));
        return LightTexture.pack(this.getBlockLightLevel(p_114506_, blockpos), this.getSkyLightLevel(p_114506_, blockpos));
    }

    protected int getSkyLightLevel(T p_114509_, BlockPos p_114510_) {
        return p_114509_.level().getBrightness(LightLayer.SKY, p_114510_);
    }

    protected int getBlockLightLevel(T p_114496_, BlockPos p_114497_) {
        return p_114496_.isOnFire() ? 15 : p_114496_.level().getBrightness(LightLayer.BLOCK, p_114497_);
    }

    public boolean shouldRender(T p_114491_, Frustum p_114492_, double p_114493_, double p_114494_, double p_114495_) {
        if (!p_114491_.shouldRender(p_114493_, p_114494_, p_114495_)) {
            return false;
        } else if (p_114491_.noCulling) {
            return true;
        } else {
            AABB aabb = p_114491_.getBoundingBoxForCulling().inflate(0.5);
            if (aabb.hasNaN() || aabb.getSize() == 0.0) {
                aabb = new AABB(
                    p_114491_.getX() - 2.0,
                    p_114491_.getY() - 2.0,
                    p_114491_.getZ() - 2.0,
                    p_114491_.getX() + 2.0,
                    p_114491_.getY() + 2.0,
                    p_114491_.getZ() + 2.0
                );
            }

            if (p_114492_.isVisible(aabb)) {
                return true;
            } else {
                if (p_114491_ instanceof Leashable leashable) {
                    Entity entity = leashable.getLeashHolder();
                    if (entity != null) {
                        return p_114492_.isVisible(entity.getBoundingBoxForCulling());
                    }
                }

                return false;
            }
        }
    }

    public Vec3 getRenderOffset(T p_114483_, float p_114484_) {
        return Vec3.ZERO;
    }

    public void render(T p_114485_, float p_114486_, float p_114487_, PoseStack p_114488_, MultiBufferSource p_114489_, int p_114490_) {
        if (p_114485_ instanceof Leashable leashable) {
            Entity entity = leashable.getLeashHolder();
            if (entity != null) {
                this.renderLeash(p_114485_, p_114487_, p_114488_, p_114489_, entity);
            }
        }

        // Neo: Post the RenderNameTagEvent and conditionally wrap #renderNameTag based on the result.
        var event = new net.neoforged.neoforge.client.event.RenderNameTagEvent(p_114485_, p_114485_.getDisplayName(), this, p_114488_, p_114489_, p_114490_, p_114487_);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (event.canRender().isTrue() || event.canRender().isDefault() && this.shouldShowName(p_114485_)) {
            this.renderNameTag(p_114485_, event.getContent(), p_114488_, p_114489_, p_114490_, p_114487_);
        }
    }

    private <E extends Entity> void renderLeash(T p_352225_, float p_352465_, PoseStack p_352205_, MultiBufferSource p_352444_, E p_352269_) {
        p_352205_.pushPose();
        Vec3 vec3 = p_352269_.getRopeHoldPosition(p_352465_);
        double d0 = (double)(p_352225_.getPreciseBodyRotation(p_352465_) * (float) (Math.PI / 180.0)) + (Math.PI / 2);
        Vec3 vec31 = p_352225_.getLeashOffset(p_352465_);
        double d1 = Math.cos(d0) * vec31.z + Math.sin(d0) * vec31.x;
        double d2 = Math.sin(d0) * vec31.z - Math.cos(d0) * vec31.x;
        double d3 = Mth.lerp((double)p_352465_, p_352225_.xo, p_352225_.getX()) + d1;
        double d4 = Mth.lerp((double)p_352465_, p_352225_.yo, p_352225_.getY()) + vec31.y;
        double d5 = Mth.lerp((double)p_352465_, p_352225_.zo, p_352225_.getZ()) + d2;
        p_352205_.translate(d1, vec31.y, d2);
        float f = (float)(vec3.x - d3);
        float f1 = (float)(vec3.y - d4);
        float f2 = (float)(vec3.z - d5);
        float f3 = 0.025F;
        VertexConsumer vertexconsumer = p_352444_.getBuffer(RenderType.leash());
        Matrix4f matrix4f = p_352205_.last().pose();
        float f4 = Mth.invSqrt(f * f + f2 * f2) * 0.025F / 2.0F;
        float f5 = f2 * f4;
        float f6 = f * f4;
        BlockPos blockpos = BlockPos.containing(p_352225_.getEyePosition(p_352465_));
        BlockPos blockpos1 = BlockPos.containing(p_352269_.getEyePosition(p_352465_));
        int i = this.getBlockLightLevel(p_352225_, blockpos);
        int j = this.entityRenderDispatcher.getRenderer(p_352269_).getBlockLightLevel(p_352269_, blockpos1);
        int k = p_352225_.level().getBrightness(LightLayer.SKY, blockpos);
        int l = p_352225_.level().getBrightness(LightLayer.SKY, blockpos1);

        for (int i1 = 0; i1 <= 24; i1++) {
            addVertexPair(vertexconsumer, matrix4f, f, f1, f2, i, j, k, l, 0.025F, 0.025F, f5, f6, i1, false);
        }

        for (int j1 = 24; j1 >= 0; j1--) {
            addVertexPair(vertexconsumer, matrix4f, f, f1, f2, i, j, k, l, 0.025F, 0.0F, f5, f6, j1, true);
        }

        p_352205_.popPose();
    }

    private static void addVertexPair(
        VertexConsumer p_352095_,
        Matrix4f p_352142_,
        float p_352462_,
        float p_352226_,
        float p_352086_,
        int p_352406_,
        int p_352470_,
        int p_352371_,
        int p_352167_,
        float p_352293_,
        float p_352138_,
        float p_352315_,
        float p_352162_,
        int p_352291_,
        boolean p_352079_
    ) {
        float f = (float)p_352291_ / 24.0F;
        int i = (int)Mth.lerp(f, (float)p_352406_, (float)p_352470_);
        int j = (int)Mth.lerp(f, (float)p_352371_, (float)p_352167_);
        int k = LightTexture.pack(i, j);
        float f1 = p_352291_ % 2 == (p_352079_ ? 1 : 0) ? 0.7F : 1.0F;
        float f2 = 0.5F * f1;
        float f3 = 0.4F * f1;
        float f4 = 0.3F * f1;
        float f5 = p_352462_ * f;
        float f6 = p_352226_ > 0.0F ? p_352226_ * f * f : p_352226_ - p_352226_ * (1.0F - f) * (1.0F - f);
        float f7 = p_352086_ * f;
        p_352095_.addVertex(p_352142_, f5 - p_352315_, f6 + p_352138_, f7 + p_352162_).setColor(f2, f3, f4, 1.0F).setLight(k);
        p_352095_.addVertex(p_352142_, f5 + p_352315_, f6 + p_352293_ - p_352138_, f7 - p_352162_).setColor(f2, f3, f4, 1.0F).setLight(k);
    }

    protected boolean shouldShowName(T p_114504_) {
        return p_114504_.shouldShowName() || p_114504_.hasCustomName() && p_114504_ == this.entityRenderDispatcher.crosshairPickEntity;
    }

    public abstract ResourceLocation getTextureLocation(T p_114482_);

    public Font getFont() {
        return this.font;
    }

    protected void renderNameTag(T p_114498_, Component p_114499_, PoseStack p_114500_, MultiBufferSource p_114501_, int p_114502_, float p_316698_) {
        double d0 = this.entityRenderDispatcher.distanceToSqr(p_114498_);
        if (net.neoforged.neoforge.client.ClientHooks.isNameplateInRenderDistance(p_114498_, d0)) {
            Vec3 vec3 = p_114498_.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, p_114498_.getViewYRot(p_316698_));
            if (vec3 != null) {
                boolean flag = !p_114498_.isDiscrete();
                int i = "deadmau5".equals(p_114499_.getString()) ? -10 : 0;
                p_114500_.pushPose();
                p_114500_.translate(vec3.x, vec3.y + 0.5, vec3.z);
                p_114500_.mulPose(this.entityRenderDispatcher.cameraOrientation());
                p_114500_.scale(0.025F, -0.025F, 0.025F);
                Matrix4f matrix4f = p_114500_.last().pose();
                float f = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                int j = (int)(f * 255.0F) << 24;
                Font font = this.getFont();
                float f1 = (float)(-font.width(p_114499_) / 2);
                font.drawInBatch(
                    p_114499_, f1, (float)i, 553648127, false, matrix4f, p_114501_, flag ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, j, p_114502_
                );
                if (flag) {
                    font.drawInBatch(p_114499_, f1, (float)i, -1, false, matrix4f, p_114501_, Font.DisplayMode.NORMAL, 0, p_114502_);
                }

                p_114500_.popPose();
            }
        }
    }

    protected float getShadowRadius(T p_316475_) {
        return this.shadowRadius;
    }
}
