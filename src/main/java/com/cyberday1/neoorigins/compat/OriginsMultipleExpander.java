package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

            // Synthetic ID: namespace:path/subkey
            Identifier syntheticId = Identifier.fromNamespaceAndPath(
                id.getNamespace(),
                id.getPath() + "/" + key
            );

            if (!OriginsFormatDetector.isOriginsFormat(subPowerJson)) {
                // Sub-power is already NeoOrigins format (unusual but pass through)
                NeoOrigins.LOGGER.debug("OriginsCompat: multiple sub-power {} is not Origins format, using as-is", syntheticId);
                result.put(syntheticId, subPowerJson);
                syntheticIds.add(syntheticId);
                CompatTranslationLog.pass(syntheticId, "origins:multiple sub-power (native format)");
                continue;
            }

            // Check for nested origins:multiple — recurse
            String subType = OriginsFormatDetector.getType(subPowerJson);
            if ("origins:multiple".equals(subType) || "apace:multiple".equals(subType)) {
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

            // Always track this synthetic ID so OriginDataManager includes it in power lists.
            // Route B may load the power even if Route A skips it.
            syntheticIds.add(syntheticId);

            // Translate the sub-power via Route A
            Optional<JsonObject> translated = OriginsPowerTranslator.translate(syntheticId, subPowerJson);
            if (translated.isPresent()) {
                result.put(syntheticId, translated.get());
            }
            // If empty, Route B loader will handle it if the type is supported.
        }

        if (!syntheticIds.isEmpty()) {
            MULTIPLE_EXPANSION_MAP.put(id, Collections.unmodifiableList(syntheticIds));
        }

        return result;
    }
}
