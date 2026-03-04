package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class LayerDataManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final LayerDataManager INSTANCE = new LayerDataManager();

    private Map<ResourceLocation, OriginLayer> layers = new HashMap<>();
    private List<OriginLayer> sortedLayers = new ArrayList<>();

    public LayerDataManager() {
        super(GSON, "origins/origin_layers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<ResourceLocation, OriginLayer> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
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

    public Map<ResourceLocation, OriginLayer> getLayers() { return layers; }
    public List<OriginLayer> getSortedLayers() { return sortedLayers; }
    public OriginLayer getLayer(ResourceLocation id) { return layers.get(id); }
    public boolean hasLayer(ResourceLocation id) { return layers.containsKey(id); }
}
