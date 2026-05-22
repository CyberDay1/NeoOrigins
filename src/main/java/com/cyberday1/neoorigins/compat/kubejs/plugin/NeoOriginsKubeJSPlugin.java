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
        registry.add("NeoOrigins", NeoOriginsBindings.INSTANCE);
    }

    @Override
    public void clearCaches() {
        // KubeJS reloads scripts on /kubejs reload — wipe stale handlers so
        // re-registration on StartupEvents.init produces a clean slate.
        KubeJSCallbacks.clearAll();
        JsPowerRegistry.clearAll();
    }
}
