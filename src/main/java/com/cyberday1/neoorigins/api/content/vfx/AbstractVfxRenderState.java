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

    // ── Data-driven visual config (2.1). A renderer that supports per-entity
    // visuals (MagicOrb) populates these in extractRenderState after resolving
    // explicit JSON > effect_type defaults > hardcoded. A negative size / alpha
    // means "use the renderer's hardcoded default"; coreColor/glowColor null means
    // "fall back to resolveColor()". ──

    /** Explicit core RGB (0xRRGGBB unpacked), or null to use {@code resolveColor}. */
    public int[] coreColor;
    /** Explicit glow RGB, or null to reuse the core color. */
    public int[] glowColor;
    /** Core quad scale, or negative for the renderer default. */
    public float size = -1.0f;
    /** Glow base scale, or negative for the renderer default. */
    public float glowSize = -1.0f;
    /** Glow alpha 0–255, or negative for the renderer default. */
    public int glowAlpha = -1;
    /** Shape key: cross / cube / ring / sphere. Empty/null = cross. */
    public String shape = "";

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
