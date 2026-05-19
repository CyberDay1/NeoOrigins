package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * One per-mob-origin drop. Defined in Phase 1 for codec stability; compiled
 * into a generated global loot modifier in Phase 5.
 *
 * <p>Phase-5 TODO: an optional vanilla loot-condition (JSON pass-through) is
 * deliberately NOT modelled yet — adding a half-wired field now would be
 * speculative. It is a leaf addition that does not reshape {@link MobOrigin}.
 */
public record DropEntry(
    Identifier item,
    IntRange count,
    double chance,
    int rolls
) {
    public static final Codec<DropEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("item").forGetter(DropEntry::item),
        IntRange.CODEC.optionalFieldOf("count", new IntRange(1, 1)).forGetter(DropEntry::count),
        Codec.DOUBLE.optionalFieldOf("chance", 1.0).forGetter(DropEntry::chance),
        Codec.INT.optionalFieldOf("rolls", 1).forGetter(DropEntry::rolls)
    ).apply(inst, DropEntry::new));
}
