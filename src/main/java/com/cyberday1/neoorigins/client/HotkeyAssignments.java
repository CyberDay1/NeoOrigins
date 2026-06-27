package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side state for the named-hotkey system.
 *
 * <p>The server sends {@link com.cyberday1.neoorigins.network.payload.SyncKeybindRegistryPayload}
 * on login and after every reload; this class consumes the snapshot and routes
 * each declared translation key to either:
 * <ul>
 *   <li>An anonymous pool slot from {@link NeoOriginsKeybindings#HOTKEY_POOL} —
 *       the default native path. The player rebinds it in Controls just like any
 *       other key.</li>
 *   <li>An <b>external</b> {@link KeyMapping} (e.g. one registered by
 *       <i>keybindjs</i>) that already exists with the same translation key.
 *       This avoids duplicate entries in the Controls screen when a pack ships
 *       both a JSON {@code "key"} declaration and a keybindjs script.</li>
 * </ul>
 *
 * <p>The poll loop in {@link NeoOriginsClientEvents} reads {@link #poolKey(int)}
 * for each pool index, and walks {@link #externalMappings()} for keybindjs-owned
 * bindings.
 */
public final class HotkeyAssignments {

    private HotkeyAssignments() {}

    /** Pool slot index → translation key currently assigned to that slot. null = empty. */
    private static volatile String[] poolAssignments = new String[0];

    /** Translation key → "continuous" flag (fire while held). */
    private static volatile Map<String, Boolean> continuousByKey = Map.of();

    /** Power id → translation key, for in-game UI hints (origin info screen, etc.). */
    private static volatile Map<Identifier, String> powerToKey = Map.of();

    /** External (keybindjs-owned) mappings: their KeyMapping → translation key. */
    private static volatile Map<KeyMapping, String> externalAssignments = Collections.emptyMap();

    /**
     * Apply a fresh registry snapshot. Runs on the client thread (enqueued by
     * the payload handler), so it's safe to touch KeyMapping state.
     *
     * <p>Stable ordering: keys come sorted from the server, so a player who
     * relogs sees the same key on the same slot — unless they're already in
     * the Controls screen rebinding mid-session, which they'd notice anyway.
     */
    public static void set(List<String> declaredKeys,
                           List<Boolean> continuousFlags,
                           Map<Identifier, String> p2k) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) return;

        Map<String, Boolean> cmap = new HashMap<>(declaredKeys.size());
        for (int i = 0; i < declaredKeys.size(); i++) {
            cmap.put(declaredKeys.get(i), continuousFlags.get(i));
        }
        continuousByKey = Map.copyOf(cmap);
        powerToKey = Map.copyOf(p2k);

        KeyMapping[] pool = NeoOriginsKeybindings.HOTKEY_POOL;
        String[] slots = new String[pool.length];
        Map<KeyMapping, String> external = new HashMap<>();

        // External-mapping lookup. Build a single (translationKey -> KeyMapping) view
        // for keybindjs-registered keys so we don't burn pool slots on duplicates.
        Map<String, KeyMapping> externalCandidates = collectExternalCandidates(pool);

        int slotIdx = 0;
        for (String key : declaredKeys) {
            // The two vanilla toolbar (creative save/load hotbar) keys are driven
            // by their real vanilla KeyMapping in the client tick loop, not by a
            // NeoOrigins pool slot — so don't consume a pool slot for them.
            if (isVanillaToolbarKey(key)) continue;
            KeyMapping ext = externalCandidates.get(key);
            if (ext != null) {
                external.put(ext, key);
                continue;
            }
            if (slotIdx >= pool.length) {
                NeoOrigins.LOGGER.warn(
                    "[Hotkeys] Pool exhausted ({} slots) — key '{}' will be dormant. "
                        + "Increase config/neoorigins/client.toml [hotkeys] pool_size or assign via keybindjs.",
                    pool.length, key);
                continue;
            }
            slots[slotIdx++] = key;
        }

        poolAssignments = slots;
        externalAssignments = Map.copyOf(external);

        NeoOrigins.LOGGER.info(
            "[Hotkeys] Assigned {} pool slots + {} external keybindjs slots (pool size: {})",
            slotIdx, external.size(), pool.length);
    }

    /**
     * Walk the client's current KeyMapping list and find any registered with one
     * of our declared translation keys that ISN'T one of our pool slots. Those
     * are externally registered (almost always by keybindjs) and we should
     * defer to them instead of double-binding.
     *
     * <p>Gated on {@code keybindjs} being loaded — when it isn't, the array walk
     * has no candidates anyway, so we skip the iteration cost entirely. (Other
     * mods could conceivably also register here; the gate is a soft hint, not
     * a correctness requirement.)
     */
    private static Map<String, KeyMapping> collectExternalCandidates(KeyMapping[] pool) {
        if (!ModList.get().isLoaded("keybindjs")) return Map.of();

        java.util.Set<KeyMapping> poolSet = new java.util.HashSet<>();
        Collections.addAll(poolSet, pool);

        Map<String, KeyMapping> out = new HashMap<>();
        try {
            for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
                if (poolSet.contains(km)) continue;
                String name = km.getName();
                if (continuousByKey.containsKey(name)) {
                    out.put(name, km);
                }
            }
        } catch (RuntimeException e) {
            // keyMappings is normally well-formed, but a misbehaving keybind
            // mod could throw on iteration. Don't let it kill the assignment.
            NeoOrigins.LOGGER.warn("[Hotkeys] Failed scanning keyMappings for external bindings", e);
        }
        return out;
    }

    /** Translation key bound to pool slot {@code i}, or null if the slot is empty. */
    public static String poolKey(int i) {
        String[] snap = poolAssignments;
        return (i >= 0 && i < snap.length) ? snap[i] : null;
    }

    /** True if any binding for this translation key fires continuously while held. */
    public static boolean isContinuous(String translationKey) {
        return Boolean.TRUE.equals(continuousByKey.get(translationKey));
    }

    /** Translation keys for the two vanilla creative toolbar (save/load hotbar) keys. */
    public static final String SAVE_TOOLBAR_KEY = "key.saveToolbarActivator";
    public static final String LOAD_TOOLBAR_KEY = "key.loadToolbarActivator";

    /** True if the translation key is one of the vanilla creative toolbar keys. */
    public static boolean isVanillaToolbarKey(String translationKey) {
        return SAVE_TOOLBAR_KEY.equals(translationKey) || LOAD_TOOLBAR_KEY.equals(translationKey);
    }

    /** True if at least one compat power is bound to the given vanilla toolbar key. */
    public static boolean isToolbarKeyDeclared(String translationKey) {
        return continuousByKey.containsKey(translationKey);
    }

    /** Snapshot of external (keybindjs-owned) mappings to poll alongside the pool. */
    public static Map<KeyMapping, String> externalMappings() {
        return externalAssignments;
    }

    /** Translation key bound to a granted power id (for UI tooltips). */
    public static String keyForPower(Identifier powerId) {
        return powerToKey.get(powerId);
    }

    /** Vanilla input keys a compat active_self power can bind to → the vanilla
     *  KeyMapping the player can rebind. Used to label such powers in the UI. */
    private static KeyMapping vanillaMapping(String key) {
        var opts = Minecraft.getInstance().options;
        return switch (key) {
            case "key.use"     -> opts.keyUse;
            case "key.attack"  -> opts.keyAttack;
            case "key.sneak"   -> opts.keyShift;
            case "key.jump"    -> opts.keyJump;
            case "key.sprint"  -> opts.keySprint;
            case "key.forward" -> opts.keyUp;
            case "key.back"    -> opts.keyDown;
            case "key.left"    -> opts.keyLeft;
            case "key.right"   -> opts.keyRight;
            default -> null;
        };
    }

    /** The KeyMapping a named (pack-declared) translation key resolved to — its
     *  pool slot or an external keybindjs mapping. Null if not assigned. */
    private static KeyMapping namedMapping(String translationKey) {
        String[] snap = poolAssignments;
        for (int i = 0; i < snap.length; i++) {
            if (translationKey.equals(snap[i])) return NeoOriginsKeybindings.HOTKEY_POOL[i];
        }
        for (var e : externalAssignments.entrySet()) {
            if (translationKey.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    /**
     * Display name of the physical key bound to a power's named-hotkey or
     * vanilla-input-key activation (e.g. "R", "Right Click"), for the origin info
     * screen. Returns {@code null} when the power has no such binding, so callers
     * fall back to the skill-slot tag (or no tag for true passives).
     */
    public static String displayKeyForPower(Identifier powerId) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) return null;
        String key = powerToKey.get(powerId);
        if (key == null) return null;
        KeyMapping km = key.startsWith("key.") ? vanillaMapping(key) : namedMapping(key);
        if (km == null) return null;
        String s = km.getTranslatedKeyMessage().getString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Clear assignments — call on world-disconnect so a stale map can't fire on a new server. */
    public static void clear() {
        poolAssignments = new String[0];
        continuousByKey = Map.of();
        powerToKey = Map.of();
        externalAssignments = Map.of();
    }
}
