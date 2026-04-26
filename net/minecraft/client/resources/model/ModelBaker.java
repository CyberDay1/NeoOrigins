package net.minecraft.client.resources.model;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ModelBaker extends net.neoforged.neoforge.client.extensions.IModelBakerExtension {
    UnbakedModel getModel(ResourceLocation p_252194_);

    /**
     * @deprecated Forge: Use {@link #bake(ResourceLocation, ModelState, java.util.function.Function)} instead.
     */
    @Deprecated
    @Nullable
    BakedModel bake(ResourceLocation p_250776_, ModelState p_251280_);
}
