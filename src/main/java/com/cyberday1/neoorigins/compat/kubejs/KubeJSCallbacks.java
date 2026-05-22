package com.cyberday1.neoorigins.compat.kubejs;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Always-loaded registry of JS-registered callbacks invokable from JSON via
 * the {@code neoorigins:kubejs_callback} entity action.
 *
 * <p>The registry stores plain {@link Consumer}s of {@link ServerPlayer}.
 * KubeJS auto-adapts a JS function to a {@code Consumer} when it crosses the
 * binding boundary, so this class has zero KubeJS imports — it works
 * identically whether KubeJS is present or not. If KubeJS is absent, the map
 * stays empty and every {@link #invoke} is a no-op.
 *
 * <p>The registry is wiped on every KubeJS script reload (via the plugin's
 * {@code clearCaches()} hook) so stale callbacks never accumulate. Pack
 * authors should re-register on {@code StartupEvents.init} or
 * {@code ServerEvents.loaded}.
 */
public final class KubeJSCallbacks {

    private KubeJSCallbacks() {}

    private static final Map<String, Consumer<ServerPlayer>> REGISTRY = new ConcurrentHashMap<>();

    /** Registers a JS callback under the given id. A repeat id overwrites. */
    public static void register(String id, Consumer<ServerPlayer> callback) {
        if (id == null || id.isEmpty() || callback == null) return;
        REGISTRY.put(id, callback);
    }

    /** Unregisters a previously-registered callback. */
    public static void unregister(String id) {
        if (id == null) return;
        REGISTRY.remove(id);
    }

    /** Returns true if a callback is registered under the given id. */
    public static boolean has(String id) {
        return id != null && REGISTRY.containsKey(id);
    }

    /**
     * Invokes the callback registered under {@code id}. If no callback is
     * registered (typical when KubeJS is absent, or the script hasn't
     * registered it yet), the call is silently dropped with a debug log.
     * Exceptions thrown from JS are caught and logged so a misbehaving
     * script can't tear down the calling power.
     */
    public static void invoke(String id, ServerPlayer player) {
        Consumer<ServerPlayer> cb = REGISTRY.get(id);
        if (cb == null) {
            NeoOrigins.LOGGER.debug("[kubejs] no callback registered for id '{}'", id);
            return;
        }
        try {
            cb.accept(player);
        } catch (RuntimeException e) {
            NeoOrigins.LOGGER.error("[kubejs] callback '{}' threw", id, e);
        }
    }

    /** Wipes every callback. Called from the plugin's clearCaches hook on reload. */
    public static void clearAll() {
        REGISTRY.clear();
    }
}
