package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.content.vfx.ProceduralQuadRenderer;
import com.cyberday1.neoorigins.content.MagicOrbProjectile;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 variant of MagicOrbRenderer. Same public surface as the 26.1
 * variant — subclass method signatures are identical so custom subclasses
 * compile unchanged on both versions.
 */
public class MagicOrbRenderer extends ProceduralQuadRenderer<MagicOrbProjectile, MagicOrbRenderState> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "textures/entity/magic_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    public MagicOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicOrbProjectile entity) {
        return TEXTURE;
    }

    @Override
    protected MagicOrbRenderState createRenderState() {
        return new MagicOrbRenderState();
    }

    @Override
    protected void extractRenderState(MagicOrbProjectile entity, MagicOrbRenderState state, float partialTick) {
        state.effectType = entity.getEffectType();
        state.lifetime = entity.tickCount;
        state.range = 0f;
        state.lifetimeProgress = 0f;

        // Data-driven visuals (2.1): explicit synched fields override; sentinels
        // (COLOR_UNSET / negative size·alpha / empty shape) leave the base
        // renderer to fall back to effect_type → hardcoded defaults.
        int orb = entity.getOrbColor();
        state.coreColor = orb != MagicOrbProjectile.COLOR_UNSET ? unpack(orb) : null;
        int glow = entity.getGlowColor();
        state.glowColor = glow != MagicOrbProjectile.COLOR_UNSET ? unpack(glow) : null;
        state.size = entity.getSize();
        state.glowSize = entity.getGlowSize();
        state.glowAlpha = entity.getGlowAlpha();

        // Shape: explicit wins, else the effect_type's shorthand default.
        String shape = entity.getShape();
        state.shape = (shape != null && !shape.isEmpty())
            ? shape
            : com.cyberday1.neoorigins.api.content.vfx.VfxEffectTypes.defaults(state.effectType).shape();
    }

    /** Unpack a 0xRRGGBB int into {r,g,b} 0–255. */
    private static int[] unpack(int packed) {
        return new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
    }

    @Override
    protected RenderType renderType() {
        return RENDER_TYPE;
    }
}
