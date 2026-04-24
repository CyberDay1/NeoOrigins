package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;

/**
 * Render state for {@link com.cyberday1.neoorigins.content.MagicOrbProjectile}.
 * Carries only the effect-type + lifetime fields from {@link AbstractVfxRenderState} —
 * the projectile itself has no per-tick animation state beyond what the
 * procedural renderer derives from time.
 */
public class MagicOrbRenderState extends AbstractVfxRenderState {
}
