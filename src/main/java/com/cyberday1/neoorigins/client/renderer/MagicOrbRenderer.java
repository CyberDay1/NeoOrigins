package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.content.vfx.ProceduralQuadRenderer;
import com.cyberday1.neoorigins.content.MagicOrbProjectile;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 1.21.1 variant of MagicOrbRenderer. Same public surface as the 26.1
 * variant — subclass method signatures are identical so custom subclasses
 * compile unchanged on both versions.
 */
@OnlyIn(Dist.CLIENT)
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
    }

    @Override
    protected RenderType renderType() {
        return RENDER_TYPE;
    }
}
