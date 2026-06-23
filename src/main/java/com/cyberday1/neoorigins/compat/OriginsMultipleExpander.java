package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Expands origins:multiple power JSONs into synthetic per-sub-power entries.
 *
 * For a multiple power with id "fairy:origins/flight", sub-keys like "wings" and "speed"
 * become synthetic powers "fairy:origins/flight/wings" and "fairy:origins/flight/speed".
 *
 * The expansion map is populated during PowerDataManager.apply() and consumed by
 * OriginsOriginTranslator during OriginDataManager.apply() to rewrite origin power lists.
 */
public final class OriginsMultipleExpander {

    /**
     * Maps each origins:multiple power ID to the list of synthetic sub-power IDs it expanded into.
     * Cleared and repopulated on each reload. Accessed by OriginsOriginTranslator.
     */
    public static final Map<Identifier, List<Identifier>> MULTIPLE_EXPANSION_MAP = new HashMap<>();

    /**
     * Maps each origins:multiple power ID to its display metadata (name/description JsonElements).
     * Used by the origin selection screen to collapse sub-powers back into one entry per parent.
     */
    public static final Map<Identifier, JsonObject> MULTIPLE_DISPLAY_MAP = new HashMap<>();

    /** Keys in an origins:multiple JSON that are metadata, not sub-power entries. */
    /** Keys in an origins:multiple JSON that are metadata, not sub-power entries. Exposed for Route B loader. */
    public static final Set<String> META_KEYS = Set.of(
        "type", "name", "description", "hidden", "loading_priority", "badges",
        "order", "special", "unchoosable", "condition"
    );

    /** Replace the expansion/display maps with data received from the server (client-side only). */
    public static void setClientData(Map<Identifier, List<Identifier>> expansionMap,
                                     Map<Identifier, JsonObject> displayMap) {
        MULTIPLE_EXPANSION_MAP.clear();
        MULTIPLE_EXPANSION_MAP.putAll(expansionMap);
        MULTIPLE_DISPLAY_MAP.clear();
        MULTIPLE_DISPLAY_MAP.putAll(displayMap);
    }

    private OriginsMultipleExpander() {}

    /**
     * True if {@code type} is a "multiple" container power in any recognized
     * namespace: the native {@code neoorigins:multiple}, the Apoli/Origins
     * {@code origins:multiple} (which {@code apoli:}/{@code apugli:} canonicalize
     * to), or Apace's {@code apace:multiple}. All three flatten through
     * {@link #expand} into synthetic per-sub-power entries, so the native form is
     * the first-class, in-namespace way to author a sub-power container while the
     * {@code origins:}/{@code apace:} forms remain for imported packs.
     */
    public static boolean isMultipleType(String type) {
        return "neoorigins:multiple".equals(type)
            || "origins:multiple".equals(type)
            || "apace:multiple".equals(type);
    }

    /** True if {@code json} carries a boolean {@code "hidden": true}. */
    public static boolean readHiddenFlag(JsonObject json) {
        return json.has("hidden") && json.get("hidden").isJsonPrimitive()
            && json.get("hidden").getAsJsonPrimitive().isBoolean()
            && json.get("hidden").getAsBoolean();
    }

    /** Clears the expansion and display maps. Call at the start of PowerDataManager.apply(). */
    public static void reset() {
        MULTIPLE_EXPANSION_MAP.clear();
        MULTIPLE_DISPLAY_MAP.clear();
    }

