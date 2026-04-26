package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class Model {
    protected final Function<ResourceLocation, RenderType> renderType;

    public Model(Function<ResourceLocation, RenderType> p_103110_) {
        this.renderType = p_103110_;
    }

    public final RenderType renderType(ResourceLocation p_103120_) {
        return this.renderType.apply(p_103120_);
    }

    public abstract void renderToBuffer(PoseStack p_103111_, VertexConsumer p_103112_, int p_103113_, int p_103114_, int p_350308_);

    public final void renderToBuffer(PoseStack p_350804_, VertexConsumer p_350976_, int p_350539_, int p_350374_) {
        this.renderToBuffer(p_350804_, p_350976_, p_350539_, p_350374_, -1);
    }
}
