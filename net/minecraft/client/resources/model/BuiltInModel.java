package net.minecraft.client.resources.model;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BuiltInModel implements BakedModel {
    private final ItemTransforms itemTransforms;
    private final ItemOverrides overrides;
    private final TextureAtlasSprite particleTexture;
    private final boolean usesBlockLight;

    public BuiltInModel(ItemTransforms p_119172_, ItemOverrides p_119173_, TextureAtlasSprite p_119174_, boolean p_119175_) {
        this.itemTransforms = p_119172_;
        this.overrides = p_119173_;
        this.particleTexture = p_119174_;
        this.usesBlockLight = p_119175_;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState p_235043_, @Nullable Direction p_235044_, RandomSource p_235045_) {
        return Collections.emptyList();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return this.usesBlockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.particleTexture;
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.itemTransforms;
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.overrides;
    }
}
