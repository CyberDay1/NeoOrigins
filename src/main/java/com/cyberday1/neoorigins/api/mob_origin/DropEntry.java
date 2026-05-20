package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * One per-mob-origin drop. The same record carries both strategies (chosen
 * at the {@link DropRules} level):
 *
 * <ul>
 *   <li>{@link DropRules.Strategy#INDEPENDENT_CHANCE} — {@link #rolls} independent
 *       rolls, each succeeding with {@link #chance} probability and yielding
 *       {@link #count} items. {@link #weight} is ignored.</li>
 *   <li>{@link DropRules.Strategy#WEIGHTED_POOL} — entries are weighted picks
 *       with relative weight {@link #weight}; the pool is rolled
 *       {@link DropRules#poolRolls()} times, and each pick yields {@link #count}
 *       items. {@link #chance} / {@link #rolls} are ignored.</li>
 * </ul>
 *
 * <p>Phase-5 TODO: an optional vanilla loot-condition (JSON pass-through) is
 * deliberately NOT modelled yet — adding a half-wired field now would be
 * speculative. It is a leaf addition that does not reshape {@link MobOrigin}.
 */
public record DropEntry(
    ResourceLocation item,
    IntRange count,
    double chance,
    int rolls,
    int weight
) {
    public static final Codec<DropEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.fieldOf("item").forGetter(DropEntry::item),
        IntRange.CODEC.optionalFieldOf("count", new IntRange(1, 1)).forGetter(DropEntry::count),
        Codec.DOUBLE.optionalFieldOf("chance", 1.0).forGetter(DropEntry::chance),
        Codec.INT.optionalFieldOf("rolls", 1).forGetter(DropEntry::rolls),
        Codec.INT.optionalFieldOf("weight", 1).forGetter(DropEntry::weight)
    ).apply(inst, DropEntry::new));
}
