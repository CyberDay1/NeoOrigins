package com.cyberday1.neoorigins.compat.kubejs;

import net.neoforged.fml.ModList;

/**
 * Soft-dependency probe for KubeJS.
 *
 * <p>When KubeJS is installed, NeoOrigins exposes:
 * <ul>
 *   <li>An {@code NeoOriginsEvents} event group with origin / power / mob-origin
 *       / mount events for scripts to listen to.</li>
 *   <li>{@code NeoOrigins.callbacks} bindings for attaching JS handlers to
 *       built-in active powers via a {@code kubejs_callback} field.</li>
 *   <li>{@code NeoOrigins.powers} bindings for registering entirely
 *       JS-defined active power types.</li>
 * </ul>
 *
 * <p>NeoOrigins loads cleanly without KubeJS — the plugin class and all
 * KubeJS-typed classes under {@code compat.kubejs.plugin} are only
 * classloaded when {@link #isLoaded()} returns {@code true}. The static
 * {@link com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge} probes
 * this flag before delegating to KubeJS code, so unrelated power firing
 * paths pay only a single boolean check when KubeJS is absent.
 */
public final class KubeJSCompat {

    private KubeJSCompat() {}

    private static final String MOD_ID = "kubejs";

    private static Boolean cached;

    /** True if KubeJS is present at runtime. Cached after first check. */
    public static boolean isLoaded() {
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return cached;
    }
}
