package com.cyberday1.neoorigins.api.content.vfx;

/**
 * Common render-state fields for VFX entities — range, effect type,
 * lifetime, lifetime progress, partialTick.
 *
 * <p>This is the 1.21.1 variant — a plain POJO (MC 1.21.1 doesn't have
 * the {@code EntityRenderState} pattern). The 26.1 variant extends
 * {@code EntityRenderState}. Both expose the same fields so subclass
 * code compiles unchanged across versions.
 *
 * <p>On 1.21.1 the renderer populates the fields manually inside its
 * {@code render(...)} method; the engine doesn't drive state extraction
 * the way it does on 26.1. See {@link ProceduralQuadRenderer} for how
 * the base class handles this transparently for subclasses.
 *
 * <p>API status: stable. Added in 2.0.
 */
public class AbstractVfxRenderState {

    /** Radius in blocks. */
    public float range;

    /** Effect type key — look up color via {@link VfxEffectTypes#get(String)}. */
    public String effectType = "";

    /** Ticks since spawn. Combine with {@link #partialTick} for smooth time. */
    public int lifetime;

    /** 0.0–1.0 progress toward expiry. */
    public float lifetimeProgress;

    /** Fractional tick — added to {@link #lifetime} for per-frame smooth animation. */
    public float partialTick;

    /**
     * Copy the common fields from an entity into the state. Called from
     * the base renderer's render flow; subclasses typically don't need
     * to call this directly.
     */
    public static void extract(AbstractVfxEntity entity, AbstractVfxRenderState state) {
        state.range = entity.getRange();
        state.effectType = entity.getEffectType();
        state.lifetime = entity.getLifetime();
        state.lifetimeProgress = entity.getLifetimeProgress();
    }
}
