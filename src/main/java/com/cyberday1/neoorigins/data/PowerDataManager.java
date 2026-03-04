package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PowerDataManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final PowerDataManager INSTANCE = new PowerDataManager();

    private Map<ResourceLocation, PowerHolder<?>> powers = new HashMap<>();

    public PowerDataManager() {
        super(GSON, "origins/powers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<ResourceLocation, PowerHolder<?>> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                if (!json.has("type")) {
                    NeoOrigins.LOGGER.warn("Power {} missing 'type' field", id);
                    continue;
                }
                ResourceLocation typeId = ResourceLocation.parse(json.get("type").getAsString());
                PowerType<?> type = PowerTypes.get(typeId);
                if (type == null) {
                    NeoOrigins.LOGGER.warn("Unknown power type '{}' for power {}", typeId, id);
                    continue;
                }
                parsePower(id, type, json, loaded);
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading power {}", id, e);
            }
        }
        this.powers = Collections.unmodifiableMap(loaded);
        NeoOrigins.LOGGER.info("Loaded {} powers", loaded.size());
    }

    @SuppressWarnings("unchecked")
    private <C extends PowerConfiguration> void parsePower(
            ResourceLocation id, PowerType<C> type, JsonObject json,
            Map<ResourceLocation, PowerHolder<?>> target) {
        type.codec().parse(JsonOps.INSTANCE, json)
            .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse power config {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(type, config)));
    }

    public Map<ResourceLocation, PowerHolder<?>> getPowers() { return powers; }
    public PowerHolder<?> getPower(ResourceLocation id) { return powers.get(id); }
    public boolean hasPower(ResourceLocation id) { return powers.containsKey(id); }
}
