package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.CompatTranslationLog;
import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
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
import java.util.Optional;

public class PowerDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final PowerDataManager INSTANCE = new PowerDataManager();
    // NeoOrigins format: data/<ns>/origins/powers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/powers");
    // Origins mod format: data/<ns>/powers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    private Map<Identifier, PowerHolder<?>> powers = new HashMap<>();

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER, resourceManager, map);
        // Also scan Origins-format path (data/<ns>/powers/), skipping IDs already loaded natively
        scanConverter(COMPAT_CONVERTER, resourceManager, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager resourceManager, Map<Identifier, JsonElement> map) {
        for (var entry : converter.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = converter.fileToId(fileId);
            if (map.containsKey(id)) continue; // native format wins
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading power file {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        CompatTranslationLog.open();
        OriginsMultipleExpander.reset();

        // Build a working set, expanding any origins:multiple entries into synthetic sub-power entries
        Map<Identifier, JsonElement> working = new HashMap<>(pObject);
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            String typeStr = OriginsFormatDetector.getType(json);
            if ("origins:multiple".equals(typeStr) || "apace:multiple".equals(typeStr)) {
                working.remove(id);
                try {
                    Map<Identifier, JsonObject> synthetics = OriginsMultipleExpander.expand(id, json);
                    working.putAll(synthetics);
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand origins:multiple {}: {}", id, reason);
                    CompatTranslationLog.fail(id, "origins:multiple expansion error: " + reason);
                }
            }
        }

        Map<Identifier, PowerHolder<?>> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : working.entrySet()) {
            Identifier id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                if (!json.has("type")) {
                    NeoOrigins.LOGGER.warn("Power {} missing 'type' field", id);
                    continue;
                }

                // Translate Origins-format power to NeoOrigins format before parsing
                if (OriginsFormatDetector.isOriginsFormat(json)) {
                    Optional<JsonObject> translated = OriginsPowerTranslator.translate(id, json);
                    if (translated.isEmpty()) continue; // logged by translator
                    json = translated.get();
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

        // TEMP DIAGNOSTIC: breakdown by namespace
        Map<String, Long> byNamespace = loaded.keySet().stream()
            .collect(java.util.stream.Collectors.groupingBy(Identifier::getNamespace, java.util.stream.Collectors.counting()));
        byNamespace.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> NeoOrigins.LOGGER.info("  [DIAG] powers: {}  x{}", e.getKey(), e.getValue()));
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
