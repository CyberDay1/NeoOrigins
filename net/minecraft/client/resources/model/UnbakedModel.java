package net.minecraft.client.resources.model;

import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface UnbakedModel {
    Collection<ResourceLocation> getDependencies();

    void resolveParents(Function<ResourceLocation, UnbakedModel> p_119538_);

    @Nullable
    BakedModel bake(ModelBaker p_250133_, Function<Material, TextureAtlasSprite> p_119535_, ModelState p_119536_);
}
