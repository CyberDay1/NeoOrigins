package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.*;

public class LayerDataManager extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

    public static final LayerDataManager INSTANCE = new LayerDataManager();
    // NeoOrigins format: data/<ns>/origins/origin_layers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/origin_layers");
    // Origins mod format: data/<ns>/origin_layers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("origin_layers");

    private static final ResourceLocation ORIGINS_ORIGIN = ResourceLocation.fromNamespaceAndPath("origins", "origin");
    private static final ResourceLocation NEO_ORIGIN = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "origin");

    private Map<ResourceLocation, OriginLayer> layers = new HashMap<>();
    private List<OriginLayer> sortedLayers = new ArrayList<>();

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> map = new HashMap<>();
        // Native format first — these are authoritative
        scanConverterStacks(FILE_CONVERTER, resourceManager, map, false);
        // Compat format second — merges into existing layers
        scanConverterStacks(COMPAT_CONVERTER, resourceManager, map, true);
        // Fold neoorigins:origin into origins:origin so all origins appear in one layer
        mergeNeoOriginsIntoCompat(map);
        return map;
    }

    /**
     * Scans all resource stacks for a converter, merging layer files that share the same ID.
     * Uses listMatchingResourceStacks() to read ALL packs, not just the top-priority one.
     */
    private void scanConverterStacks(FileToIdConverter converter, ResourceManager resourceManager,
                                     Map<ResourceLocation, JsonObject> map, boolean isCompat) {
        for (var entry : converter.listMatchingResourceStacks(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id = converter.fileToId(fileId);
            // Skip neoorigins namespace in compat converter — FILE_CONVERTER already handles it
            if (isCompat && NeoOrigins.MOD_ID.equals(id.getNamespace())) continue;

            List<Resource> resources = entry.getValue();
            for (Resource resource : resources) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    JsonObject obj = parsed.getAsJsonObject();

                    if (map.containsKey(id)) {
                        mergeOrigins(map.get(id), obj);
                    } else {
                        map.put(id, obj);
                    }
                } catch (Exception e) {
                    NeoOrigins.LOGGER.error("Error reading layer file {} from pack", fileId, e);
                }
            }
        }
    }

    /**
     * Merges origins from {@code source} into {@code target}.
     * If source has "replace": true, it replaces target entirely.
     * Otherwise, appends source's origins array into target's.
     */
    private static void mergeOrigins(JsonObject target, JsonObject source) {
        if (source.has("replace") && source.get("replace").getAsBoolean()) {
            for (var e : source.entrySet()) {
                target.add(e.getKey(), e.getValue());
            }
            return;
        }
        // Additive merge — append origins, deduplicating by ID string
        JsonArray targetOrigins = target.has("origins") && target.get("origins").isJsonArray()
            ? target.getAsJsonArray("origins") : new JsonArray();
        Set<String> existing = new HashSet<>();
        for (JsonElement el : targetOrigins) {
            existing.add(originKey(el));
        }
        JsonArray sourceOrigins = source.has("origins") && source.get("origins").isJsonArray()
            ? source.getAsJsonArray("origins") : new JsonArray();
        for (JsonElement el : sourceOrigins) {
            if (existing.add(originKey(el))) {
                targetOrigins.add(el);
            }
        }
        target.add("origins", targetOrigins);
    }

    /** Returns a stable string key for an origin entry (plain string or object with "origin" field). */
    private static String originKey(JsonElement el) {
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject() && el.getAsJsonObject().has("origin"))
            return el.getAsJsonObject().get("origin").getAsString();
        return el.toString();
    }

    /**
     * If both neoorigins:origin and origins:origin exist, merge the NeoOrigins
     * base origins into the origins:origin layer so everything appears in one tab.
     * If only neoorigins:origin exists (no compat packs), rename it to origins:origin.
     */
    private static void mergeNeoOriginsIntoCompat(Map<ResourceLocation, JsonObject> map) {
        JsonObject neo = map.get(NEO_ORIGIN);
        if (neo == null) return;

        if (map.containsKey(ORIGINS_ORIGIN)) {
            mergeOrigins(map.get(ORIGINS_ORIGIN), neo);
        } else {
            map.put(ORIGINS_ORIGIN, neo);
        }
        map.remove(NEO_ORIGIN);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<ResourceLocation, OriginLayer> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject obj = entry.getValue();
                obj.addProperty("id", id.toString());
                normalizeLayerJson(id, obj);
                OriginLayer.CODEC.parse(JsonOps.INSTANCE, obj)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.warn("Failed to parse layer {}: {}", id, err))
                    .ifPresent(layer -> loaded.put(id, layer));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading layer {}", id, e);
            }
        }
        this.layers = Collections.unmodifiableMap(loaded);
        this.sortedLayers = loaded.values().stream()
            .filter(OriginLayer::enabled)
            .sorted(Comparator.comparingInt(OriginLayer::order))
            .toList();
        NeoOrigins.LOGGER.info("Loaded {} origin layers", loaded.size());
    }

    /**
     * Normalizes Origins-format layer JSON fields to NeoOrigins format in-place.
     */
    private static void normalizeLayerJson(ResourceLocation id, JsonObject obj) {
        if (!obj.has("name")) {
            obj.addProperty("name", id.getPath());
        } else if (obj.get("name").isJsonObject()) {
            JsonObject nameObj = obj.get("name").getAsJsonObject();
            String nameStr;
            if (nameObj.has("translate")) {
                nameStr = nameObj.get("translate").getAsString();
            } else if (nameObj.has("text")) {
                nameStr = nameObj.get("text").getAsString();
            } else {
                nameStr = id.getPath();
            }
            obj.addProperty("name", nameStr);
        }

        // Strip non-string condition fields from origins entries so codec can parse them
        if (obj.has("origins") && obj.get("origins").isJsonArray()) {
            JsonArray origins = obj.getAsJsonArray("origins");
            for (JsonElement el : origins) {
                if (el.isJsonObject()) {
                    JsonObject entry = el.getAsJsonObject();
                    if (entry.has("condition") && entry.get("condition").isJsonObject()) {
                        entry.remove("condition");
                    }
                }
            }
        }
    }

    public Map<ResourceLocation, OriginLayer> getLayers() { return layers; }
    public List<OriginLayer> getSortedLayers() { return sortedLayers; }
    public OriginLayer getLayer(ResourceLocation id) { return layers.get(id); }
    public boolean hasLayer(ResourceLocation id) { return layers.containsKey(id); }
}