    /**
     * Expands an origins:multiple JSON into a map of synthetic-id → translated-json.
     * Also records the expansion in MULTIPLE_EXPANSION_MAP for later origin rewriting.
     *
     * Sub-powers that fail translation are omitted from the result (already logged).
     *
     * @param id  The Identifier of the multiple power (e.g. fairy:origins/flight)
     * @param src The full JSON of the origins:multiple power
     * @return Map of synthetic Identifier → translated NeoOrigins power JSON
     */
    public static Map<Identifier, JsonObject> expand(Identifier id, JsonObject src) {
        Map<Identifier, JsonObject> result = new HashMap<>();
        List<Identifier> syntheticIds = new ArrayList<>();

        // A hidden parent multiple suppresses the whole entry in Origins; since we
        // flatten it into per-sub-power synthetic IDs (which the origin lists
        // individually), the flag must ride down onto every sub-power or they
        // surface one-by-one in the info panel.
        boolean parentHidden = readHiddenFlag(src);

        // Store display metadata from the parent multiple so the screen can collapse sub-powers.
        if (src.has("name") || src.has("description")) {
            JsonObject display = new JsonObject();
            if (src.has("name"))        display.add("name",        src.get("name"));
            if (src.has("description")) display.add("description", src.get("description"));
            MULTIPLE_DISPLAY_MAP.put(id, display);
        }

        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            String key = entry.getKey();
            if (META_KEYS.contains(key)) continue;
            if (!entry.getValue().isJsonObject()) continue;

            JsonObject subPowerJson = entry.getValue().getAsJsonObject();

            // Synthetic ID: namespace:parentPath_subkey — the Apoli convention.
            // A multiple's sub-powers are addressed as parentPath + "_" + subkey
            // (e.g. "..._resource"), which is exactly how datapacks reference them
            // in change_resource / has_power / *:*_subkey. Joining with "/" here
            // (the old behaviour) desynced authored underscore references from the
            // registered power id.
            Identifier syntheticId = Identifier.fromNamespaceAndPath(
                id.getNamespace(),
                id.getPath() + "_" + key
            );

            // Resolve *:* self-references: in Origins/Apoli, "*:*_subkey" within
            // a multiple refers to the sibling sub-power "parentId/subkey".
            subPowerJson = resolveSelfReferences(subPowerJson, id);

            // Canonicalize apoli:/apugli: sub-power types to origins: BEFORE the
            // format check — top-level powers are canonicalized by the loaders,
            // but nested sub-powers only pass through here. Without this, an
            // apoli:-typed sub-power is misclassified as "native format" below
            // and a nested apoli:multiple is never recursed into.
            OriginsFormatDetector.canonicalizePowerType(subPowerJson);

            // Check for nested multiple (any namespace) — recurse. This MUST run
            // before the native-format passthrough below, because a native
            // neoorigins:multiple sub-power is "not Origins format" and would
            // otherwise be stored as a single opaque power instead of flattened.
            String subType = OriginsFormatDetector.getType(subPowerJson);
            if (parentHidden && !readHiddenFlag(subPowerJson)) subPowerJson.addProperty("hidden", true);
            if (isMultipleType(subType)) {
                try {
                    Map<Identifier, JsonObject> nested = expand(syntheticId, subPowerJson);
                    result.putAll(nested);
                    // Add all nested synthetic IDs to this level's list
                    if (MULTIPLE_EXPANSION_MAP.containsKey(syntheticId)) {
                        syntheticIds.addAll(MULTIPLE_EXPANSION_MAP.get(syntheticId));
                    } else {
                        syntheticIds.addAll(nested.keySet());
                    }
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand nested multiple {}: {}", syntheticId, reason);
                    CompatTranslationLog.fail(syntheticId, "nested origins:multiple expansion error: " + reason);
                }
                continue;
            }

            if (!OriginsFormatDetector.isOriginsFormat(subPowerJson)) {
                // Sub-power is already NeoOrigins format — pass through as-is so it
                // re-enters native power loading. This is the normal case for a
                // native neoorigins:multiple container's sub-powers.
                NeoOrigins.LOGGER.debug("OriginsCompat: multiple sub-power {} is not Origins format, using as-is", syntheticId);
                result.put(syntheticId, subPowerJson);
                syntheticIds.add(syntheticId);
                CompatTranslationLog.pass(syntheticId, "multiple sub-power (native format)");
                continue;
            }

            // Always track this synthetic ID so OriginDataManager includes it in power lists.
            // Route B may load the power even if Route A skips it.
            syntheticIds.add(syntheticId);

            // Translate the sub-power via Route A
            Optional<JsonObject> translated = OriginsPowerTranslator.translate(syntheticId, subPowerJson);
            if (translated.isPresent()) {
                JsonObject out = translated.get();
                // Ensure the parent's hidden flag survives translation (the
                // translator may not copy it through verbatim).
                if (parentHidden && !readHiddenFlag(out)) out.addProperty("hidden", true);
                result.put(syntheticId, out);
            }
            // If empty, Route B loader will handle it if the type is supported.
        }

