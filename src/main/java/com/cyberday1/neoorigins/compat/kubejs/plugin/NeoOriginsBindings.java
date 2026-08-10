package com.cyberday1.neoorigins.compat.kubejs.plugin;

import com.cyberday1.neoorigins.compat.kubejs.JsActivePower;
import com.cyberday1.neoorigins.compat.kubejs.JsPassivePower;
import com.cyberday1.neoorigins.compat.kubejs.JsPowerRegistry;
import com.cyberday1.neoorigins.compat.kubejs.KubeJSCallbacks;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Methods on this object are exposed to JS as {@code NeoOrigins.*} via
 * {@link NeoOriginsKubeJSPlugin#registerBindings}.
 *
 * <p>Today it carries the callback-registry surface used by the
 * {@code neoorigins:kubejs_callback} entity action; future bindings (power
 * registration, helpers) will be added here. KubeJS-only types stay out of
 * the {@code compat/kubejs} root — this class lives under {@code plugin/}
 * so it's only classloaded when KubeJS is present.
 *
 * <p>Example use from a {@code startup_scripts/} file:
 * <pre>{@code
 * NeoOrigins.registerCallback('mypack:shout', player => {
 *     player.tell('You triggered the power!')
 * })
 * }</pre>
 */
public final class NeoOriginsBindings {

    /** Stable singleton — the binding is added once per script context. */
    public static final NeoOriginsBindings INSTANCE = new NeoOriginsBindings();

    private NeoOriginsBindings() {}

    /**
     * Registers a JS callback under the given id. Reference the same id from
     * a {@code neoorigins:kubejs_callback} action in JSON to invoke it.
     */
    public void registerCallback(String id, Consumer<ServerPlayer> callback) {
        KubeJSCallbacks.register(id, callback);
    }

    /** Removes a previously-registered callback. */
    public void unregisterCallback(String id) {
        KubeJSCallbacks.unregister(id);
    }

    /** Returns true if a callback with the given id is currently registered. */
    public boolean hasCallback(String id) {
        return KubeJSCallbacks.has(id);
    }

    // ── JS-defined power behaviors ──────────────────────────────────────
    //
    // Pair with the neoorigins:js_custom and neoorigins:js_active power
    // types: a datapack power JSON references `js_id` and the registered
    // handler supplies the lifecycle / onUse logic.

    /**
     * Registers a passive power behavior under the given id. The handler
     * is a JS object literal — Rhino auto-adapts it to {@link JsPassivePower},
     * filling in default no-ops for any hook (onGranted / onRevoked /
     * onTick) the JS object doesn't supply.
     */
    public void registerPower(String id, JsPassivePower handler) {
        JsPowerRegistry.registerPassive(id, handler);
    }

    /**
     * Registers an active (keybind) power behavior under the given id.
     * The handler must implement {@code onUse(player)} returning a boolean
     * (true = cooldown consumed, false = no-op). Optional onGranted /
     * onRevoked default to no-ops.
     */
    public void registerActivePower(String id, JsActivePower handler) {
        JsPowerRegistry.registerActive(id, handler);
    }

    public boolean hasPower(String id) {
        return JsPowerRegistry.hasPassive(id);
    }

    public boolean hasActivePower(String id) {
        return JsPowerRegistry.hasActive(id);
    }
}
