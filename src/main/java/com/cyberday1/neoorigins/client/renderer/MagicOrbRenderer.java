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
    }

    @Override
    protected RenderType renderType() {
        return RENDER_TYPE;
    }
}
