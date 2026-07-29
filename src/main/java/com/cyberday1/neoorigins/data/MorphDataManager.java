package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code data/<ns>/neoorigins/morphs/<name>.json} into {@link MorphSpec}
 * records — reusable, named morph definitions that a
 * {@code neoorigins:entity_model} power can reference by id instead of
 * repeating the same entity type / variant NBT / scale in every origin.
 *
 * <p>Mirrors {@link EntityGroupDataManager}: a handful of {@linkplain #BUILTINS
 * built-in} definitions ship in code so common variants work with zero setup,
 * a datapack file of the same id OVERRIDES the built-in, and an unknown id
 * resolves to {@link MorphSpec#EMPTY} with a one-time WARN rather than
 * silently doing nothing.
 *
 * <p>Morph definitions span both server concerns and client concerns, so per
 * the standing "server is source of truth" rule this is a single server-side
 * registry whose resolved output is synced down to clients — not a split
 * across {@code data/} and {@code assets/}.
 */
public class MorphDataManager
        extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final MorphDataManager INSTANCE = new MorphDataManager();
    // data/<ns>/neoorigins/morphs/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("neoorigins/morphs");

    /**
     * Built-in morph definitions. These are the variants that used to require
     * per-type code in the render handler; a datapack file at the same id
     * replaces the entry here wholesale.
     */
    private static final Map<Identifier, MorphSpec> BUILTINS = Map.of(
        // The original v1 behaviour: the smallest vanilla slime, whose footprint
        // is the closest match to a player's.
        id("slime"), mob("minecraft:slime", sized("Size", 0)),
        id("magma_cube"), mob("minecraft:magma_cube", sized("Size", 0)),
        // Sheep/cat/villager are the variant-driven mobs authors ask for most;
        // each is a plain NBT variant with no bespoke code behind it.
        id("sheep"), mob("minecraft:sheep", null),
        id("cat"), mob("minecraft:cat", null),
        id("villager"), mob("minecraft:villager", null),
        id("creeper"), mob("minecraft:creeper", null),
        id("zombie"), mob("minecraft:zombie", null),
        id("skeleton"), mob("minecraft:skeleton", null),
        id("enderman"), mob("minecraft:enderman", null),
        id("spider"), mob("minecraft:spider", null)
    );

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, name);
    }

    private static MorphSpec mob(String entityType, CompoundTag nbt) {
        return new MorphSpec(
            Optional.ofNullable(Identifier.tryParse(entityType)),
            Optional.ofNullable(nbt),
            1.0f, true, true, true, MorphSpec.FIRST_PERSON_ITEM, Optional.empty(),
            Optional.empty(), true, Optional.empty());
    }

    private static CompoundTag sized(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, value);
        return tag;
    }

    /** Datapack-loaded definitions (override built-ins by id). */
    private Map<Identifier, MorphSpec> loaded = Map.of();

    /** One-time WARN de-dup for unresolvable morph ids. */
    private final Set<String> warnedUnknown = ConcurrentHashMap.newKeySet();

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier morphId = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(morphId, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading morph file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, MorphSpec> defs = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : object.entrySet()) {
            Identifier morphId = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                MorphSpec.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse morph {}: {}", morphId, err))
                    .ifPresent(def -> defs.put(morphId, def));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading morph {}", morphId, e);
            }
        }
        this.loaded = Map.copyOf(defs);
        this.warnedUnknown.clear();
        NeoOrigins.LOGGER.info("Loaded {} morph definitions (+{} built-in defaults)",
            defs.size(), BUILTINS.size());
    }

    /**
     * Resolve a morph id to its definition. A bare value (no namespace) resolves
     * to {@code neoorigins:<name>}. Datapack entries win over built-ins by id.
     * An unknown id returns {@link MorphSpec#EMPTY} and logs a one-time WARN.
     */
    public MorphSpec resolve(String morph) {
        if (morph == null || morph.isBlank()) return MorphSpec.EMPTY;
        Identifier morphId = morph.indexOf(':') >= 0
            ? Identifier.tryParse(morph)
            : Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, morph);
        if (morphId == null) {
            warnUnknown(morph);
            return MorphSpec.EMPTY;
        }
        MorphSpec def = loaded.get(morphId);      // datapack wins
        if (def != null) return def;
        def = BUILTINS.get(morphId);              // then built-in default
        if (def != null) return def;
        warnUnknown(morph);
        return MorphSpec.EMPTY;
    }

    private void warnUnknown(String morph) {
        if (warnedUnknown.add(morph)) {
            NeoOrigins.LOGGER.warn(
                "entity_model references unknown morph '{}' — no built-in default and no "
                + "data/<ns>/neoorigins/morphs/ file defines it; the reference will be ignored. "
                + "Built-in morphs: slime, magma_cube, sheep, cat, villager, creeper, zombie, "
                + "skeleton, enderman, spider.", morph);
        }
    }
}
