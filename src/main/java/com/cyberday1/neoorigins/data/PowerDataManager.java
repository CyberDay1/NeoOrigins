package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.config.PowerOverridesConfig;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import net.minecraft.network.chat.Component;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

public class PowerDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final PowerDataManager INSTANCE = new PowerDataManager();
    // NeoOrigins format: data/<ns>/origins/powers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/powers");
    // Origins mod format: data/<ns>/powers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    private Map<Identifier, PowerHolder<?>> powers = new HashMap<>();
    /** Post-translation raw JSON kept for the creator's template loader so a
     *  cloned vanilla power lands in the draft as the same body the loader
     *  saw, not a codec round-trip (which loses fields stripped before parse).
     *  Only powers that successfully parse are recorded. */
    private Map<Identifier, JsonObject> rawPowerJson = new HashMap<>();
    /** Route B powers injected by OriginsCompatPowerLoader after native loading. */
    private Map<Identifier, PowerHolder<?>> injectedPowers = new HashMap<>();
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
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER, resourceManager, map);
        scanConverter(COMPAT_CONVERTER, resourceManager, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager resourceManager, Map<Identifier, JsonElement> map) {
        for (var entry : converter.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = converter.fileToId(fileId);
            if (map.containsKey(id)) continue; // native format wins
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading power file {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        CompatTranslationLog.open();
        OriginsMultipleExpander.reset();

        // Build a working set, expanding any origins:multiple entries into synthetic sub-power entries
        Map<Identifier, JsonElement> working = new HashMap<>(pObject);
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            // Rewrite apoli:/apugli: power types to the canonical origins: namespace
            // in place, so the dispatch below (and the main loop's format check +
            // translator) recognize packs that use the Apoli namespace.
            String typeStr = OriginsFormatDetector.canonicalizePowerType(json);
            if ("origins:multiple".equals(typeStr) || "apace:multiple".equals(typeStr)) {
                working.remove(id);
                try {
                    Map<Identifier, JsonObject> synthetics = OriginsMultipleExpander.expand(id, json);
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
                    Map<Identifier, JsonObject> synthetics = OriginsMultipleExpander.expandAttributeMulti(id, json);
                    working.putAll(synthetics);
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand multi-modifier origins:attribute {}: {}", id, reason);
                    CompatTranslationLog.fail(id, "multi-modifier origins:attribute expansion error: " + reason);
                }
            }
        }

        Map<Identifier, PowerHolder<?>> loaded = new HashMap<>();
        Map<Identifier, JsonObject> rawSnapshot = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : working.entrySet()) {
            Identifier id = entry.getKey();
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

                // Canonicalize apoli:/apugli: -> origins: here too, to cover the
                // synthetic sub-powers emitted by multiple-expansion (which never
                // pass through the first loop).
                OriginsFormatDetector.canonicalizePowerType(json);

                // Translate Origins-format power to NeoOrigins format before parsing
                if (OriginsFormatDetector.isOriginsFormat(json)) {
                    Optional<JsonObject> translated = OriginsPowerTranslator.translate(id, json);
                    if (translated.isEmpty()) continue; // logged by translator
                    json = translated.get();
                }

                Identifier typeId = Identifier.parse(json.get("type").getAsString());
                // 2.0 legacy alias remap — transparently rewrites old type IDs.
                typeId = LegacyPowerTypeAliases.apply(typeId, json, id);
                PowerType<?> type = PowerTypes.get(typeId);
                if (type == null) {
                    // Don't warn for types handled by Route B compat — they'll
                    // be picked up by OriginsCompatPowerLoader after us.
                    String rawType = json.get("type").getAsString();
                    if (!com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader.isRouteBType(rawType)) {
                        NeoOrigins.LOGGER.warn("Unknown power type '{}' for power {}", typeId, id);
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
        recomputeUltiminePowerInUse();
        NeoOrigins.LOGGER.info("Loaded {} powers", loaded.size());

        // Per-namespace breakdown — toggled via config/neoorigins/admin.toml
        if (AdminConfig.DEBUG_POWER_LOADING.get()) {
            Map<String, Long> byNamespace = loaded.keySet().stream()
                .collect(Collectors.groupingBy(Identifier::getNamespace, Collectors.counting()));
            byNamespace.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> NeoOrigins.LOGGER.info("  [DEBUG] powers: {}  x{}", e.getKey(), e.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    private <C extends PowerConfiguration> void parsePower(
            Identifier id, Identifier typeId, PowerType<C> type, JsonObject json,
            Map<Identifier, PowerHolder<?>> target) {
        // Apply config overrides before parsing
        applyConfigOverrides(id, json);

        Component name = extractComponentField(json, "name");
        Component desc = extractComponentField(json, "description");
        boolean hidden = json.has("hidden") && json.get("hidden").isJsonPrimitive()
            && json.get("hidden").getAsJsonPrimitive().isBoolean()
            && json.get("hidden").getAsBoolean();

        // Parse top-level condition gate (optional, works for all power types).
        // Field is canonically named "power_condition" (not "condition") to avoid
        // colliding with power types that claim "condition" in their own config
        // codecs (per BuiltinPowers FieldSpecs: model_color, attribute_modifier,
        // action_on_event, modify_damage, active_ability, persistent_effect,
        // condition_passive, prevent_death, conditional).
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
        configJson.remove("hidden");
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

    /** Merges config-file overrides into the power JSON before CODEC parsing. */
    private static void applyConfigOverrides(Identifier id, JsonObject json) {
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
    public void injectExternalPowers(Map<Identifier, PowerHolder<?>> external) {
        this.injectedPowers = Collections.unmodifiableMap(new HashMap<>(external));
        this.version++;
        recomputeUltiminePowerInUse();
    }

    /** Returns all powers including Route B injected ones (used for registry sync). */
    public Map<Identifier, PowerHolder<?>> getAllPowers() {
        if (injectedPowers.isEmpty()) return powers;
        Map<Identifier, PowerHolder<?>> all = new HashMap<>(powers);
        all.putAll(injectedPowers);
        return Collections.unmodifiableMap(all);
    }

    public Map<Identifier, PowerHolder<?>> getPowers() { return powers; }

    public PowerHolder<?> getPower(Identifier id) {
        PowerHolder<?> holder = powers.get(id);
        return holder != null ? holder : injectedPowers.get(id);
    }

    public boolean hasPower(Identifier id) {
        return powers.containsKey(id) || injectedPowers.containsKey(id);
    }

    /** Post-translation raw power JSON for the creator's template loader.
     *  Returns null when this power was only loaded on the client (powers
     *  aren't synced with their bodies) or wasn't loaded at all.
     *
     *  <p>Returns a deep copy: the snapshot map holds references to the
     *  loader's working objects (see the reload loop), so callers get an
     *  isolated object they may freely mutate. */
    public JsonObject getRawPowerJson(Identifier id) {
        JsonObject json = rawPowerJson.get(id);
        return json != null ? json.deepCopy() : null;
    }
}
