package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PowerDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final PowerDataManager INSTANCE = new PowerDataManager();
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/powers");

    private Map<Identifier, PowerHolder<?>> powers = new HashMap<>();

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading power file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, PowerHolder<?>> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                if (!json.has("type")) {
                    NeoOrigins.LOGGER.warn("Power {} missing 'type' field", id);
                    continue;
                }
                Identifier typeId = Identifier.parse(json.get("type").getAsString());
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
            Identifier id, PowerType<C> type, JsonObject json,
            Map<Identifier, PowerHolder<?>> target) {
        type.codec().parse(JsonOps.INSTANCE, json)
            .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse power config {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(type, config)));
    }

    public Map<Identifier, PowerHolder<?>> getPowers() { return powers; }
    public PowerHolder<?> getPower(Identifier id) { return powers.get(id); }
    public boolean hasPower(Identifier id) { return powers.containsKey(id); }
}
