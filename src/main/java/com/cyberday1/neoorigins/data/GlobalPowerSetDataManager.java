package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.global_power.GlobalPowerSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/<ns>/global_powers/<name>.json} into {@link GlobalPowerSet}
 * instances — NeoOrigins' port of Apoli's {@code apoli:global} "Global Power Set"
 * feature. A global power set grants powers to entities (players AND/OR mobs)
 * without an origin assignment.
 *
 * <p>Registered AFTER {@link MobOriginDataManager} in
 * {@code NeoOrigins.onAddReloadListeners} — it only resolves power ids, which the
 * earlier power loaders have already populated.
 *
 * <p>The {@code order} field (default 0) determines apply order: lower applies
 * first. {@link #playerPowersOrdered()} and {@link #mobPowersOrdered(EntityType)}
 * both honour it by sorting candidate sets by {@code (order, id)} and walking
 * their {@code powers} lists in declaration order, de-duplicating so a power
 * referenced by several sets is granted once.
 */
public class GlobalPowerSetDataManager
        extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    public static final GlobalPowerSetDataManager INSTANCE = new GlobalPowerSetDataManager();
    // Apoli format: data/<ns>/global_powers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("global_powers");

    private List<GlobalPowerSet> sets = List.of();
    /** Bumped on every datapack reload so callers can detect changes. */
    private int version = 0;
    public int version() { return version; }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading global power set file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<GlobalPowerSet> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                json.addProperty("id", id.toString()); // injected from path, like MobOriginDataManager
                GlobalPowerSet.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse global power set {}: {}", id, err))
                    .ifPresent(loaded::add);
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading global power set {}", id, e);
            }
        }
        // Deterministic apply order: (order asc, then id) so the grant order is
        // stable and de-duplication is reproducible across reloads.
        loaded.sort(Comparator
            .comparingInt(GlobalPowerSet::order)
            .thenComparing(s -> s.id().toString()));
        this.sets = Collections.unmodifiableList(loaded);
        this.version++;
        NeoOrigins.LOGGER.info("Loaded {} global power sets", loaded.size());
    }

    /** All loaded sets, already sorted by {@code (order, id)}. */
    public List<GlobalPowerSet> getSets() { return sets; }

    /**
     * Ordered, de-duplicated power ids that apply to the player entity
     * ({@code minecraft:player} or any set with no {@code entity_types}).
     * Order honours {@code order} (sets pre-sorted) then declaration order
     * within each set.
     */
    public List<ResourceLocation> playerPowersOrdered() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (GlobalPowerSet set : sets) {
            if (set.matchesPlayer()) out.addAll(set.powers());
        }
        return new ArrayList<>(out);
    }

    /**
     * Ordered, de-duplicated power ids that apply to a mob of {@code type}.
     * Order honours {@code order} then declaration order within each set.
     * Mob-applicability of the powers themselves is filtered by the caller
     * (only {@code appliesToMobs()} powers are applied).
     */
    public List<ResourceLocation> mobPowersOrdered(EntityType<?> type) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (GlobalPowerSet set : sets) {
            if (set.matchesEntityType(type)) out.addAll(set.powers());
        }
        return new ArrayList<>(out);
    }
}
