package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import net.minecraft.network.chat.Component;
import com.cyberday1.neoorigins.compat.CompatTranslationLog;
import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

public class PowerDataManager extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    public static final PowerDataManager INSTANCE = new PowerDataManager();
    // NeoOrigins format: data/<ns>/origins/powers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/powers");
    // Origins mod format: data/<ns>/powers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    private Map<ResourceLocation, PowerHolder<?>> powers = new HashMap<>();
    /** Route B powers injected by OriginsCompatPowerLoader after native loading. */
    private Map<ResourceLocation, PowerHolder<?>> injectedPowers = new HashMap<>();
    /** Bumped on every datapack reload and Route-B injection so per-player power caches can invalidate. */
    private int version = 0;
    public int version() { return version; }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER, resourceManager, map);
        scanConverter(COMPAT_CONVERTER, resourceManager, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager resourceManager, Map<ResourceLocation, JsonElement> map) {
        for (var entry : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id = converter.fileToId(fileId);
            if (map.containsKey(id)) continue; // native format wins
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading power file {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        CompatTranslationLog.open();
        OriginsMultipleExpander.reset();

        // Build a working set, expanding any origins:multiple entries into synthetic sub-power entries
        Map<ResourceLocation, JsonElement> working = new HashMap<>(pObject);
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            String typeStr = OriginsFormatDetector.getType(json);
            if ("origins:multiple".equals(typeStr) || "apace:multiple".equals(typeStr)) {
                working.remove(id);
                try {
                    Map<ResourceLocation, JsonObject> synthetics = OriginsMultipleExpander.expand(id, json);
                    working.putAll(synthetics);
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    NeoOrigins.LOGGER.warn("OriginsCompat: Failed to expand origins:multiple {}: {}", id, reason);
                    CompatTranslationLog.fail(id, "origins:multiple expansion error: " + reason);
                }
            }
        }

        Map<ResourceLocation, PowerHolder<?>> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : working.entrySet()) {
            ResourceLocation id = entry.getKey();
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

                ResourceLocation typeId = ResourceLocation.parse(json.get("type").getAsString());
                // 2.0 legacy alias remap — transparently rewrites old type IDs.
                typeId = LegacyPowerTypeAliases.apply(typeId, json, id);
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
        this.injectedPowers = new HashMap<>(); // cleared; Route B will re-inject after us
        this.version++;
        NeoOrigins.LOGGER.info("Loaded {} powers", loaded.size());

        // Per-namespace breakdown — toggled via config/neoorigins-common.toml
        if (NeoOriginsConfig.DEBUG_POWER_LOADING.get()) {
            Map<String, Long> byNamespace = loaded.keySet().stream()
                .collect(Collectors.groupingBy(ResourceLocation::getNamespace, Collectors.counting()));
            byNamespace.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> NeoOrigins.LOGGER.info("  [DEBUG] powers: {}  x{}", e.getKey(), e.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    private <C extends PowerConfiguration> void parsePower(
            ResourceLocation id, PowerType<C> type, JsonObject json,
            Map<ResourceLocation, PowerHolder<?>> target) {
        // Apply config overrides before parsing
        applyConfigOverrides(id, json);

        Component name = extractComponentField(json, "name");
        Component desc = extractComponentField(json, "description");

        // Strip display fields so they don't confuse the typed codec.
        JsonObject configJson = json.deepCopy();
        configJson.remove("name");
        configJson.remove("description");

        type.codec().parse(JsonOps.INSTANCE, configJson)
            .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse power config {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(id, type, config, name, desc)));
    }

    /** Merges config-file overrides into the power JSON before CODEC parsing. */
    private static void applyConfigOverrides(ResourceLocation id, JsonObject json) {
        Map<String, Object> overrides = NeoOriginsConfig.getPowerOverrides(id.toString());
        if (overrides == null) return;

        for (var entry : overrides.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Number n) {
                json.addProperty(field, n);
            } else if (value instanceof Boolean b) {
                json.addProperty(field, b);
            } else {
                json.addProperty(field, value.toString());
            }
        }
        NeoOrigins.LOGGER.info("Applied {} config override(s) to power {}: {}",
            overrides.size(), id, overrides);
    }

    private static Component extractComponentField(JsonObject json, String field) {
        if (!json.has(field)) return Component.empty();
        JsonElement el = json.get(field);
        if (el.isJsonPrimitive()) return Component.translatable(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text"))      return Component.literal(obj.get("text").getAsString());
            if (obj.has("translate")) return Component.translatable(obj.get("translate").getAsString());
        }
        return Component.empty();
    }

    /** Called by OriginsCompatPowerLoader after its apply() to inject Route B powers. */
    public void injectExternalPowers(Map<ResourceLocation, PowerHolder<?>> external) {
        this.injectedPowers = Collections.unmodifiableMap(new HashMap<>(external));
        this.version++;
    }

    /** Returns all powers including Route B injected ones (used for registry sync). */
    public Map<ResourceLocation, PowerHolder<?>> getAllPowers() {
        if (injectedPowers.isEmpty()) return powers;
        Map<ResourceLocation, PowerHolder<?>> all = new HashMap<>(powers);
        all.putAll(injectedPowers);
        return Collections.unmodifiableMap(all);
    }

    public Map<ResourceLocation, PowerHolder<?>> getPowers() { return powers; }

    public PowerHolder<?> getPower(ResourceLocation id) {
        PowerHolder<?> holder = powers.get(id);
        return holder != null ? holder : injectedPowers.get(id);
    }

    public boolean hasPower(ResourceLocation id) {
        return powers.containsKey(id) || injectedPowers.containsKey(id);
    }
}
