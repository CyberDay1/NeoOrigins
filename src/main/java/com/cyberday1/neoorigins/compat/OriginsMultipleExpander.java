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
     * The container ("multiple") type ids, in every namespace the loader honours.
     * Exposed as a set so the schema generator can advertise all three and
     * {@code PowerEnumCheck} can exempt them from the structured-branch rule
     * without transcribing the list a second time.
     */
    public static final java.util.Set<String> MULTIPLE_TYPES = java.util.Set.of(
        "neoorigins:multiple", "origins:multiple", "apace:multiple");

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
        return MULTIPLE_TYPES.contains(type);
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
        // Top-level multiples: the id IS the original pack id (slash path from
        // the powers/ directory), which is what Apoli auto lang keys use.
        return expand(id, id.getPath(), src);
    }

    /**
     * @param legacyPath the parent's ORIGINAL Apoli-visible path — the pack-file
     *        slash path for top-level multiples, chained with "/" for nested
     *        ones (pre-2.2.8 NeoOrigins convention). Used to build the Apoli
     *        auto translation keys ({@code power.<ns>.<path>.name}) that packs
     *        like Origins++ rely on for unnamed multiples, and never derived by
     *        splitting the underscore synthetic id (sub-keys may themselves
     *        contain underscores).
     */
    private static Map<Identifier, JsonObject> expand(Identifier id, String legacyPath, JsonObject src) {
        Map<Identifier, JsonObject> result = new HashMap<>();
        List<Identifier> syntheticIds = new ArrayList<>();

        // A hidden parent multiple suppresses the whole entry in Origins; since we
        // flatten it into per-sub-power synthetic IDs (which the origin lists
        // individually), the flag must ride down onto every sub-power or they
        // surface one-by-one in the info panel.
        boolean parentHidden = readHiddenFlag(src);

        // Store display metadata from the parent multiple so the screen can
        // collapse sub-powers. Always recorded: even without inline name /
        // description, the auto_* keys let the screen resolve the Apoli auto
        // lang-file entries for the ORIGINAL parent id (issue #110 fix 1).
        MULTIPLE_DISPLAY_MAP.put(id, buildDisplay(id, legacyPath, src));

        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            String key = entry.getKey();
            if (META_KEYS.contains(key)) continue;
            if (!entry.getValue().isJsonObject()) continue;

            JsonObject subPowerJson = entry.getValue().getAsJsonObject();

            // Synthetic ID: namespace:path_subkey. Apoli joins a multiple's parent
            // path and sub-key with an underscore (e.g. "size" + "resource" ->
            // "size_resource"), and the resource/change_resource state key as well as
            // *:* self-references all assume that form. Using a slash here split the
            // identity in two (slash for visuals, underscore for logic), desyncing
            // resource bars — so we must match Apoli exactly.
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
                    Map<Identifier, JsonObject> nested =
                        expand(syntheticId, legacyPath + "/" + key, subPowerJson);
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

        MULTIPLE_DISPLAY_MAP.put(id, buildDisplay(id, id.getPath(), src));

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
     * Display metadata for a collapsed parent row: inline {@code name} /
     * {@code description} when authored, plus the Apoli auto translation keys
     * built from the parent's original (slash-form) path so lang-file-only
     * packs (Origins++) still render a proper title. The screen resolves
     * {@code auto_*} only when the lang file actually has the key.
     */
    private static JsonObject buildDisplay(Identifier id, String legacyPath, JsonObject src) {
        JsonObject display = new JsonObject();
        if (src.has("name"))        display.add("name",        src.get("name"));
        if (src.has("description")) display.add("description", src.get("description"));
        display.addProperty("auto_name", "power." + id.getNamespace() + "." + legacyPath + ".name");
        display.addProperty("auto_description", "power." + id.getNamespace() + "." + legacyPath + ".description");
        return display;
    }

    /**
     * Rewrites {@code *:*_<subkey>} self-references in a sub-power JSON to
     * the resolved synthetic form {@code namespace:parentPath_subkey}.
     */
    static JsonObject resolveSelfReferences(JsonObject json, Identifier parentId) {
        String raw = json.toString();
        if (!raw.contains("*:*")) return json;
        String resolved = raw.replace("*:*_", parentId.getNamespace() + ":" + parentId.getPath() + "_");
        resolved = resolved.replace("*:*", parentId.toString());
        return JsonParser.parseString(resolved).getAsJsonObject();
    }
}
