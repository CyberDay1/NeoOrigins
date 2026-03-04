package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.OriginsLoadedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OriginDataManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final OriginDataManager INSTANCE = new OriginDataManager();

    private Map<ResourceLocation, Origin> origins = new HashMap<>();

    public OriginDataManager() {
        super(GSON, "origins/origins");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<ResourceLocation, Origin> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonElement json = entry.getValue();
                // Inject id field so Origins know their own id
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

    public Map<ResourceLocation, Origin> getOrigins() {
        return origins;
    }

    public Origin getOrigin(ResourceLocation id) {
        return origins.get(id);
    }

    public boolean hasOrigin(ResourceLocation id) {
        return origins.containsKey(id);
    }
}