        if (!syntheticIds.isEmpty()) {
            MULTIPLE_EXPANSION_MAP.put(id, Collections.unmodifiableList(syntheticIds));
        }

        return result;
    }

    /**
     * Expands an {@code origins:attribute} power that ships a {@code modifiers}
     * array (multiple modifiers in one power) into N synthetic
     * single-modifier {@code origins:attribute} powers — one per array entry.
     *
     * <p>{@code OriginsPowerTranslator.translateAttribute} can only emit one
     * {@code neoorigins:attribute_modifier} per call; multi-modifier authors
     * (very common in Apoli-derivative packs — MoR Pixie pixie_properties has
     * 7 modifiers in one power) would silently lose all but the first
     * modifier without this pre-pass.
     *
     * <p>Synthetic IDs use the {@code <originalPath>/mod_<index>} pattern,
     * matching the {@code origins:multiple} convention so the expansion map
     * and origin power-list rewrite work uniformly.
     *
     * <p>Returns an empty map (and does not record an expansion) if the JSON
     * has fewer than two modifiers — caller can fall through to the normal
     * single-power translation path.
     */
    public static Map<Identifier, JsonObject> expandAttributeMulti(Identifier id, JsonObject src) {
        if (!src.has("modifiers") || !src.get("modifiers").isJsonArray()) return Map.of();
        com.google.gson.JsonArray modifiers = src.getAsJsonArray("modifiers");
        if (modifiers.size() < 2) return Map.of();

        Map<Identifier, JsonObject> result = new HashMap<>();
        List<Identifier> syntheticIds = new ArrayList<>();

        if (src.has("name") || src.has("description")) {
            JsonObject display = new JsonObject();
            if (src.has("name"))        display.add("name",        src.get("name"));
            if (src.has("description")) display.add("description", src.get("description"));
            MULTIPLE_DISPLAY_MAP.put(id, display);
        }

        for (int i = 0; i < modifiers.size(); i++) {
            JsonElement el = modifiers.get(i);
            if (!el.isJsonObject()) continue;

            Identifier syntheticId = Identifier.fromNamespaceAndPath(
                id.getNamespace(),
                id.getPath() + "/mod_" + i
            );

            // Build a single-modifier origins:attribute that the regular
            // translator path will compile into a neoorigins:attribute_modifier.
            JsonObject sub = new JsonObject();
            sub.addProperty("type", "origins:attribute");
            sub.add("modifier", el);
            // Carry top-level condition / equipment_condition / location_condition
            // through verbatim — they apply to every modifier in the original.
            for (String passthrough : new String[] {"condition", "equipment_condition", "location_condition"}) {
                if (src.has(passthrough)) sub.add(passthrough, src.get(passthrough));
            }

            syntheticIds.add(syntheticId);
            Optional<JsonObject> translated = OriginsPowerTranslator.translate(syntheticId, sub);
            if (translated.isPresent()) {
                result.put(syntheticId, translated.get());
            }
        }

        if (!syntheticIds.isEmpty()) {
            MULTIPLE_EXPANSION_MAP.put(id, Collections.unmodifiableList(syntheticIds));
        }
        return result;
    }

    /**
     * Rewrites {@code *:*_<subkey>} self-references in a sub-power JSON to
     * the resolved synthetic form {@code namespace:parentPath_subkey} (Apoli
     * convention — matches how the synthetic sub-power id is built above).
     */
    static JsonObject resolveSelfReferences(JsonObject json, Identifier parentId) {
        String raw = json.toString();
        if (!raw.contains("*:*")) return json;
        String resolved = raw.replace("*:*_", parentId.getNamespace() + ":" + parentId.getPath() + "_");
        resolved = resolved.replace("*:*", parentId.toString());
        return JsonParser.parseString(resolved).getAsJsonObject();
    }
}
