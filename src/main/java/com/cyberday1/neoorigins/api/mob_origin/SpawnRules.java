package com.cyberday1.neoorigins.api.mob_origin;

import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.MobSpawnType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Weighted, condition-gated rules deciding whether a {@link MobOrigin} rolls
 * onto a matching entity at spawn. Defined in Phase 1 (so the
 * {@link MobOrigin} codec is stable); actually evaluated in Phase 2.
 *
 * <p>{@code location} reuses the existing {@link LocationCondition} codec
 * verbatim (dim / biome / biome_tag / structure / structure_tag) — the same
 * condition already battle-tested on player {@code spawn_location}.
 */
public record SpawnRules(
    double weight,
    Optional<LocationCondition> location,
    Optional<IntRange> yRange,
    Optional<IntRange> lightRange,
    TimeOfDay timeOfDay,
    Set<MobSpawnType> spawnReasons,
    Optional<String> mutexGroup,
    boolean replace
) {
    /** {@code minecraft:MobSpawnType} is a plain enum; code it by lowercase name. */
    private static final Codec<MobSpawnType> SPAWN_TYPE = Codec.STRING.xmap(
        s -> MobSpawnType.valueOf(s.toUpperCase(Locale.ROOT)),
        t -> t.name().toLowerCase(Locale.ROOT));

    public static final SpawnRules NEVER =
        new SpawnRules(0.0, Optional.empty(), Optional.empty(), Optional.empty(),
            TimeOfDay.ANY, Set.of(), Optional.empty(), false);

    public static final Codec<SpawnRules> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.DOUBLE.optionalFieldOf("weight", 0.0).forGetter(SpawnRules::weight),
        LocationCondition.CODEC.optionalFieldOf("location").forGetter(SpawnRules::location),
        IntRange.CODEC.optionalFieldOf("y_range").forGetter(SpawnRules::yRange),
        IntRange.CODEC.optionalFieldOf("light_range").forGetter(SpawnRules::lightRange),
        TimeOfDay.CODEC.optionalFieldOf("time_of_day", TimeOfDay.ANY).forGetter(SpawnRules::timeOfDay),
        SPAWN_TYPE.listOf().optionalFieldOf("spawn_reasons", List.of())
            .xmap(Set::copyOf, List::copyOf).forGetter(SpawnRules::spawnReasons),
        Codec.STRING.optionalFieldOf("mutex_group").forGetter(SpawnRules::mutexGroup),
        Codec.BOOL.optionalFieldOf("replace", false).forGetter(SpawnRules::replace)
    ).apply(inst, SpawnRules::new));

    /** Empty {@code spawn_reasons} means "any reason". */
    public boolean allowsReason(MobSpawnType reason) {
        return spawnReasons.isEmpty() || spawnReasons.contains(reason);
    }
}
