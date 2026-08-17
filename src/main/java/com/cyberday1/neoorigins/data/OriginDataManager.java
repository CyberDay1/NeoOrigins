package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.OriginsLoadedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.compat.CompatTranslationLog;
import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.compat.OriginsOriginTranslator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;

import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OriginDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final OriginDataManager INSTANCE = new OriginDataManager();
    // NeoOrigins format: data/<ns>/origins/origins/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/origins");
    // Origins mod format: data/<ns>/origins/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("origins");

    private Map<Identifier, Origin> origins = new HashMap<>();
    /** Raw post-normalize JSON for each loaded origin id, kept so the in-game
     *  creator's "Load template" path can clone an existing origin's body
     *  verbatim (display fields, impact, order, power list) without having to
     *  re-serialize a parsed {@link Origin} through {@link Origin#CODEC}.
     *  Headless tests don't populate this; templates simply find nothing. */
    private Map<Identifier, JsonObject> rawOriginJson = new HashMap<>();
    /** Bumped on every datapack reload so per-player power caches can invalidate. */
    private int version = 0;
    public int version() { return version; }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER, resourceManager, map);
        // Also scan Origins-format path (data/<ns>/origins/), skipping IDs already loaded natively
        scanConverter(COMPAT_CONVERTER, resourceManager, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager resourceManager, Map<Identifier, JsonElement> map) {
        for (var entry : converter.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = converter.fileToId(fileId);
            if (map.containsKey(id)) continue; // native format wins
            // Skip neoorigins namespace in the compat converter — FILE_CONVERTER already handles it
            if (converter == COMPAT_CONVERTER && NeoOrigins.MOD_ID.equals(id.getNamespace())) continue;
            // Skip files under native data-type sub-folders — the native FILE_CONVERTER handles those.
            // Without this filter, the compat path tries to parse `data/<ns>/origins/powers/foo.json`
            // as a flat-layout origin and emits spurious "No key description; No key name" errors.
            // Verified 2026-05-28 via headless server boot with a class-type datapack.
            // NOTE: `origins/` is intentionally NOT skipped here. Native neoorigins-namespace ids
            // are already skipped above (line 61), so this guard only sees third-party compat packs.
            // Such packs may nest origins at `data/<ns>/origins/origins/<name>.json` and reference
            // them by their Origins-mod nested id `<ns>:origins/<name>`; skipping the `origins/`
            // prefix would drop those, making their layers resolve to null and auto-skip in the picker.
            if (converter == COMPAT_CONVERTER) {
                String path = id.getPath();
                if (path.startsWith("powers/") || path.startsWith("origin_layers/")
                        || path.startsWith("mob_origins/")) {
                    continue;
                }
            }
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading origin file {}", fileId, e);
            }
        }
    }

    /**
     * Drops {@code null} entries from an origin's power-id lists, returning how
     * many were removed.
     *
     * <p>A single null in {@code powers} fails the whole {@link Origin} codec, so
     * the origin does not load at all: it is absent from the picker, and the only
     * trace is {@code "Not a string: null"} repeated once per null with no key
     * name and no id. A live server ran an origin with five null power entries and
     * lost the entire origin to it.
     *
     * <p>Dropping them instead keeps every power the author actually named, which
     * matches how the rest of the compat layer treats a bad leaf — one unreadable
     * entry should cost that entry, not the whole file. The caller warns with the
     * origin id so the author can still find and fix it.
     */
    static int stripNullPowerEntries(JsonObject json) {
        int removed = stripNulls(json, "powers");
        removed += stripNulls(json, "upgrades");
        if (json.has("tier_powers") && json.get("tier_powers").isJsonArray()) {
            for (JsonElement overlay : json.getAsJsonArray("tier_powers")) {
                if (!overlay.isJsonObject()) continue;
                removed += stripNulls(overlay.getAsJsonObject(), "add");
                removed += stripNulls(overlay.getAsJsonObject(), "remove");
            }
        }
        return removed;
    }

    /** Rewrites {@code owner[field]} without its null entries; returns the count dropped. */
    private static int stripNulls(JsonObject owner, String field) {
        if (!owner.has(field) || !owner.get(field).isJsonArray()) return 0;
        com.google.gson.JsonArray original = owner.getAsJsonArray(field);
        com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
        int removed = 0;
        for (JsonElement el : original) {
            if (el == null || el.isJsonNull()) removed++;
            else kept.add(el);
        }
        if (removed > 0) owner.add(field, kept);
        return removed;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, Origin> loaded = new HashMap<>();
        Map<Identifier, JsonObject> rawSnapshot = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();

                // Top-level required_mods gate — skip origins whose target mod(s)
                // are absent so they never load or appear in the picker (see ModGate).
                if (!ModGate.satisfied(json.get("required_mods"))) continue;

                // Normalize Origins-format origin fields to NeoOrigins format
                if (OriginsFormatDetector.isOriginsOriginFormat(json)) {
                    json = OriginsOriginTranslator.normalize(id, json);
                }

                // Rewrite multiple-power references for EVERY origin, native or
                // foreign. Native-format origins skip normalize() above, so without
                // this their power list would keep referencing the parent multiple
                // ID that PowerDataManager already removed — leaving the multiple's
                // sub-powers registered but referenced by nobody. Idempotent after
                // normalize (synthetic IDs are not expansion-map keys).
                OriginsOriginTranslator.rewriteMultiplePowerRefs(json);

                // Drop null power ids before parsing — a single null fails the
                // whole codec and costs the entire origin (see stripNullPowerEntries).
                int droppedNulls = stripNullPowerEntries(json);
                if (droppedNulls > 0) {
                    NeoOrigins.LOGGER.warn("Origin {} lists {} null power entr{} — dropping them and loading the rest. "
                        + "Check the file for a trailing comma or an empty slot in \"powers\"/\"upgrades\"/\"tier_powers\".",
                        id, droppedNulls, droppedNulls == 1 ? "y" : "ies");
                }

                // Always add the id field after normalization
                json.addProperty("id", id.toString());

                final JsonObject finalJson = json;
                Origin.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse origin {}: {}", id, err))
                    .ifPresent(origin -> {
                        loaded.put(id, origin);
                        // Stash a deep copy so the template path can hand back
                        // the exact body that was just successfully parsed,
                        // unaffected by any later mutation of `finalJson`.
                        rawSnapshot.put(id, finalJson.deepCopy());
                    });
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading origin {}", id, e);
            }
        }
        // NOTE: Config-disabled origins are NOT removed here — they remain registered
        // so that /neoorigins set and other commands can still reference them.
        // The selection screen filters them out via ContentTogglesConfig.isOriginDisabled().

        // Filter out addon origins with too many broken powers
        double minRatio = AdminConfig.COMPAT_MIN_POWER_RATIO.get();
        if (minRatio > 0.0) {
            int compatFiltered = 0;
            var it = loaded.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                // Skip neoorigins namespace — our own origins are always shown
                if (NeoOrigins.MOD_ID.equals(entry.getKey().getNamespace())) continue;
                Origin origin = entry.getValue();
                if (origin.powers().isEmpty()) continue;
                long loadedPowers = origin.powers().stream()
                    .filter(PowerDataManager.INSTANCE::hasPower)
                    .count();
                double ratio = (double) loadedPowers / origin.powers().size();
                if (ratio < minRatio) {
                    NeoOrigins.LOGGER.info("[Compat] Hiding origin {} — only {}/{} powers loaded ({} < {})",
                        entry.getKey(), loadedPowers, origin.powers().size(),
                        String.format("%.0f%%", ratio * 100), String.format("%.0f%%", minRatio * 100));
                    it.remove();
                    compatFiltered++;
                }
            }
            if (compatFiltered > 0) {
                NeoOrigins.LOGGER.info("[Compat] Hidden {} addon origin(s) with insufficient power support", compatFiltered);
            }
        }

        this.origins = Collections.unmodifiableMap(loaded);
        // Prune rawSnapshot to only the ids that survived the compat-filter loop
        // above so template lookups don't expose origins that were intentionally
        // hidden from players.
        rawSnapshot.keySet().retainAll(loaded.keySet());
        this.rawOriginJson = Collections.unmodifiableMap(rawSnapshot);
        this.version++;
        NeoOrigins.LOGGER.info("Loaded {} origins", loaded.size());

        if (AdminConfig.DEBUG_POWER_LOADING.get()) {
            Map<String, List<String>> byNamespace = new HashMap<>();
            for (var entry : loaded.entrySet()) {
                byNamespace.computeIfAbsent(entry.getKey().getNamespace(), k -> new java.util.ArrayList<>())
                    .add(entry.getKey().getPath());
            }
            byNamespace.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> NeoOrigins.LOGGER.info("  [DIAG] origins namespace '{}': {}", e.getKey(), e.getValue()));
        }
        NeoForge.EVENT_BUS.post(new OriginsLoadedEvent());
        CompatTranslationLog.close();
    }

    /** Replace the origin registry with data received from the server (client-side only). */
    public void setClientData(Map<Identifier, Origin> clientOrigins) {
        this.origins = Collections.unmodifiableMap(new HashMap<>(clientOrigins));
        this.version++;
    }

    public Map<Identifier, Origin> getOrigins() { return origins; }
    public Origin getOrigin(Identifier id) { return origins.get(LegacyOriginIds.remap(id)); }
    public boolean hasOrigin(Identifier id) { return origins.containsKey(LegacyOriginIds.remap(id)); }

    /** Raw post-normalize origin JSON for the creator's template loader.
     *  Returns null when the id was loaded from a non-resource path
     *  (client sync, headless test). */
    public JsonObject getRawOriginJson(Identifier id) {
        return rawOriginJson.get(id);
    }

    /** Public re-prepare+re-apply hook so {@code NeoOrigins.onServerStarting}
     *  can refresh after the 26.1 item-component bind (initial reload happens
     *  before components are bound; icons fall back to EMPTY until this runs). */
    public void reloadFromResources(ResourceManager rm, ProfilerFiller profiler) {
        apply(prepare(rm, profiler), rm, profiler);
    }
}
