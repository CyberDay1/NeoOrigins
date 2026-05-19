package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Random;

/**
 * Inclusive {@code {min,max}} integer range. Shared by {@link SpawnRules}
 * (Y / light bounds) and {@link DropEntry} (drop count). A bare number in
 * JSON is also accepted and treated as {@code {n,n}}.
 */
public record IntRange(int min, int max) {

    private static final Codec<IntRange> OBJECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.fieldOf("min").forGetter(IntRange::min),
        Codec.INT.fieldOf("max").forGetter(IntRange::max)
    ).apply(inst, IntRange::new));

    /** {@code 5} or {@code {"min":1,"max":3}} both decode. */
    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, OBJECT_CODEC)
        .xmap(
            e -> e.map(n -> new IntRange(n, n), r -> r),
            r -> r.min() == r.max()
                ? com.mojang.datafixers.util.Either.left(r.min())
                : com.mojang.datafixers.util.Either.right(r));

    public IntRange {
        if (max < min) { int t = min; min = max; max = t; }
    }

    /** Uniformly roll an inclusive value in [min, max]. */
    public int sample(Random random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    public boolean contains(int v) {
        return v >= min && v <= max;
    }
}
