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
