package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.config.PowerOverridesConfig;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import net.minecraft.network.chat.Component;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatTranslationLog;
import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

public class PowerDataManager extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    public static final PowerDataManager INSTANCE = new PowerDataManager();
    // NeoOrigins format: data/<ns>/origins/powers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/powers");
    // Origins mod format: data/<ns>/powers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    private Map<ResourceLocation, PowerHolder<?>> powers = new HashMap<>();
    /** Post-translation raw JSON kept for the creator's template loader so a
     *  cloned vanilla power lands in the draft as the same body the loader
     *  saw, not a codec round-trip (which loses fields stripped before parse).
     *  Only powers that successfully parse are recorded. */
    private Map<ResourceLocation, JsonObject> rawPowerJson = new HashMap<>();
    /** Route B powers injected by OriginsCompatPowerLoader after native loading. */
    private Map<ResourceLocation, PowerHolder<?>> injectedPowers = new HashMap<>();
    /** Bumped on every datapack reload and Route-B injection so per-player power caches can invalidate. */
    private int version = 0;
    public int version() { return version; }

    /** Cached: does any loaded power (native or Route-B injected) have type
     *  {@code neoorigins:ultimine}? Recomputed whenever the power set changes
     *  (datapack reload in {@link #apply} or Route-B injection in
     *  {@link #injectExternalPowers}). Lets the FTB Ultimine bridge stay
     *  completely dormant unless a loaded pack actually defines an ultimine
     *  power — without this flag the deny-only restriction API would disable
     *  vein-mining for non-holders even when no pack uses the power. */
    private boolean ultiminePowerInUse = false;

    /** True if at least one loaded power has type {@code neoorigins:ultimine}. */
    public boolean isUltiminePowerInUse() { return ultiminePowerInUse; }

    /** Rescan the loaded power set for any {@code neoorigins:ultimine} power. */
    private void recomputeUltiminePowerInUse() {
        boolean found = false;
        for (PowerHolder<?> holder : powers.values()) {
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.UltiminePower) {
                found = true;
                break;
            }
        }
        if (!found) {
            for (PowerHolder<?> holder : injectedPowers.values()) {
                if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.UltiminePower) {
                    found = true;
                    break;
                }
            }
        }
        this.ultiminePowerInUse = found;
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER, resourceManager, map);
        scanConverter(COMPAT_CONVERTER, resourceManager, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager resourceManager, Map<ResourceLocation, JsonElement> map) {
        for (var entry : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id = converter.fileToId(fileId);
            if (map.containsKey(id)) continue; // native format wins
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading power file {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        CompatTranslationLog.open();
        OriginsMultipleExpander.reset();

        // Build a working set, expanding any origins:multiple entries into synthetic sub-power entries
        Map<ResourceLocation, JsonElement> working = new HashMap<>(pObject);
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            // Canonicalize apoli:/apugli: -> origins: so apoli:multiple is expanded.
            String typeStr = OriginsFormatDetector.canonicalizePowerType(json);
            if (OriginsMultipleExpander.isMultipleType(typeStr)) {
                working.remove(id);
                try {
                    Map<ResourceLocation, JsonObject> synthetics = OriginsMultipleExpander.expand(id, json);
                    working.putAll(synthetics);
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand origins:multiple {}: {}", id, reason);
                    CompatTranslationLog.fail(id, "origins:multiple expansion error: " + reason);
                }
            } else if (("origins:attribute".equals(typeStr) || "apace:attribute".equals(typeStr))
                && json.has("modifiers")
                && json.get("modifiers").isJsonArray()
                && json.getAsJsonArray("modifiers").size() > 1) {
                // Multi-modifier origins:attribute — Apoli-style packs commonly
                // ship 5–7 modifiers in one power (MoR Pixie pixie_properties).
                // Pre-expand into one single-modifier synthetic per entry so
                // the per-modifier translator path produces correct
                // neoorigins:attribute_modifier instances. Without this the
                // translator silently kept only modifiers[0].
                working.remove(id);
                try {
                    Map<ResourceLocation, JsonObject> synthetics = OriginsMultipleExpander.expandAttributeMulti(id, json);
                    working.putAll(synthetics);
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand multi-modifier origins:attribute {}: {}", id, reason);
                    CompatTranslationLog.fail(id, "multi-modifier origins:attribute expansion error: " + reason);
                }
            } else if (com.cyberday1.neoorigins.compat.OriginsStartingEquipmentExpander.isStartingEquipment(typeStr)) {
                // Apoli's starting_equipment is a single power with N stacks;
                // NeoOrigins' is one stack per power. Expand here so the
                // downstream loop sees N synthetic neoorigins-shape entries.
                working.remove(id);
                try {
                    Map<ResourceLocation, JsonObject> synthetics =
                        com.cyberday1.neoorigins.compat.OriginsStartingEquipmentExpander.expand(id, json);
                    working.putAll(synthetics);
                    CompatTranslationLog.pass(id, "starting_equipment expanded into " + synthetics.size() + " stacks");
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand starting_equipment {}: {}", id, reason);
                    CompatTranslationLog.fail(id, "starting_equipment expansion error: " + reason);
                }
            }
        }

        Map<ResourceLocation, PowerHolder<?>> loaded = new HashMap<>();
        Map<ResourceLocation, JsonObject> rawSnapshot = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : working.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                if (!json.has("type")) {
                    NeoOrigins.LOGGER.warn("Power {} missing 'type' field", id);
                    continue;
                }

                // Top-level required_mods gate — skip powers whose target mod(s)
                // are absent so they never load, sync, or appear (see ModGate).
                if (!ModGate.satisfied(json.get("required_mods"))) continue;

                Resolved resolved = resolvePowerType(id, json);
                if (resolved == null) continue; // dropped; every drop path logs
                ResourceLocation typeId = resolved.typeId();
                json = resolved.json();
                PowerType<?> type = PowerTypes.get(typeId);
                if (type == null) {
                    // Don't warn for types handled by Route B compat — they'll
                    // be picked up by OriginsCompatPowerLoader after us.
                    String rawType = json.get("type").getAsString();
                    if (!com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader.isRouteBType(rawType)) {
                        NeoOrigins.LOGGER.warn("Unknown power type '{}' for power {}{}",
                            typeId, id, unknownTypeHint(typeId));
                    }
                    continue;
                }
                int beforeSize = loaded.size();
                parsePower(id, typeId, type, json, loaded);
                if (loaded.size() > beforeSize) {
                    // Power parsed cleanly — keep the post-translation body for
                    // the creator's template loader. Stored by REFERENCE: every
                    // pipeline mutation (canonicalize, translate, config
                    // overrides, alias remap) happens before this point, and
                    // parsePower only mutates its own deepCopy (configJson), so
                    // the object is final here. getRawPowerJson() deep-copies on
                    // read instead, shifting the cost from every datapack reload
                    // (~840 copies) to the rare creator-open path.
                    rawSnapshot.put(id, json);
                }
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading power {}", id, e);
            }
        }
        this.powers = Collections.unmodifiableMap(loaded);
        this.rawPowerJson = Collections.unmodifiableMap(rawSnapshot);
        this.injectedPowers = new HashMap<>(); // cleared; Route B will re-inject after us
        this.version++;
        registerVariableDeclarations(loaded);
        recomputeUltiminePowerInUse();
        NeoOrigins.LOGGER.info("Loaded {} powers", loaded.size());

        // Per-namespace breakdown — toggled via config/neoorigins/admin.toml
        if (AdminConfig.DEBUG_POWER_LOADING.get()) {
            Map<String, Long> byNamespace = loaded.keySet().stream()
                .collect(Collectors.groupingBy(ResourceLocation::getNamespace, Collectors.counting()));
            byNamespace.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> NeoOrigins.LOGGER.info("  [DEBUG] powers: {}  x{}", e.getKey(), e.getValue()));
        }
    }

    /**
     * Outcome of {@link #resolvePowerType}: the type id to look up in
     * {@code PowerTypes} and the JSON body to parse, both post-translation and
     * post-remap. {@code json} may be a different object than the one passed in
     * (Route A returns a rewritten body), so callers must use this one.
     */
    public record Resolved(ResourceLocation typeId, JsonObject json) {}

    /**
     * The pre-parse compat pipeline for a single power entry, in the one order
     * that works: canonicalize Apoli-family &rarr; Route A translate &rarr;
     * config overrides &rarr; legacy alias remap. Returns {@code null} when the
     * entry is dropped before parsing (every drop path logs its own reason).
     *
     * <p>Two orderings here are load-bearing and must not be shuffled:
     *
     * <ul>
     *   <li>{@link #applyConfigOverrides} runs BEFORE
     *       {@link LegacyPowerTypeAliases#apply}, so remap lambdas with
     *       value-dependent gates (damage_in_water's {@code dps > 0} check, which
     *       decides between a damage action and a {@code neoorigins:nothing}
     *       no-op) and the field-strip step see the server owner's final values
     *       rather than the pack defaults. Reversing it is how "water damage
     *       still fires at config 0" happened.</li>
     *   <li>Canonicalization runs BEFORE Route A, because the dispatch switch is
     *       keyed on {@code origins:}/{@code apace:} labels only.</li>
     * </ul>
     *
     * <p>Extracted from {@link #apply} so both the ordering and the alias
     * fallback below are directly testable without a ResourceManager.
     */
    public static Resolved resolvePowerType(ResourceLocation id, JsonObject json) {
        // The type id EXACTLY as authored, captured before canonicalization
        // overwrites it. LegacyPowerTypeAliases is keyed on the authored id, and
        // for the Apoli family that key does not survive the next line — see the
        // fallback below.
        ResourceLocation authoredTypeId =
            ResourceLocation.tryParse(json.get("type").getAsString());

        // Canonicalize apoli:/apugli: -> origins: here too, to cover the
        // synthetic sub-powers emitted by multiple-expansion (which never
        // pass through the first loop).
        String canonicalType = OriginsFormatDetector.canonicalizePowerType(json);
        // ...and the reverse mistake: a legacy type spelled with OUR namespace,
        // which resolves to nothing and would drop the power outright.
        canonicalType = OriginsFormatDetector.salvageLegacyPowerSpelling(json);

        // Translate Origins-format power to NeoOrigins format before parsing
        if (OriginsFormatDetector.isOriginsFormat(json)) {
            Optional<JsonObject> translated = OriginsPowerTranslator.translate(id, json);
            if (translated.isPresent()) {
                json = translated.get();
            } else if (aliasCanStillClaim(authoredTypeId, canonicalType)) {
                // Route A has no case for the canonical id and would drop the
                // power right here — but the alias table knows this type under
                // the id the pack actually authored (apugli:action_on_jump,
                // apugli:action_on_target_death). Canonicalization had already
                // erased that key, so the alias pass below could never fire and
                // the power was lost outright. Put the authored id back and let
                // the alias pass have it.
                json.addProperty("type", authoredTypeId.toString());
            } else {
                return null; // dropped; logged by the translator
            }
        }

        ResourceLocation typeId = ResourceLocation.parse(json.get("type").getAsString());
        // Apply config overrides BEFORE alias remap — see the ordering note above.
        applyConfigOverrides(id, json);
        // 2.0 legacy alias remap — transparently rewrites old type IDs.
        typeId = LegacyPowerTypeAliases.apply(typeId, json, id);
        return new Resolved(typeId, json);
    }

    /**
     * True when a power Route A just refused is one the legacy alias table can
     * still handle under its authored id.
     *
     * <p>Only ever consulted on the drop path, so it cannot re-route anything
     * that already loads: {@code apoli:}/{@code apugli:edible_item} keep going
     * through Route A, because {@code origins:edible_item} has a translation
     * case and so never reaches here.
     *
     * <p>Route B is checked because it runs after us and legitimately claims the
     * types Route A skips; an alias must not steal one out from under it.
     */
    private static boolean aliasCanStillClaim(ResourceLocation authoredTypeId, String canonicalType) {
        if (authoredTypeId == null) return false;
        if (!LegacyPowerTypeAliases.hasAlias(authoredTypeId)) return false;
        if (com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader.isRouteBType(canonicalType)) return false;
        return !com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader
            .CONDITIONED_ROUTE_B_TYPES.contains(canonicalType);
    }

    /**
     * A trailing clause naming the likely fix for an unresolved power {@code type},
     * or {@code ""} when we have nothing useful to say.
     *
     * <p>"Unknown power type" on its own tells an author that something is wrong but
     * not what. The common cause by far is writing an ACTION or CONDITION id in the
     * {@code type} field: the three vocabularies share a namespace and a naming
     * style, so {@code cast_iron_spell} reads like a power type until you find it in
     * {@link ActionParser}.
     *
     * <p>The other diagnosable case — a legacy type spelled {@code neoorigins:} — is
     * not handled here because it never reaches this point: {@code
     * OriginsFormatDetector.salvageLegacyPowerSpelling} rewrites it and logs its own
     * advice.
     *
     * <p>Matched on the PATH only, so a mis-namespaced id still gets the hint.
     */
    static String unknownTypeHint(ResourceLocation typeId) {
        String path = typeId.getPath();
        String nativeId = "neoorigins:" + path;
        if (ActionParser.KNOWN_TYPES.contains(nativeId)) {
            return " — '" + path + "' is an action, not a power type; put it in the"
                + " entity_action field of a power that takes one (neoorigins:active_ability,"
                + " neoorigins:action_on_event)";
        }
        if (ConditionParser.KNOWN_TYPES.contains(nativeId)) {
            return " — '" + path + "' is a condition, not a power type; put it in a power's"
                + " condition field";
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private <C extends PowerConfiguration> void parsePower(
            ResourceLocation id, ResourceLocation typeId, PowerType<C> type, JsonObject json,
            Map<ResourceLocation, PowerHolder<?>> target) {
        // Config overrides are applied upstream in loadPowers/apply() before the
        // legacy alias remap so value-dependent gates see the user's values.

        Component name = extractComponentField(json, "name");
        Component desc = extractComponentField(json, "description");
        boolean hidden = json.has("hidden") && json.get("hidden").isJsonPrimitive()
            && json.get("hidden").getAsJsonPrimitive().isBoolean()
            && json.get("hidden").getAsBoolean();

        // Parse top-level condition gate (optional, works for all power types).
        // Field is canonically named "power_condition" (not "condition") to avoid
        // colliding with power types that claim "condition" in their own config
        // codecs. The excluded set is derived below from the BuiltinPowers
        // FieldSpecs, never listed here; docs/POWER_TYPES.md carries the
        // author-facing list and docFieldTableCheck keeps it honest.
        EntityCondition condition = null;
        PowerHolder.ConditionMode conditionMode = PowerHolder.ConditionMode.DENY;
        if (json.has("power_condition") && json.get("power_condition").isJsonObject()) {
            condition = ConditionParser.parse(json.getAsJsonObject("power_condition"), id.toString());
        }
        if (json.has("power_condition_mode") && json.get("power_condition_mode").isJsonPrimitive()) {
            String modeStr = json.get("power_condition_mode").getAsString().toUpperCase();
            if ("ALLOW".equals(modeStr)) {
                conditionMode = PowerHolder.ConditionMode.ALLOW;
            }
        }

        // Alias: top-level "condition" acts as power_condition (default mode ALLOW)
        // on types that do NOT declare "condition" as one of their own config fields.
        // Type-field lookup goes through the BuiltinPowers FieldSpec table; if the
        // type has no FieldSpec coverage we cannot prove the codec doesn't consume
        // the key, so we fail safe: leave it alone and tell the author.
        boolean aliasedCondition = false;
        if (json.has("condition") && json.get("condition").isJsonObject()) {
            BuiltinPowers.PowerSpec spec = BuiltinPowers.get(typeId);
            if (spec == null) {
                NeoOrigins.LOGGER.warn(
                    "Power {}: top-level 'condition' is not a recognized gate here; use 'power_condition'",
                    id);
            } else if (spec.fields().stream().noneMatch(f -> "condition".equals(f.name()))) {
                if (condition != null) {
                    NeoOrigins.LOGGER.warn(
                        "Power {}: both 'power_condition' and top-level 'condition' present; "
                            + "'power_condition' wins, the redundant 'condition' key is ignored",
                        id);
                } else {
                    condition = ConditionParser.parse(json.getAsJsonObject("condition"), id.toString());
                    if (!json.has("power_condition_mode")) {
                        conditionMode = PowerHolder.ConditionMode.ALLOW;
                    }
                    aliasedCondition = true;
                }
            }
            // else: the type claims "condition" natively — its codec consumes it.
        }
        final EntityCondition finalCondition = condition;
        final PowerHolder.ConditionMode finalConditionMode = conditionMode;

        // Strip display fields so they don't confuse the typed codec.
        JsonObject configJson = json.deepCopy();
        configJson.remove("name");
        configJson.remove("description");
        // NOTE: do NOT strip "hidden" — it is read above for the PowerHolder
        // (origin info-panel visibility) from the original `json`, but
        // neoorigins:resource ALSO reads top-level `hidden` from its config
        // codec to hide the HUD bar. Stripping it here meant authors who wrote
        // `"hidden": true` on a resource never hid the bar (only
        // hud_render.should_render worked). No other power codec reads `hidden`,
        // and the custom/record codecs ignore unknown fields, so leaving it in
        // is safe for every other type.
        configJson.remove("power_condition");
        configJson.remove("power_condition_mode");
        if (aliasedCondition) {
            configJson.remove("condition");
        }
        configJson.remove("required_mods");
        // Inject power ID for types that need it at codec-decode time (e.g. ResourcePower).
        configJson.addProperty("_power_id", id.toString());

        type.codec().parse(JsonOps.INSTANCE, configJson)
            .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse power config {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(id, type, config, name, desc, hidden, finalCondition, finalConditionMode)));
    }

    /**
     * Rebuilds the global {@code neoorigins:variable} declaration registry from
     * the freshly loaded power set. Registering at load time (rather than only
     * per-player on grant) means a {@code resource} condition / {@code change_resource}
     * read resolves the declared start/bounds regardless of where the variable
     * sits in an origin's power list — "declared at the start of the power stack"
     * is true by construction, not by authoring order. A variable's storage key
     * is its own power id, so it can never collide with another power's id; the
     * only same-key case is a variable and a resource declared under the same id,
     * which we warn about (the map already deduped to one survivor by then).
     */
    private void registerVariableDeclarations(Map<ResourceLocation, PowerHolder<?>> loaded) {
        CompatAttachments.clearVariables();
        java.util.Set<String> resourceKeys = new java.util.HashSet<>();
        for (var entry : loaded.entrySet()) {
            if (entry.getValue().type() instanceof com.cyberday1.neoorigins.power.builtin.ResourcePower) {
                resourceKeys.add(entry.getKey().toString());
            }
        }
        int count = 0;
        for (var entry : loaded.entrySet()) {
            if (!(entry.getValue().type() instanceof com.cyberday1.neoorigins.power.builtin.VariablePower)) continue;
            String key = entry.getKey().toString();
            if (resourceKeys.contains(key)) {
                NeoOrigins.LOGGER.warn("neoorigins:variable {} shares an id with a resource of the same name; "
                    + "they would share one stored value. Rename one.", key);
            }
            var cfg = (com.cyberday1.neoorigins.power.builtin.VariablePower.Config) entry.getValue().config();
            CompatAttachments.registerVariable(key,
                new CompatAttachments.VariableDecl(cfg.start(), cfg.min(), cfg.max()));
            count++;
        }
        if (count > 0) NeoOrigins.LOGGER.info("Registered {} neoorigins:variable declaration(s)", count);
    }

    /** Merges config-file overrides into the power JSON before CODEC parsing. */
    private static void applyConfigOverrides(ResourceLocation id, JsonObject json) {
        Map<String, Object> overrides = PowerOverridesConfig.getPowerOverrides(id.toString());
        if (overrides == null) return;

        for (var entry : overrides.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Number n) {
                json.addProperty(field, n);
            } else if (value instanceof Boolean b) {
                json.addProperty(field, b);
            } else {
                json.addProperty(field, value.toString());
            }
        }
        NeoOrigins.LOGGER.info("Applied {} config override(s) to power {}: {}",
            overrides.size(), id, overrides);
    }

    private static Component extractComponentField(JsonObject json, String field) {
        if (!json.has(field)) return Component.empty();
        JsonElement el = json.get(field);
        if (el.isJsonPrimitive()) return Component.translatable(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text"))      return Component.literal(obj.get("text").getAsString());
            if (obj.has("translate")) return Component.translatable(obj.get("translate").getAsString());
        }
        return Component.empty();
    }

    /** Called by OriginsCompatPowerLoader after its apply() to inject Route B powers. */
    public void injectExternalPowers(Map<ResourceLocation, PowerHolder<?>> external) {
        this.injectedPowers = Collections.unmodifiableMap(new HashMap<>(external));
        this.version++;
        recomputeUltiminePowerInUse();
    }

    /** Returns all powers including Route B injected ones (used for registry sync). */
    public Map<ResourceLocation, PowerHolder<?>> getAllPowers() {
        if (injectedPowers.isEmpty()) return powers;
        Map<ResourceLocation, PowerHolder<?>> all = new HashMap<>(powers);
        all.putAll(injectedPowers);
        return Collections.unmodifiableMap(all);
    }

    public Map<ResourceLocation, PowerHolder<?>> getPowers() { return powers; }

    public PowerHolder<?> getPower(ResourceLocation id) {
        PowerHolder<?> holder = powers.get(id);
        return holder != null ? holder : injectedPowers.get(id);
    }

    public boolean hasPower(ResourceLocation id) {
        return powers.containsKey(id) || injectedPowers.containsKey(id);
    }

    /** Post-translation raw power JSON for the creator's template loader.
     *  Returns null when this power was only loaded on the client (powers
     *  aren't synced with their bodies) or wasn't loaded at all.
     *
     *  <p>Returns a deep copy: the snapshot map holds references to the
     *  loader's working objects (see the reload loop), so callers get an
     *  isolated object they may freely mutate. */
    public JsonObject getRawPowerJson(ResourceLocation id) {
        JsonObject json = rawPowerJson.get(id);
        return json != null ? json.deepCopy() : null;
    }
}
