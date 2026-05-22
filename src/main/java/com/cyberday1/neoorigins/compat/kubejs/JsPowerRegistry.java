package com.cyberday1.neoorigins.compat.kubejs;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Always-loaded registry of JS-defined power behaviors looked up by the
 * {@code js_id} field on {@code neoorigins:js_custom} (passive) and
 * {@code neoorigins:js_active} JSON powers.
 *
 * <p>Pack authors register behaviors from JS:
 * <pre>{@code
 * NeoOrigins.registerPower('mypack:shadow_form', {
 *     onGranted: player => player.tell('embraced the shadow'),
 *     onRevoked: player => player.tell('the shadow fades'),
 *     onTick: player => { ... }
 * })
 * }</pre>
 *
 * <p>and reference them from a datapack power JSON:
 * <pre>{@code
 * {"type": "neoorigins:js_custom", "js_id": "mypack:shadow_form"}
 * }</pre>
 *
 * <p>Wiped on every {@code /kubejs reload} via the plugin's
 * {@code clearCaches} hook so stale behaviors never accumulate.
 */
public final class JsPowerRegistry {

    private JsPowerRegistry() {}

    private static final Map<String, JsPassivePower> PASSIVE = new ConcurrentHashMap<>();
    private static final Map<String, JsActivePower>  ACTIVE  = new ConcurrentHashMap<>();

    // ── Passive ─────────────────────────────────────────────────────────

    public static void registerPassive(String id, JsPassivePower handler) {
        if (id == null || id.isEmpty() || handler == null) return;
        PASSIVE.put(id, handler);
    }

    public static boolean hasPassive(String id) {
        return id != null && PASSIVE.containsKey(id);
    }

    public static void invokeOnGranted(String id, ServerPlayer player) {
        JsPassivePower h = PASSIVE.get(id);
        if (h == null) {
            NeoOrigins.LOGGER.debug("[kubejs] no passive power handler for '{}'", id);
            return;
        }
        try { h.onGranted(player); }
        catch (RuntimeException e) { NeoOrigins.LOGGER.error("[kubejs] passive '{}' onGranted threw", id, e); }
    }

    public static void invokeOnRevoked(String id, ServerPlayer player) {
        JsPassivePower h = PASSIVE.get(id);
        if (h == null) return;
        try { h.onRevoked(player); }
        catch (RuntimeException e) { NeoOrigins.LOGGER.error("[kubejs] passive '{}' onRevoked threw", id, e); }
    }

    public static void invokeOnTick(String id, ServerPlayer player) {
        JsPassivePower h = PASSIVE.get(id);
        if (h == null) return;
        try { h.onTick(player); }
        catch (RuntimeException e) { NeoOrigins.LOGGER.error("[kubejs] passive '{}' onTick threw", id, e); }
    }

    // ── Active ──────────────────────────────────────────────────────────

    public static void registerActive(String id, JsActivePower handler) {
        if (id == null || id.isEmpty() || handler == null) return;
        ACTIVE.put(id, handler);
    }

    public static boolean hasActive(String id) {
        return id != null && ACTIVE.containsKey(id);
    }

    /**
     * Invokes the active power's onUse. Returns {@code false} if no handler
     * is registered (treated as "nothing happened" — no cooldown consumed)
     * or if the handler threw.
     */
    public static boolean invokeOnUse(String id, ServerPlayer player) {
        JsActivePower h = ACTIVE.get(id);
        if (h == null) {
            NeoOrigins.LOGGER.debug("[kubejs] no active power handler for '{}'", id);
            return false;
        }
        try { return h.onUse(player); }
        catch (RuntimeException e) {
            NeoOrigins.LOGGER.error("[kubejs] active '{}' onUse threw", id, e);
            return false;
        }
    }

    public static void invokeActiveOnGranted(String id, ServerPlayer player) {
        JsActivePower h = ACTIVE.get(id);
        if (h == null) return;
        try { h.onGranted(player); }
        catch (RuntimeException e) { NeoOrigins.LOGGER.error("[kubejs] active '{}' onGranted threw", id, e); }
    }

    public static void invokeActiveOnRevoked(String id, ServerPlayer player) {
        JsActivePower h = ACTIVE.get(id);
        if (h == null) return;
        try { h.onRevoked(player); }
        catch (RuntimeException e) { NeoOrigins.LOGGER.error("[kubejs] active '{}' onRevoked threw", id, e); }
    }

    /** Wipes every registered handler. Called from the plugin's clearCaches hook on reload. */
    public static void clearAll() {
        PASSIVE.clear();
        ACTIVE.clear();
    }
}
