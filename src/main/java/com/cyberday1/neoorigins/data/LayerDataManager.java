package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.google.gson.JsonArray;
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

public class LayerDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final LayerDataManager INSTANCE = new LayerDataManager();
    // NeoOrigins format: data/<ns>/origins/origin_layers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/origin_layers");
    // Origins mod format: data/<ns>/origin_layers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("origin_layers");

    private Map<Identifier, OriginLayer> layers = new HashMap<>();
    private List<OriginLayer> sortedLayers = new ArrayList<>();

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
            // Skip neoorigins namespace in compat converter — FILE_CONVERTER already handles it
            if (converter == COMPAT_CONVERTER && NeoOrigins.MOD_ID.equals(id.getNamespace())) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading layer file {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, OriginLayer> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonElement json = entry.getValue();
                if (json.isJsonObject()) {
                    JsonObject obj = json.getAsJsonObject();
                    obj.addProperty("id", id.toString());
                    normalizeLayerJson(id, obj);
                    json = obj;
                }
                OriginLayer.CODEC.parse(JsonOps.INSTANCE, json)
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
     * ComponentCodecHelper.CODEC uses Codec.STRING, so the name field must be a plain string.
     * - If name is missing: synthesize from the layer ID path
     * - If name is a JSON object (translate/text component): extract the string value
     * - If name is already a plain string: leave it unchanged
     * - Strips complex "condition" objects from origins entries (cannot be evaluated)
     */
    private static void normalizeLayerJson(Identifier id, JsonObject obj) {
        if (!obj.has("name")) {
            // No name field — synthesize from ID path (e.g. "origin" from "neoorigins:origin")
            obj.addProperty("name", id.getPath());
        } else if (obj.get("name").isJsonObject()) {
            // Component JSON object — extract string value for the plain-string codec
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
        // If name is already a plain string: no change needed — codec accepts it as-is.

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

    public Map<Identifier, OriginLayer> getLayers() { return layers; }
    public List<OriginLayer> getSortedLayers() { return sortedLayers; }
    public OriginLayer getLayer(Identifier id) { return layers.get(id); }
    public boolean hasLayer(Identifier id) { return layers.containsKey(id); }
}
