package com.cyberday1.neoorigins.compat;

import net.neoforged.fml.ModList;

/**
 * Soft-dependency probe for GeckoLib.
 *
 * <p>GeckoLib is a runtime-optional dependency that, when present,
 * unlocks animated custom projectiles and other renderers. NeoOrigins
 * loads cleanly without it — missing GeckoLib means pack-authored
 * animated projectiles fall back to the item-based visual from
 * {@link com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile#getVisualItem()}.
 *
 * <p>This class is server-safe: it only reads {@link ModList}, which
 * exists on both dist. Any code that actually touches GeckoLib classes
 * (renderers, animation controllers) must be gated on {@link #isLoaded()}
 * and lazily loaded — do not import GeckoLib types at the top of a class
 * that might get classloaded without the mod present.
 *
 * <p>Future work: when we ship {@code AnimatedProjectileRenderer},
 * register it only if {@code isLoaded()} is true, and provide a fallback
 * {@link net.minecraft.client.renderer.entity.ThrownItemRenderer} branch
 * for the no-GeckoLib case.
 */
public final class GeckoLibCompat {

    private GeckoLibCompat() {}

    private static final String MOD_ID = "geckolib";

    private static Boolean cached;

    /** True if GeckoLib is present at runtime. Cached after first check. */
    public static boolean isLoaded() {
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return cached;
    }
}
