package net.minecraft.client.renderer.block.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BakedQuad {
    protected final int[] vertices;
    protected final int tintIndex;
    protected final Direction direction;
    protected final TextureAtlasSprite sprite;
    private final boolean shade;
    private final boolean hasAmbientOcclusion;

    public BakedQuad(int[] p_111298_, int p_111299_, Direction p_111300_, TextureAtlasSprite p_111301_, boolean p_111302_) {
        this(p_111298_, p_111299_, p_111300_, p_111301_, p_111302_, true);
    }

    public BakedQuad(int[] p_111298_, int p_111299_, Direction p_111300_, TextureAtlasSprite p_111301_, boolean p_111302_, boolean hasAmbientOcclusion) {
        this.vertices = p_111298_;
        this.tintIndex = p_111299_;
        this.direction = p_111300_;
        this.sprite = p_111301_;
        this.shade = p_111302_;
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }

    public TextureAtlasSprite getSprite() {
        return this.sprite;
    }

    public int[] getVertices() {
        return this.vertices;
    }

    public boolean isTinted() {
        return this.tintIndex != -1;
    }

    public int getTintIndex() {
        return this.tintIndex;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public boolean isShade() {
        return this.shade;
    }

    /**
     * {@return false to force-disable AO for this quad or true to use the behavior dictated by
     * {@link net.neoforged.neoforge.client.extensions.IBakedModelExtension#useAmbientOcclusion(net.minecraft.world.level.block.state.BlockState, net.neoforged.neoforge.client.model.data.ModelData, net.minecraft.client.renderer.RenderType)}}
     */
    public boolean hasAmbientOcclusion() {
        return this.hasAmbientOcclusion;
    }
}
