package com.cyberday1.neoorigins.compat.kubejs.plugin;

import com.cyberday1.neoorigins.compat.kubejs.JsPowerRegistry;
import com.cyberday1.neoorigins.compat.kubejs.KubeJSCallbacks;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

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
 *       {@link #clearCaches()} so stale callbacks never accumulate.</li>
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

    @Override
    public void clearCaches() {
        // Fired at the top of every script reload, for every script type — so the
        // server reload at world load lands here too. Drop what a reloadable script
        // registered, keep what startup_scripts/ did: those scripts are not re-run,
        // so clearing them made NeoOrigins.registerPower from startup a no-op.
        KubeJSCallbacks.clearReloadable();
        JsPowerRegistry.clearReloadable();
    }
}
