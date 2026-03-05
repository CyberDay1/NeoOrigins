package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.OriginsLoadedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.google.gson.JsonElement;
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
import java.util.Map;

public class OriginDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final OriginDataManager INSTANCE = new OriginDataManager();
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/origins");

    private Map<Identifier, Origin> origins = new HashMap<>();

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading origin file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, Origin> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonElement json = entry.getValue();
                if (json.isJsonObject()) {
                    json.getAsJsonObject().addProperty("id", id.toString());
                }
                Origin.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse origin {}: {}", id, err))
                    .ifPresent(origin -> loaded.put(id, origin));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading origin {}", id, e);
            }
        }
        this.origins = Collections.unmodifiableMap(loaded);
        NeoOrigins.LOGGER.info("Loaded {} origins", loaded.size());
        NeoForge.EVENT_BUS.post(new OriginsLoadedEvent());
    }

    public Map<Identifier, Origin> getOrigins() { return origins; }
    public Origin getOrigin(Identifier id) { return origins.get(id); }
    public boolean hasOrigin(Identifier id) { return origins.containsKey(id); }
}
