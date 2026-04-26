package com.mojang.blaze3d.vertex;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

@OnlyIn(Dist.CLIENT)
public interface VertexConsumer extends net.neoforged.neoforge.client.extensions.IVertexConsumerExtension {
    VertexConsumer addVertex(float p_350761_, float p_350704_, float p_350711_);

    VertexConsumer setColor(int p_350535_, int p_350875_, int p_350886_, int p_350775_);

    VertexConsumer setUv(float p_350572_, float p_350917_);

    VertexConsumer setUv1(int p_350815_, int p_350629_);

    VertexConsumer setUv2(int p_350859_, int p_351004_);

    VertexConsumer setNormal(float p_350429_, float p_350286_, float p_350836_);

    default void addVertex(
        float p_351049_,
        float p_350528_,
        float p_351018_,
        int p_350427_,
        float p_350508_,
        float p_350864_,
        int p_350846_,
        int p_350731_,
        float p_350784_,
        float p_351051_,
        float p_350759_
    ) {
        this.addVertex(p_351049_, p_350528_, p_351018_);
        this.setColor(p_350427_);
        this.setUv(p_350508_, p_350864_);
        this.setOverlay(p_350846_);
        this.setLight(p_350731_);
        this.setNormal(p_350784_, p_351051_, p_350759_);
    }

    default VertexConsumer setColor(float p_350350_, float p_350356_, float p_350623_, float p_350312_) {
        return this.setColor((int)(p_350350_ * 255.0F), (int)(p_350356_ * 255.0F), (int)(p_350623_ * 255.0F), (int)(p_350312_ * 255.0F));
    }

    default VertexConsumer setColor(int p_350809_) {
        return this.setColor(
            FastColor.ARGB32.red(p_350809_), FastColor.ARGB32.green(p_350809_), FastColor.ARGB32.blue(p_350809_), FastColor.ARGB32.alpha(p_350809_)
        );
    }

    default VertexConsumer setWhiteAlpha(int p_350979_) {
        return this.setColor(FastColor.ARGB32.color(p_350979_, -1));
    }

    default VertexConsumer setLight(int p_350855_) {
        return this.setUv2(p_350855_ & 65535, p_350855_ >> 16 & 65535);
    }

    default VertexConsumer setOverlay(int p_350697_) {
        return this.setUv1(p_350697_ & 65535, p_350697_ >> 16 & 65535);
    }

    default void putBulkData(
        PoseStack.Pose p_85996_, BakedQuad p_85997_, float p_85999_, float p_86000_, float p_86001_, float p_331520_, int p_86003_, int p_331548_
    ) {
        this.putBulkData(
            p_85996_,
            p_85997_,
            new float[]{1.0F, 1.0F, 1.0F, 1.0F},
            p_85999_,
            p_86000_,
            p_86001_,
            p_331520_,
            new int[]{p_86003_, p_86003_, p_86003_, p_86003_},
            p_331548_,
            false
        );
    }

    default void putBulkData(
        PoseStack.Pose p_85988_,
        BakedQuad p_85989_,
        float[] p_331397_,
        float p_85990_,
        float p_85991_,
        float p_85992_,
        float p_331416_,
        int[] p_331378_,
        int p_85993_,
        boolean p_331268_
    ) {
        int[] aint = p_85989_.getVertices();
        Vec3i vec3i = p_85989_.getDirection().getNormal();
        Matrix4f matrix4f = p_85988_.pose();
        Vector3f vector3f = p_85988_.transformNormal((float)vec3i.getX(), (float)vec3i.getY(), (float)vec3i.getZ(), new Vector3f());
        int i = 8;
        int j = aint.length / 8;
        int k = (int)(p_331416_ * 255.0F);

        try (MemoryStack memorystack = MemoryStack.stackPush()) {
            ByteBuffer bytebuffer = memorystack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer intbuffer = bytebuffer.asIntBuffer();

            for (int l = 0; l < j; l++) {
                intbuffer.clear();
                intbuffer.put(aint, l * 8, 8);
                float f = bytebuffer.getFloat(0);
                float f1 = bytebuffer.getFloat(4);
                float f2 = bytebuffer.getFloat(8);
                float f3;
                float f4;
                float f5;
                if (p_331268_) {
                    float f6 = (float)(bytebuffer.get(12) & 255);
                    float f7 = (float)(bytebuffer.get(13) & 255);
                    float f8 = (float)(bytebuffer.get(14) & 255);
                    f3 = f6 * p_331397_[l] * p_85990_;
                    f4 = f7 * p_331397_[l] * p_85991_;
                    f5 = f8 * p_331397_[l] * p_85992_;
                } else {
                    f3 = p_331397_[l] * p_85990_ * 255.0F;
                    f4 = p_331397_[l] * p_85991_ * 255.0F;
                    f5 = p_331397_[l] * p_85992_ * 255.0F;
                }

                // Neo: also apply alpha that's coming from the baked quad
                int vertexAlpha = p_331268_ ? (int)((p_331416_ * (float) (bytebuffer.get(15) & 255) / 255.0F) * 255) : k;
                int i1 = FastColor.ARGB32.color(vertexAlpha, (int)f3, (int)f4, (int)f5);
                int j1 = applyBakedLighting(p_331378_[l], bytebuffer);
                float f10 = bytebuffer.getFloat(16);
                float f9 = bytebuffer.getFloat(20);
                Vector3f vector3f1 = matrix4f.transformPosition(f, f1, f2, new Vector3f());
                applyBakedNormals(vector3f, bytebuffer, p_85988_.normal());
                this.addVertex(vector3f1.x(), vector3f1.y(), vector3f1.z(), i1, f10, f9, p_85993_, j1, vector3f.x(), vector3f.y(), vector3f.z());
            }
        }
    }

    default VertexConsumer addVertex(Vector3f p_350685_) {
        return this.addVertex(p_350685_.x(), p_350685_.y(), p_350685_.z());
    }

    default VertexConsumer addVertex(PoseStack.Pose p_352288_, Vector3f p_352298_) {
        return this.addVertex(p_352288_, p_352298_.x(), p_352298_.y(), p_352298_.z());
    }

    default VertexConsumer addVertex(PoseStack.Pose p_350506_, float p_350934_, float p_350873_, float p_350981_) {
        return this.addVertex(p_350506_.pose(), p_350934_, p_350873_, p_350981_);
    }

    default VertexConsumer addVertex(Matrix4f p_350929_, float p_350884_, float p_350885_, float p_350942_) {
        Vector3f vector3f = p_350929_.transformPosition(p_350884_, p_350885_, p_350942_, new Vector3f());
        return this.addVertex(vector3f.x(), vector3f.y(), vector3f.z());
    }

    default VertexConsumer setNormal(PoseStack.Pose p_350592_, float p_350534_, float p_350411_, float p_350441_) {
        Vector3f vector3f = p_350592_.transformNormal(p_350534_, p_350411_, p_350441_, new Vector3f());
        return this.setNormal(vector3f.x(), vector3f.y(), vector3f.z());
    }
}
