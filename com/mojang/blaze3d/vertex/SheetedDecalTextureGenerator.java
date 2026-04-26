package com.mojang.blaze3d.vertex;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class SheetedDecalTextureGenerator implements VertexConsumer {
    private final VertexConsumer delegate;
    private final Matrix4f cameraInversePose;
    private final Matrix3f normalInversePose;
    private final float textureScale;
    private final Vector3f worldPos = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private float x;
    private float y;
    private float z;

    public SheetedDecalTextureGenerator(VertexConsumer p_260211_, PoseStack.Pose p_324244_, float p_259312_) {
        this.delegate = p_260211_;
        this.cameraInversePose = new Matrix4f(p_324244_.pose()).invert();
        this.normalInversePose = new Matrix3f(p_324244_.normal()).invert();
        this.textureScale = p_259312_;
    }

    @Override
    public VertexConsumer addVertex(float p_350285_, float p_350727_, float p_350832_) {
        this.x = p_350285_;
        this.y = p_350727_;
        this.z = p_350832_;
        this.delegate.addVertex(p_350285_, p_350727_, p_350832_);
        return this;
    }

    @Override
    public VertexConsumer setColor(int p_350431_, int p_350486_, int p_350631_, int p_350495_) {
        this.delegate.setColor(-1);
        return this;
    }

    @Override
    public VertexConsumer setUv(float p_351041_, float p_350355_) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int p_350521_, int p_350750_) {
        this.delegate.setUv1(p_350521_, p_350750_);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int p_351012_, int p_350527_) {
        this.delegate.setUv2(p_351012_, p_350527_);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float p_350270_, float p_350794_, float p_350323_) {
        this.delegate.setNormal(p_350270_, p_350794_, p_350323_);
        Vector3f vector3f = this.normalInversePose.transform(p_350270_, p_350794_, p_350323_, this.normal);
        Direction direction = Direction.getNearest(vector3f.x(), vector3f.y(), vector3f.z());
        Vector3f vector3f1 = this.cameraInversePose.transformPosition(this.x, this.y, this.z, this.worldPos);
        vector3f1.rotateY((float) Math.PI);
        vector3f1.rotateX((float) (-Math.PI / 2));
        vector3f1.rotate(direction.getRotation());
        this.delegate.setUv(-vector3f1.x() * this.textureScale, -vector3f1.y() * this.textureScale);
        return this;
    }
}
