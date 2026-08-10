package com.cyberday1.neoorigins.compat.kubejs.plugin;

import com.cyberday1.neoorigins.compat.kubejs.JsPowerRegistry;
import com.cyberday1.neoorigins.compat.kubejs.KubeJSCallbacks;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;

/**
 * Entrypoint discovered by KubeJS via the {@code kubejs.plugins.txt}
 * resource at the jar root.
 *
 * <p>Only classloaded when KubeJS is present at runtime — KubeJS scans
 * the classpath for {@code kubejs.plugins.txt} during its own init, so
 * the file is invisible to the mod loader when KubeJS is absent.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Register the {@link NeoOriginsEvents} event group so JS scripts
 *       can hook origin/power/mob-origin/mount events.</li>
 *   <li>Expose {@link NeoOriginsBindings} as the {@code NeoOrigins} global
 *       so scripts can register JS callbacks invokable from JSON.</li>
 *   <li>Wipe the callback registry on every script reload via
 *       {@link #beforeScriptsLoaded(ScriptManager)} so stale callbacks never
 *       accumulate.</li>
 * </ul>
 */
public class NeoOriginsKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(NeoOriginsEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("NeoOrigins", NeoOriginsBindings.INSTANCE);
    }

    /**
     * Wipes both registries ahead of a script reload, so re-registration on
     * {@code StartupEvents.init} produces a clean slate rather than stacking
     * on top of the handlers the previous load left behind.
     *
     * <p>This is the KubeJS 8.x spelling of what the 1.21.1 branch does in
     * {@code KubeJSPlugin.clearCaches()}. 8.x dropped that method; the
     * replacement is not merely similar but structurally identical, and
     * deliberately so: in 7.x {@code ScriptManager.reload()} called
     * {@code clearCaches} on every plugin as its first act, and in 8.x the
     * same method calls {@code beforeScriptsLoaded} in the same position,
     * ahead of {@code loadFromDirectory} / {@code load} and the closing
     * {@code afterScriptsLoaded}. Verified against the 8.0.4 jar rather than
     * the docs. The {@code ScriptManager} argument is unused: clearing is
     * per-reload and idempotent, exactly as the 7.x hook was.
     */
    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        KubeJSCallbacks.clearAll();
        JsPowerRegistry.clearAll();
    }
}
