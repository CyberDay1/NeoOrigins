package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpriteCoordinateExpander implements VertexConsumer {
    private final VertexConsumer delegate;
    private final TextureAtlasSprite sprite;

    public SpriteCoordinateExpander(VertexConsumer p_110798_, TextureAtlasSprite p_110799_) {
        this.delegate = p_110798_;
        this.sprite = p_110799_;
    }

    @Override
    public VertexConsumer addVertex(float p_350653_, float p_350607_, float p_350347_) {
        this.delegate.addVertex(p_350653_, p_350607_, p_350347_);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setColor(int p_350639_, int p_350295_, int p_350400_, int p_350645_) {
        this.delegate.setColor(p_350639_, p_350295_, p_350400_, p_350645_);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setUv(float p_350609_, float p_350452_) {
        this.delegate.setUv(this.sprite.getU(p_350609_), this.sprite.getV(p_350452_));
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setUv1(int p_351031_, int p_351048_) {
        this.delegate.setUv1(p_351031_, p_351048_);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setUv2(int p_350272_, int p_350390_) {
        this.delegate.setUv2(p_350272_, p_350390_);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setNormal(float p_350659_, float p_350518_, float p_350663_) {
        this.delegate.setNormal(p_350659_, p_350518_, p_350663_);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public void addVertex(
        float p_350724_,
        float p_350634_,
        float p_350513_,
        int p_350392_,
        float p_350322_,
        float p_350843_,
        int p_350477_,
        int p_350406_,
        float p_350536_,
        float p_350625_,
        float p_350888_
    ) {
        this.delegate
            .addVertex(
                p_350724_,
                p_350634_,
                p_350513_,
                p_350392_,
                this.sprite.getU(p_350322_),
                this.sprite.getV(p_350843_),
                p_350477_,
                p_350406_,
                p_350536_,
                p_350625_,
                p_350888_
            );
    }
}
