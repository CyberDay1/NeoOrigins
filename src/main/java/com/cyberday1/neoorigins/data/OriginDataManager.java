package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
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
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading origin file {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, Origin> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();

                // Normalize Origins-format origin fields to NeoOrigins format
                if (OriginsFormatDetector.isOriginsOriginFormat(json)) {
                    json = OriginsOriginTranslator.normalize(id, json);
                }

                // Always add the id field after normalization
                json.addProperty("id", id.toString());

                Origin.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse origin {}: {}", id, err))
                    .ifPresent(origin -> loaded.put(id, origin));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading origin {}", id, e);
            }
        }
        // Filter out disabled built-in origins
        int beforeFilter = loaded.size();
        loaded.entrySet().removeIf(entry -> NeoOriginsConfig.isOriginDisabled(entry.getKey()));
        if (loaded.size() < beforeFilter) {
            NeoOrigins.LOGGER.info("Disabled {} built-in origin(s) via config", beforeFilter - loaded.size());
        }

        // Filter out addon origins with too many broken powers
        double minRatio = NeoOriginsConfig.COMPAT_MIN_POWER_RATIO.get();
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
        this.version++;
        NeoOrigins.LOGGER.info("Loaded {} origins", loaded.size());

        if (NeoOriginsConfig.DEBUG_POWER_LOADING.get()) {
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
    public Origin getOrigin(Identifier id) { return origins.get(id); }
    public boolean hasOrigin(Identifier id) { return origins.containsKey(id); }
}
