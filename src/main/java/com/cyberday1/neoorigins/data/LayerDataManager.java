package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.google.gson.JsonElement;
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
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/origin_layers");

    private Map<Identifier, OriginLayer> layers = new HashMap<>();
    private List<OriginLayer> sortedLayers = new ArrayList<>();

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading layer file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, OriginLayer> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonElement json = entry.getValue();
                if (json.isJsonObject()) {
                    json.getAsJsonObject().addProperty("id", id.toString());
                }
                OriginLayer.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse layer {}: {}", id, err))
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

    public Map<Identifier, OriginLayer> getLayers() { return layers; }
    public List<OriginLayer> getSortedLayers() { return sortedLayers; }
    public OriginLayer getLayer(Identifier id) { return layers.get(id); }
    public boolean hasLayer(Identifier id) { return layers.containsKey(id); }
}
