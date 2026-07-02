package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player state attachments for Route B powers:
 *   ResourceState — integer resource bar values keyed by power ID string
 *   ToggleState   — boolean toggle states keyed by power ID string
 */
public class CompatAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NeoOrigins.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceState>> RESOURCE_STATE =
        ATTACHMENT_TYPES.register("resource_state", () ->
            AttachmentType.builder(ResourceState::new)
                .serialize(ResourceState.CODEC)
                // Carry resource values across death — otherwise the entire map
                // is wiped on respawn and syncResourcesToClient skips the bar
                // because state.getAll() has no entry. Reported as part of
                // GitHub #90 (Voidwalker energy bar disappears).
                .copyOnDeath()
                .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ToggleState>> TOGGLE_STATE =
        ATTACHMENT_TYPES.register("toggle_state", () ->
            AttachmentType.builder(ToggleState::new)
                .serialize(ToggleState.CODEC)
                .copyOnDeath()
                .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static AttachmentType<ResourceState> resourceState() { return RESOURCE_STATE.get(); }
    public static AttachmentType<ToggleState>   toggleState()   { return TOGGLE_STATE.get(); }

    // ---- ResourceState ----

    public static class ResourceState {
        private final Map<String, Integer> values = new HashMap<>();

        public static final Codec<ResourceState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                .optionalFieldOf("values", Map.of())
                .forGetter(s -> Map.copyOf(s.values))
        ).apply(inst, map -> {
            ResourceState state = new ResourceState();
            state.values.putAll(map);
            return state;
        }));

        public int get(String key, int defaultValue) { return values.getOrDefault(resolveLegacySyntheticId(key), defaultValue); }
        public boolean has(String key)               { return values.containsKey(resolveLegacySyntheticId(key)); }
        public void set(String key, int value)       { values.put(resolveLegacySyntheticId(key), value); dirty = true; }
        public void remove(String key)               { values.remove(resolveLegacySyntheticId(key)); dirty = true; }

        public void clampedAdd(String key, int delta, int min, int max) {
            String k = resolveLegacySyntheticId(key);
            int cur = values.getOrDefault(k, 0);
            values.put(k, Math.max(min, Math.min(max, cur + delta)));
            dirty = true;
        }

        /** Dirty flag — set when any value changes. Cleared by sync logic. */
        private boolean dirty;
        public boolean isDirty() { return dirty; }
        public void clearDirty() { dirty = false; }

        public Map<String, Integer> getAll() { return Map.copyOf(values); }

        // ---- Wildcard support ----
        // Apoli-derivative packs (Origins++, Medieval Origins Revival, etc.)
        // commonly write resource selectors with `*` segments — for example
        // `*:*_flight_resource` to mean "any flight-resource bar in any
        // namespace". Without wildcard support these never match anything,
        // which silently breaks gameplay (Pixie's flight resource bar,
        // Soul Seer's antivore, the Origins++ teleport ray).

        /** True if the selector contains a `*` glob segment. */
        public static boolean isWildcard(String selector) {
            return selector != null && selector.indexOf('*') >= 0;
        }

        /**
         * Returns all stored keys that glob-match the selector. `*` matches
         * any run of characters (including the empty run). Used for reads
         * and bulk writes against wildcard selectors.
         *
         * <p>Two-pass match: first the strict glob, then a second pass that
         * normalises path separators (`/` → `_`) on each key before testing
         * the same pattern. The second pass exists because Apoli-derivative
         * packs commonly author selectors like `*:*_flight_resource` that
         * expect to match a power's flat name (e.g. `flight_resource`),
         * but our synthetic-ID format from {@code origins:multiple}
         * expansion uses `/` as the segment separator
         * ({@code pixie/flight/flight_resource}). Without the normalisation
         * the pattern silently never matches and wildcard writes are no-ops.
         */
        public java.util.List<String> matchingKeys(String selector) {
            if (!isWildcard(selector)) {
                String k = resolveLegacySyntheticId(selector);
                return values.containsKey(k) ? java.util.List.of(k) : java.util.List.of();
            }
            java.util.regex.Pattern p = globToPattern(selector);
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String k : values.keySet()) {
                if (p.matcher(k).matches() || p.matcher(k.replace('/', '_')).matches()) {
                    out.add(k);
                }
            }
            return out;
        }

        /** First matching value, or the default if no key matches. */
        public int getAny(String selector, int defaultValue) {
            if (!isWildcard(selector)) return get(selector, defaultValue);
            java.util.regex.Pattern p = globToPattern(selector);
            for (var entry : values.entrySet()) {
                String k = entry.getKey();
                if (p.matcher(k).matches() || p.matcher(k.replace('/', '_')).matches()) {
                    return entry.getValue();
                }
            }
            return defaultValue;
        }

        /** Apply a delta to every key matching the selector. No-op if none match. */
        public void clampedAddAll(String selector, int delta, int min, int max) {
            for (String k : matchingKeys(selector)) {
                clampedAdd(k, delta, min, max);
            }
        }

        /** Set every matching key to the same value. */
        public void setAll(String selector, int value) {
            for (String k : matchingKeys(selector)) set(k, value);
        }

        // Compiled patterns are cached to avoid recompiling the regex on
        // every condition test / action dispatch. Selectors are pack-defined
        // and low-cardinality (typically <50 unique strings), so the cache
        // stays small. Concurrent because resource-state reads can fire from
        // multiple ticks scheduled in parallel; ConcurrentHashMap is fine
        // for this read-mostly workload.
        private static final java.util.concurrent.ConcurrentHashMap<String, java.util.regex.Pattern> GLOB_PATTERN_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

        private static java.util.regex.Pattern globToPattern(String glob) {
            return GLOB_PATTERN_CACHE.computeIfAbsent(glob, g -> {
                StringBuilder sb = new StringBuilder("^");
                for (int i = 0; i < g.length(); i++) {
                    char c = g.charAt(i);
                    if (c == '*')      sb.append(".*");
                    else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) sb.append('\\').append(c);
                    else               sb.append(c);
                }
                sb.append('$');
                return java.util.regex.Pattern.compile(sb.toString());
            });
        }
    }

    /**
     * Per-resource display metadata registered at parse time.
     *
     * <p>{@code barIndex}/{@code iconIndex} mirror Apoli's {@code hud_render}
     * sprite indices into {@code neoorigins:textures/gui/resource_bar.png}; a
     * value of {@code -1} means "unset" (the HUD then draws a {@code color}-tinted
     * fill inside the bar frame instead of an Apoli sprite row).
     */
    public record ResourceMeta(int min, int max, String label, int color, boolean hidden,
                               int barIndex, int iconIndex, String spriteLocation,
                               String animated, int tint, boolean alwaysShow) {
        /** Convenience constructor — visible, no Apoli sprite indices. */
        public ResourceMeta(int min, int max, String label, int color) {
            this(min, max, label, color, false, -1, -1, null, "", 0, false);
        }
        /** Convenience constructor — explicit visibility, no Apoli sprite indices. */
        public ResourceMeta(int min, int max, String label, int color, boolean hidden) {
            this(min, max, label, color, hidden, -1, -1, null, "", 0, false);
        }
        /** Convenience constructor — explicit visibility + animated FX preset (native bar). */
        public ResourceMeta(int min, int max, String label, int color, boolean hidden,
                            String animated, int tint) {
            this(min, max, label, color, hidden, -1, -1, null, animated, tint, false);
        }
        /** Convenience constructor — native bar with explicit always-render opt-in. */
        public ResourceMeta(int min, int max, String label, int color, boolean hidden,
                            String animated, int tint, boolean alwaysShow) {
            this(min, max, label, color, hidden, -1, -1, null, animated, tint, alwaysShow);
        }
        /** Convenience constructor — Apoli sprite indices against the default sheet. */
        public ResourceMeta(int min, int max, String label, int color, boolean hidden,
                            int barIndex, int iconIndex) {
            this(min, max, label, color, hidden, barIndex, iconIndex, null, "", 0, false);
        }
        /** Convenience constructor — Apoli sprite indices against a pack-declared sheet. */
        public ResourceMeta(int min, int max, String label, int color, boolean hidden,
                            int barIndex, int iconIndex, String spriteLocation) {
            this(min, max, label, color, hidden, barIndex, iconIndex, spriteLocation, "", 0, false);
        }
    }

    private static final Map<String, ResourceMeta> RESOURCE_META = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerResourceMeta(String key, ResourceMeta meta) { RESOURCE_META.put(key, meta); }
    public static void unregisterResourceMeta(String key) { RESOURCE_META.remove(key); }
    // Reads resolve legacy slash-form synthetic ids (see LEGACY_SYNTHETIC_IDS below):
    // meta is registered under the power's canonical underscore id, but datapack
    // references (change_resource bounds lookup, resource-command clamps) may still
    // carry the pre-2.2.8 "parent/subkey" form. Without resolution the lookup
    // returns null and change_resource writes unbounded — a [0,1] counter climbs
    // past max and any `resource ==` gate goes permanently false.
    public static ResourceMeta getResourceMeta(String key) { return RESOURCE_META.get(resolveLegacySyntheticId(key)); }
    public static Map<String, ResourceMeta> allResourceMeta() { return Map.copyOf(RESOURCE_META); }
    public static void clearResourceMeta() { RESOURCE_META.clear(); RESOURCE_RENDER_CONDITIONS.clear(); }

    // ---- Cooldown power durations (origins:cooldown Route B) ----
    // An Apoli cooldown power is a countdown resource: 0 == ready, >0 == ticks
    // remaining. trigger_cooldown arms it by setting the registered duration;
    // the power's onTick decrements it. Registered at power-load time (keyed by
    // the power's canonical id) so trigger_cooldown — whose `power` field may be
    // a wildcard glob or a legacy slash id — can resolve "is this a cooldown
    // power, and how long does it run" without re-parsing JSON.
    private static final Map<String, Integer> COOLDOWN_DURATIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerCooldownDuration(String key, int ticks) { COOLDOWN_DURATIONS.put(key, ticks); }
    public static void unregisterCooldownDuration(String key) { COOLDOWN_DURATIONS.remove(key); }
    public static void clearCooldownDurations() { COOLDOWN_DURATIONS.clear(); }
    /** Registered duration in ticks, or null when the key is not a cooldown power.
     *  Reads resolve legacy slash-form synthetic ids, same rationale as getResourceMeta. */
    public static Integer cooldownDuration(String key) { return COOLDOWN_DURATIONS.get(resolveLegacySyntheticId(key)); }

    // ---- Legacy synthetic sub-power id aliases (backward compat) ----
    // Pre-2.2.8, origins:multiple expansion joined a sub-power's synthetic id
    // with a slash ("parent/subkey"). 2.2.8 (commit 4e8f4ea4) switched the join
    // to an underscore ("parent_subkey") to match the Apoli convention. Datapacks
    // authored against the old scheme reference their bars / toggles by the slash
    // id, which no longer resolves to the registered underscore id — so resource
    // bars and active_ability toggles inside an origins:multiple silently stopped
    // working after the update. This map records legacy -> canonical for every
    // synthetic sub-power at expansion time (the one place the slash form is
    // unambiguously reconstructable) so those references keep resolving. Populated
    // by OriginsCompatPowerLoader.expandMultiple, cleared at the start of each reload.
    private static final Map<String, String> LEGACY_SYNTHETIC_IDS = new java.util.concurrent.ConcurrentHashMap<>();
    public static void registerLegacySyntheticId(String legacy, String canonical) {
        if (legacy != null && canonical != null && !legacy.equals(canonical)) {
            LEGACY_SYNTHETIC_IDS.put(legacy, canonical);
        }
    }
    public static void clearLegacySyntheticIds() { LEGACY_SYNTHETIC_IDS.clear(); }
    /**
     * Map a legacy slash-form synthetic sub-power id to its canonical underscore id.
     * Returns the input unchanged when it is not a known legacy id, so genuine
     * resource keys that happen to contain a '/' (e.g. "ns:folder/power") are never
     * rewritten — only ids actually emitted by pre-2.2.8 multiple-expansion are.
     */
    public static String resolveLegacySyntheticId(String key) {
        if (key == null) return null;
        String canonical = LEGACY_SYNTHETIC_IDS.get(key);
        return canonical != null ? canonical : key;
    }

    // ---- Variable declarations (neoorigins:variable) ----
    // A variable is an always-hidden counter that shares the ResourceState
    // keyspace (keyed by its own power id). Declarations are registered at
    // power-load time so a read resolves the declared start/bounds regardless
    // of where the variable sits in an origin's power list. start doubles as
    // the read fallback for a declared-but-not-yet-seeded key.
    public record VariableDecl(int start, int min, int max) {}

    private static final Map<String, VariableDecl> VARIABLE_DECLS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerVariable(String key, VariableDecl decl) { VARIABLE_DECLS.put(key, decl); }
    public static void unregisterVariable(String key) { VARIABLE_DECLS.remove(key); }
    // Reads resolve legacy slash-form synthetic ids, same rationale as getResourceMeta.
    public static VariableDecl getVariable(String key) { return VARIABLE_DECLS.get(resolveLegacySyntheticId(key)); }
    public static boolean isDeclaredVariable(String key) { return VARIABLE_DECLS.containsKey(resolveLegacySyntheticId(key)); }
    public static Map<String, VariableDecl> allVariables() { return Map.copyOf(VARIABLE_DECLS); }
    public static void clearVariables() { VARIABLE_DECLS.clear(); }

    /** Declared start value for a variable key, or 0 when the key isn't a declared variable. */
    public static int variableStart(String key) {
        VariableDecl d = getVariable(key);
        return d != null ? d.start() : 0;
    }

    // ---- Apoli hud_render.condition: a bar renders only while its condition holds ----
    // Evaluated server-side; a bar whose condition currently fails is excluded from
    // both syncs (so it never reaches the client). The defining power's onTick drives
    // a full re-sync on condition edges so the bar appears/disappears live.
    private static final Map<String, com.cyberday1.neoorigins.compat.condition.EntityCondition> RESOURCE_RENDER_CONDITIONS =
        new java.util.concurrent.ConcurrentHashMap<>();
    public static void registerResourceRenderCondition(String key, com.cyberday1.neoorigins.compat.condition.EntityCondition cond) { RESOURCE_RENDER_CONDITIONS.put(key, cond); }
    public static void unregisterResourceRenderCondition(String key) { RESOURCE_RENDER_CONDITIONS.remove(key); }
    public static com.cyberday1.neoorigins.compat.condition.EntityCondition getResourceRenderCondition(String key) { return RESOURCE_RENDER_CONDITIONS.get(key); }

    // ---- ToggleState ----

    public static class ToggleState {
        private final Map<String, Boolean> states = new HashMap<>();

        public static final Codec<ToggleState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                .optionalFieldOf("states", Map.of())
                .forGetter(s -> Map.copyOf(s.states))
        ).apply(inst, map -> {
            ToggleState state = new ToggleState();
            state.states.putAll(map);
            return state;
        }));

        public boolean isActive(String key, boolean defaultValue) {
            return states.getOrDefault(key, defaultValue);
        }

        public boolean toggle(String key, boolean defaultValue) {
            boolean next = !states.getOrDefault(key, defaultValue);
            states.put(key, next);
            return next;
        }

        public void set(String key, boolean value) { states.put(key, value); }

        /** Returns an unmodifiable view of the toggle states map for iteration (e.g. wildcard matching). */
        public java.util.Map<String, Boolean> getStates() { return java.util.Collections.unmodifiableMap(states); }
    }

    /**
     * Build and send a {@link com.cyberday1.neoorigins.network.payload.SyncResourcePayload}
     * containing all active resources for this player. Called every 10 ticks from
     * compat resource onTick, or on grant/revoke.
     */
    public static void syncResourcesToClient(net.minecraft.server.level.ServerPlayer player) {
        var state = player.getData(resourceState());
        var entries = new HashMap<String, com.cyberday1.neoorigins.network.payload.SyncResourcePayload.Entry>();
        for (var e : state.getAll().entrySet()) {
            ResourceMeta meta = getResourceMeta(e.getKey());
            if (meta == null || meta.hidden()) continue;
            var rcond = getResourceRenderCondition(e.getKey());
            if (rcond != null && !rcond.test(player)) continue;
            entries.put(e.getKey(), new com.cyberday1.neoorigins.network.payload.SyncResourcePayload.Entry(
                e.getValue(), meta.min(), meta.max(), meta.label(), meta.color(),
                meta.barIndex(), meta.iconIndex(),
                meta.spriteLocation() == null ? "" : meta.spriteLocation(),
                meta.animated() == null ? "" : meta.animated(), meta.tint(), meta.alwaysShow()));
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.cyberday1.neoorigins.network.payload.SyncResourcePayload(entries));
    }

    /**
     * Build and send a value-only {@link com.cyberday1.neoorigins.network.payload.SyncResourceValuesPayload}
     * for this player. Used on the high-frequency paths (10-tick dirty sync,
     * change/set actions and commands) where only values change — the client
     * already holds the display metadata from a prior full
     * {@link #syncResourcesToClient(net.minecraft.server.level.ServerPlayer)}.
     * Entry creation/removal must still go through the full sync.
     */
    public static void syncResourceValuesToClient(net.minecraft.server.level.ServerPlayer player) {
        var state = player.getData(resourceState());
        var values = new HashMap<String, Integer>();
        for (var e : state.getAll().entrySet()) {
            ResourceMeta meta = getResourceMeta(e.getKey());
            if (meta == null || meta.hidden()) continue;
            var rcond = getResourceRenderCondition(e.getKey());
            if (rcond != null && !rcond.test(player)) continue;
            values.put(e.getKey(), e.getValue());
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.cyberday1.neoorigins.network.payload.SyncResourceValuesPayload(values));
    }
}
