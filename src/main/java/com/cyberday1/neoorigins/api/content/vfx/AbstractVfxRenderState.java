package com.cyberday1.neoorigins.api.content.vfx;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Common render-state fields for VFX entities — range, effect type,
 * lifetime, lifetime progress. Subclasses add fields for entity-specific
 * state (pull strength, charge progress, etc.).
 *
 * <p>Populated in an extractRenderState override:
 * <pre>{@code
 * @Override
 * public void extractRenderState(MyVfxEntity entity, MyVfxRenderState state, float partialTick) {
 *     super.extractRenderState(entity, state, partialTick);
 *     AbstractVfxRenderState.extract(entity, state);  // fills common fields
 *     state.myCustomField = entity.getCustomField();
 * }
 * }</pre>
 *
 * <p>API status: stable. Added in 2.0.
 */
public class AbstractVfxRenderState extends EntityRenderState {

    /** Radius in blocks. */
    public float range;

    /** Effect type key — look up color via {@link VfxEffectTypes#get(String)}. */
    public String effectType = "";

    /** Ticks since spawn. Combine with {@link EntityRenderState#partialTick} for smooth time. */
    public int lifetime;

    /** 0.0–1.0 progress toward expiry. Useful for fade-out. */
    public float lifetimeProgress;

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
     * Copy the common fields from {@code entity} into {@code state}. Call from
     * your renderer's {@code extractRenderState} override after {@code super.extractRenderState(...)}.
     */
    public static void extract(AbstractVfxEntity entity, AbstractVfxRenderState state) {
        state.range = entity.getRange();
        state.effectType = entity.getEffectType();
        state.lifetime = entity.getLifetime();
        state.lifetimeProgress = entity.getLifetimeProgress();
    }
}
