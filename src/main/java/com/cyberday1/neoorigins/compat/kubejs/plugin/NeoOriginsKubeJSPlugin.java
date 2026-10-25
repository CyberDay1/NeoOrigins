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
        // The startup context gets the bindings whose registrations survive the
        // script reload KubeJS runs at world load; nothing re-runs a startup script.
        registry.add("NeoOrigins", registry.type().isStartup()
            ? NeoOriginsBindings.STARTUP
            : NeoOriginsBindings.INSTANCE);
    }

    /**
     * Drops what a reloadable script registered ahead of a script reload, so
     * re-registration produces a clean slate rather than stacking on top of the
     * handlers the previous load left behind, and keeps what a
     * {@code startup_scripts/} file registered: this runs for every script type, so
     * the server reload at world load lands here too, and nothing re-runs a startup
     * script. Clearing those as well made {@code NeoOrigins.registerPower} from a
     * startup script a silent no-op.
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
        KubeJSCallbacks.clearReloadable();
        JsPowerRegistry.clearReloadable();
    }
}
