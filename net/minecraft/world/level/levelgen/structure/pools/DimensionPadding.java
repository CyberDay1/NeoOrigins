package net.minecraft.world.level.levelgen.structure.pools;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.function.Function;
import net.minecraft.util.ExtraCodecs;

public record DimensionPadding(int bottom, int top) {
    private static final Codec<DimensionPadding> RECORD_CODEC = RecordCodecBuilder.create(
        p_348641_ -> p_348641_.group(
                    ExtraCodecs.NON_NEGATIVE_INT.lenientOptionalFieldOf("bottom", 0).forGetter(p_348628_ -> p_348628_.bottom),
                    ExtraCodecs.NON_NEGATIVE_INT.lenientOptionalFieldOf("top", 0).forGetter(p_348564_ -> p_348564_.top)
                )
                .apply(p_348641_, DimensionPadding::new)
    );
    public static final Codec<DimensionPadding> CODEC = Codec.either(ExtraCodecs.NON_NEGATIVE_INT, RECORD_CODEC)
        .xmap(
            p_348469_ -> p_348469_.map(DimensionPadding::new, Function.identity()),
            p_348678_ -> p_348678_.hasEqualTopAndBottom() ? Either.left(p_348678_.bottom) : Either.right(p_348678_)
        );
    public static final DimensionPadding ZERO = new DimensionPadding(0);

    public DimensionPadding(int p_348567_) {
        this(p_348567_, p_348567_);
    }

    public boolean hasEqualTopAndBottom() {
        return this.top == this.bottom;
    }
}
