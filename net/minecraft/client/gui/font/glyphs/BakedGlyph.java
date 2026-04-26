package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class BakedGlyph {
    private final GlyphRenderTypes renderTypes;
    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;
    private final float left;
    private final float right;
    private final float up;
    private final float down;

    public BakedGlyph(
        GlyphRenderTypes p_285527_,
        float p_285271_,
        float p_284970_,
        float p_285098_,
        float p_285023_,
        float p_285242_,
        float p_285043_,
        float p_285100_,
        float p_284948_
    ) {
        this.renderTypes = p_285527_;
        this.u0 = p_285271_;
        this.u1 = p_284970_;
        this.v0 = p_285098_;
        this.v1 = p_285023_;
        this.left = p_285242_;
        this.right = p_285043_;
        this.up = p_285100_;
        this.down = p_284948_;
    }

    public void render(
        boolean p_95227_,
        float p_95228_,
        float p_95229_,
        Matrix4f p_253706_,
        VertexConsumer p_95231_,
        float p_95232_,
        float p_95233_,
        float p_95234_,
        float p_95235_,
        int p_95236_
    ) {
        float f = p_95228_ + this.left;
        float f1 = p_95228_ + this.right;
        float f2 = p_95229_ + this.up;
        float f3 = p_95229_ + this.down;
        float f4 = p_95227_ ? 1.0F - 0.25F * this.up : 0.0F;
        float f5 = p_95227_ ? 1.0F - 0.25F * this.down : 0.0F;
        p_95231_.addVertex(p_253706_, f + f4, f2, 0.0F).setColor(p_95232_, p_95233_, p_95234_, p_95235_).setUv(this.u0, this.v0).setLight(p_95236_);
        p_95231_.addVertex(p_253706_, f + f5, f3, 0.0F).setColor(p_95232_, p_95233_, p_95234_, p_95235_).setUv(this.u0, this.v1).setLight(p_95236_);
        p_95231_.addVertex(p_253706_, f1 + f5, f3, 0.0F).setColor(p_95232_, p_95233_, p_95234_, p_95235_).setUv(this.u1, this.v1).setLight(p_95236_);
        p_95231_.addVertex(p_253706_, f1 + f4, f2, 0.0F).setColor(p_95232_, p_95233_, p_95234_, p_95235_).setUv(this.u1, this.v0).setLight(p_95236_);
    }

    public void renderEffect(BakedGlyph.Effect p_95221_, Matrix4f p_254370_, VertexConsumer p_95223_, int p_95224_) {
        p_95223_.addVertex(p_254370_, p_95221_.x0, p_95221_.y0, p_95221_.depth)
            .setColor(p_95221_.r, p_95221_.g, p_95221_.b, p_95221_.a)
            .setUv(this.u0, this.v0)
            .setLight(p_95224_);
        p_95223_.addVertex(p_254370_, p_95221_.x1, p_95221_.y0, p_95221_.depth)
            .setColor(p_95221_.r, p_95221_.g, p_95221_.b, p_95221_.a)
            .setUv(this.u0, this.v1)
            .setLight(p_95224_);
        p_95223_.addVertex(p_254370_, p_95221_.x1, p_95221_.y1, p_95221_.depth)
            .setColor(p_95221_.r, p_95221_.g, p_95221_.b, p_95221_.a)
            .setUv(this.u1, this.v1)
            .setLight(p_95224_);
        p_95223_.addVertex(p_254370_, p_95221_.x0, p_95221_.y1, p_95221_.depth)
            .setColor(p_95221_.r, p_95221_.g, p_95221_.b, p_95221_.a)
            .setUv(this.u1, this.v0)
            .setLight(p_95224_);
    }

    public RenderType renderType(Font.DisplayMode p_181388_) {
        return this.renderTypes.select(p_181388_);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Effect {
        protected final float x0;
        protected final float y0;
        protected final float x1;
        protected final float y1;
        protected final float depth;
        protected final float r;
        protected final float g;
        protected final float b;
        protected final float a;

        public Effect(
            float p_95247_, float p_95248_, float p_95249_, float p_95250_, float p_95251_, float p_95252_, float p_95253_, float p_95254_, float p_95255_
        ) {
            this.x0 = p_95247_;
            this.y0 = p_95248_;
            this.x1 = p_95249_;
            this.y1 = p_95250_;
            this.depth = p_95251_;
            this.r = p_95252_;
            this.g = p_95253_;
            this.b = p_95254_;
            this.a = p_95255_;
        }
    }
}
