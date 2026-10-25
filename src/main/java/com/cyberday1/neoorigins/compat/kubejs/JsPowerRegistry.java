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
 * <p>What a reloadable script (server, client) registers is wiped on every script
 * reload via the plugin's {@code clearCaches} hook, so stale behaviors never
 * accumulate. What a {@code startup_scripts/} file registers is kept: those scripts
 * run once per game session, so dropping their handlers when the server scripts
 * reload at world load left them permanently unregistered.
 */
public final class JsPowerRegistry {

    private JsPowerRegistry() {}

    private static final Map<String, JsPassivePower> PASSIVE = new ConcurrentHashMap<>();
    private static final Map<String, JsActivePower>  ACTIVE  = new ConcurrentHashMap<>();

    // Ids registered from startup_scripts/, which a reload must not drop.
    private static final java.util.Set<String> PASSIVE_KEPT = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> ACTIVE_KEPT  = ConcurrentHashMap.newKeySet();

    // ── Passive ─────────────────────────────────────────────────────────

    public static void registerPassive(String id, JsPassivePower handler) {
        registerPassive(id, handler, false);
    }

    /** {@code fromStartup} marks a registration a script reload must not drop. */
    public static void registerPassive(String id, JsPassivePower handler, boolean fromStartup) {
        if (id == null || id.isEmpty() || handler == null) return;
        PASSIVE.put(id, handler);
        if (fromStartup) PASSIVE_KEPT.add(id); else PASSIVE_KEPT.remove(id);
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
        registerActive(id, handler, false);
    }

    /** {@code fromStartup} marks a registration a script reload must not drop. */
    public static void registerActive(String id, JsActivePower handler, boolean fromStartup) {
        if (id == null || id.isEmpty() || handler == null) return;
        ACTIVE.put(id, handler);
        if (fromStartup) ACTIVE_KEPT.add(id); else ACTIVE_KEPT.remove(id);
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

    /**
     * Drops what a reloadable script registered and keeps what {@code startup_scripts/}
     * registered. Called from the plugin's clearCaches hook, which KubeJS fires at the
     * top of every script reload: the server reload at world load would otherwise take
     * the startup handlers with it, and nothing re-runs a startup script.
     */
    public static void clearReloadable() {
        PASSIVE.keySet().removeIf(id -> !PASSIVE_KEPT.contains(id));
        ACTIVE.keySet().removeIf(id -> !ACTIVE_KEPT.contains(id));
    }

    /** Wipes every registered handler, startup registrations included. */
    public static void clearAll() {
        PASSIVE.clear();
        ACTIVE.clear();
        PASSIVE_KEPT.clear();
        ACTIVE_KEPT.clear();
    }
}
