package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<ns>/origins/mob_origins/<name>.json} into {@link MobOrigin}
 * instances. Mirrors {@link OriginDataManager} (same reload-listener base, same
 * id-from-path injection) but has NO Origins-mod compat converter — mob origins
 * are a NeoOrigins-native concept.
 *
 * <p>Registered LAST in {@code NeoOrigins.onAddReloadListeners} (after
 * {@code layer_data}); only needs powers, which load earlier.
 */
public class MobOriginDataManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final MobOriginDataManager INSTANCE = new MobOriginDataManager();
    // NeoOrigins-native format only: data/<ns>/origins/mob_origins/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/mob_origins");

    private Map<Identifier, MobOrigin> mobOrigins = new HashMap<>();
    /** Bumped on every datapack reload so a future per-entity power cache can invalidate. */
    private int version = 0;
    public int version() { return version; }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading mob origin file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, MobOrigin> loaded = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : object.entrySet()) {
            Identifier id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                json.addProperty("id", id.toString()); // injected from path, like OriginDataManager
                MobOrigin.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse mob origin {}: {}", id, err))
                    .ifPresent(mo -> loaded.put(id, mo));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading mob origin {}", id, e);
            }
        }
        this.mobOrigins = Collections.unmodifiableMap(loaded);
        this.version++;
        NeoOrigins.LOGGER.info("Loaded {} mob origins", loaded.size());
    }

    public Map<Identifier, MobOrigin> getMobOrigins() { return mobOrigins; }

    public MobOrigin getMobOrigin(Identifier id) { return mobOrigins.get(id); }

    public boolean hasMobOrigin(Identifier id) { return mobOrigins.containsKey(id); }

    /**
     * All mob origins whose {@link com.cyberday1.neoorigins.api.mob_origin.EntityTargetSpec}
     * matches {@code type}. Linear scan — fine for the data sizes here and for
     * Phase 1; Phase 2 adds a reload-invalidated type→candidates cache if
     * spawn-time profiling warrants it.
     */
    public List<MobOrigin> candidatesFor(EntityType<?> type) {
        List<MobOrigin> out = new ArrayList<>();
        for (MobOrigin mo : mobOrigins.values()) {
            if (mo.target().matches(type)) out.add(mo);
        }
        return out;
    }
}
