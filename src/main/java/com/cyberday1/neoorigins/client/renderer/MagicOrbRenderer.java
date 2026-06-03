package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.api.content.vfx.ProceduralQuadRenderer;
import com.cyberday1.neoorigins.content.MagicOrbProjectile;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderer for {@link MagicOrbProjectile}. Extends the shared procedural-quad
 * base — all animation is time-math on the render state, no model or
 * animation files required.
 */
@OnlyIn(Dist.CLIENT)
public class MagicOrbRenderer extends ProceduralQuadRenderer<MagicOrbProjectile, MagicOrbRenderState> {

    /** Shared 1×1 solid-white texture for the quads. Tinted per-vertex by effect color. */
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "textures/entity/magic_orb.png");

    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    public MagicOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public MagicOrbRenderState createRenderState() {
        return new MagicOrbRenderState();
    }

    @Override
    public void extractRenderState(MagicOrbProjectile entity, MagicOrbRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // MagicOrbProjectile has its own effect_type (it doesn't extend AbstractVfxEntity —
        // it extends AbstractNeoProjectile for physics). Read directly.
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
