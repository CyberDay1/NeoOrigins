package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.event.CombatPowerEvents;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code data/<ns>/neoorigins/entity_groups/<name>.json} into
 * {@link GroupDef} records — the data-backing behind {@code neoorigins:entity_group}.
 *
 * <p>Historically the power classified a player as one of three hardcoded groups
 * ({@code undead}/{@code arthropod}/{@code water}); any other {@code group} value
 * was schema-valid but silently did nothing at runtime. This manager makes the
 * feature data-driven: authors mint their own pseudo entity-groups (a JSON file
 * with mechanical fields) and origins opt in by id. A player can't be added to
 * vanilla's real EntityType tags, so group membership is simulated by intercepting
 * the relevant game hooks (effect immunity, instant-heal inversion, enchant
 * vulnerability, targeting).
 *
 * <p>Hybrid model: the six {@linkplain #BUILTINS built-in} groups are registered
 * in code so existing packs keep working with zero config, and a datapack file of
 * the same id OVERRIDES the built-in (datapack wins). Unknown ids resolve to an
 * empty def and log a one-time WARN — the fix for the old "silent no-op".
 *
 * <p>Registered in {@code NeoOrigins.onAddReloadListeners}. Resolution is
 * server-reload-timing tolerant: {@link #resolve} on a not-yet-loaded manager
 * returns the built-in for a known id, or an empty def (never null).
 */
public class EntityGroupDataManager
        extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    public static final EntityGroupDataManager INSTANCE = new EntityGroupDataManager();
    // data/<ns>/neoorigins/entity_groups/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("neoorigins/entity_groups");

    /**
     * A resolved pseudo entity-group. All fields default empty/false; the power
     * simply reads the query methods, so an unknown group is behaviour-neutral.
     *
     * @param immuneEffects        effect ids that cannot apply to the player
     * @param invertInstantEffects instant_health &lt;-&gt; instant_damage swap (undead behaviour)
     * @param vulnerableEnchants   enchant ids; each level on the attacker's weapon adds bonus damage
     * @param ignoredBy            entity ids and/or {@code #tags} whose mobs won't target the player
     * @param targetedBy           entity ids and/or {@code #tags} whose mobs proactively hunt the player
     * @param fearedBy             entity ids and/or {@code #tags} whose mobs flee the player
     * @param burnsInSunlight      if true, the player catches fire in daylight like a vanilla skeleton/zombie
     */
    public record GroupDef(
            List<String> immuneEffects,
            boolean invertInstantEffects,
            List<String> vulnerableEnchants,
            List<String> ignoredBy,
            List<String> targetedBy,
            List<String> fearedBy,
            boolean burnsInSunlight) {

        public static final GroupDef EMPTY =
            new GroupDef(List.of(), false, List.of(), List.of(), List.of(), List.of(), false);

        public static final Codec<GroupDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("immune_effects", List.of()).forGetter(GroupDef::immuneEffects),
            Codec.BOOL.optionalFieldOf("invert_instant_effects", false).forGetter(GroupDef::invertInstantEffects),
            Codec.STRING.listOf().optionalFieldOf("vulnerable_enchants", List.of()).forGetter(GroupDef::vulnerableEnchants),
            Codec.STRING.listOf().optionalFieldOf("ignored_by", List.of()).forGetter(GroupDef::ignoredBy),
            Codec.STRING.listOf().optionalFieldOf("targeted_by", List.of()).forGetter(GroupDef::targetedBy),
            Codec.STRING.listOf().optionalFieldOf("feared_by", List.of()).forGetter(GroupDef::fearedBy),
            Codec.BOOL.optionalFieldOf("burns_in_sunlight", false).forGetter(GroupDef::burnsInSunlight)
        ).apply(inst, GroupDef::new));

        /** True if {@code effectId} (e.g. {@code minecraft:poison}) can't apply to the player. */
        public boolean immuneTo(String effectId) { return immuneEffects.contains(effectId); }

        /** True if this group inverts instant heal/harm (undead behaviour). */
        public boolean invertsInstant() { return invertInstantEffects; }

        /** True if this group takes bonus damage from {@code enchantId} (e.g. {@code minecraft:smite}). */
        public boolean vulnerableTo(String enchantId) { return vulnerableEnchants.contains(enchantId); }

        /**
         * True if {@code mob} matches any {@code ignored_by} entry (raw entity id
         * or {@code #entitytype tag}) and therefore should not target the player.
         */
        public boolean ignoredBy(LivingEntity mob) {
            for (String idOrTag : ignoredBy) {
                if (CombatPowerEvents.matchesEntityIdOrTag(mob, idOrTag)) return true;
            }
            return false;
        }

        /**
         * True if {@code mob} matches any {@code targeted_by} entry (raw entity id
         * or {@code #entitytype tag}) and should therefore proactively hunt the
         * player.
         *
         * <p>Carve-out: a <em>player-built</em> iron golem never hunts via this
         * path even when {@code minecraft:iron_golem} is listed — only
         * village-spawned golems ({@link net.minecraft.world.entity.animal.IronGolem#isPlayerCreated()}
         * {@code == false}) do. This keeps a player's own golems loyal while the
         * built-in {@code illager} group still draws village defenders.
         */
        public boolean targetedBy(LivingEntity mob) {
            for (String idOrTag : targetedBy) {
                if (CombatPowerEvents.matchesEntityIdOrTag(mob, idOrTag)) {
                    if (mob instanceof net.minecraft.world.entity.animal.IronGolem golem
                            && golem.isPlayerCreated()) {
                        return false;
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * True if {@code mob} matches any {@code feared_by} entry (raw entity id
         * or {@code #entitytype tag}) and should therefore flee the player.
         */
        public boolean fearedBy(LivingEntity mob) {
            for (String idOrTag : fearedBy) {
                if (CombatPowerEvents.matchesEntityIdOrTag(mob, idOrTag)) return true;
            }
            return false;
        }
    }

    /**
     * Built-in group defaults — reproduce the exact pre-data-driven behaviour.
     * A datapack file at the same id overrides the entry here.
     */
    private static final Map<ResourceLocation, GroupDef> BUILTINS = Map.of(
        id("undead"), new GroupDef(
            List.of("minecraft:poison", "minecraft:regeneration"), true,
            List.of("minecraft:smite"), List.of(), List.of(), List.of(), false),
        id("arthropod"), new GroupDef(
            List.of(), false,
            List.of("minecraft:bane_of_arthropods"), List.of(), List.of(), List.of(), false),
        id("water"), new GroupDef(
            List.of(), false,
            List.of("minecraft:impaling"), List.of(), List.of(), List.of(), false),
        // illager: raiders ignore you; village iron golems hunt you (player-built
        // golems stay loyal, see GroupDef#targetedBy); villagers & wandering
        // traders flee you.
        id("illager"), new GroupDef(
            List.of(), false,
            List.of(), List.of("#minecraft:raiders"),
            List.of("minecraft:iron_golem"),
            List.of("minecraft:villager", "minecraft:wandering_trader"), false),
        // piglin: piglins and brutes treat you as kin and never aggro. Unlike
        // vanilla this is unconditional — no gold-armor requirement.
        id("piglin"), new GroupDef(
            List.of(), false,
            List.of(), List.of("minecraft:piglin", "minecraft:piglin_brute"),
            List.of(), List.of(), false),
        // skeleton: the undead kit (poison/regen immunity, instant-effect
        // inversion, Smite vulnerability), wolves hunt you the way a pack hunts a
        // vanilla skeleton, and you burn in daylight (burns_in_sunlight).
        id("skeleton"), new GroupDef(
            List.of("minecraft:poison", "minecraft:regeneration"), true,
            List.of("minecraft:smite"), List.of(),
            List.of("minecraft:wolf"), List.of(), true)
    );

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, name);
    }

    /** Datapack-loaded defs (override built-ins by id). */
    private Map<ResourceLocation, GroupDef> loaded = Map.of();
    private int version = 0;
    public int version() { return version; }

    /** One-time WARN de-dup for unresolvable group ids. */
    private final Set<String> warnedUnknown = ConcurrentHashMap.newKeySet();

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation groupId = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(groupId, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error reading entity group file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, GroupDef> defs = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation groupId = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject json = entry.getValue().getAsJsonObject();
                GroupDef.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.error("Failed to parse entity group {}: {}", groupId, err))
                    .ifPresent(def -> defs.put(groupId, def));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading entity group {}", groupId, e);
            }
        }
        this.loaded = Map.copyOf(defs);
        this.warnedUnknown.clear();
        this.version++;
        NeoOrigins.LOGGER.info("Loaded {} entity group definitions (+{} built-in defaults)",
            defs.size(), BUILTINS.size());
    }

    /**
     * Resolve a group id string to its {@link GroupDef}. A bare value (no
     * namespace) resolves to {@code neoorigins:<name>} for backward compat.
     * Datapack entries win over built-ins by id. An unknown/unresolvable id
     * returns {@link GroupDef#EMPTY} and logs a one-time WARN.
     *
     * <p>The legacy {@code "undefined"} sentinel resolves to EMPTY silently —
     * it was the old "no classification" default and is not a real group.
     */
    public GroupDef resolve(String group) {
        if (group == null || group.isBlank() || "undefined".equalsIgnoreCase(group)) {
            return GroupDef.EMPTY;
        }
        ResourceLocation groupId = group.indexOf(':') >= 0
            ? ResourceLocation.tryParse(group)
            : ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, group);
        if (groupId == null) {
            warnUnknown(group);
            return GroupDef.EMPTY;
        }
        GroupDef def = loaded.get(groupId);          // datapack wins
        if (def != null) return def;
        def = BUILTINS.get(groupId);                 // then built-in default
        if (def != null) return def;
        warnUnknown(group);
        return GroupDef.EMPTY;
    }

    private void warnUnknown(String group) {
        if (warnedUnknown.add(group)) {
            NeoOrigins.LOGGER.warn(
                "entity_group references unknown group '{}' — no built-in default and no "
                + "data/<ns>/neoorigins/entity_groups/ file defines it; the power will do nothing. "
                + "Built-in groups: undead, arthropod, water, illager, piglin, skeleton.", group);
        }
    }
}
