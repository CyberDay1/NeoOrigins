package net.minecraft.advancements.critereon;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.Predicate;

public record CollectionPredicate<T, P extends Predicate<T>>(
    Optional<CollectionContentsPredicate<T, P>> contains, Optional<CollectionCountsPredicate<T, P>> counts, Optional<MinMaxBounds.Ints> size
) implements Predicate<Iterable<T>> {
    public static <T, P extends Predicate<T>> Codec<CollectionPredicate<T, P>> codec(Codec<P> p_340851_) {
        return RecordCodecBuilder.create(
            p_341197_ -> p_341197_.group(
                        CollectionContentsPredicate.<T, P>codec(p_340851_).optionalFieldOf("contains").forGetter(CollectionPredicate::contains),
                        CollectionCountsPredicate.<T, P>codec(p_340851_).optionalFieldOf("count").forGetter(CollectionPredicate::counts),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("size").forGetter(CollectionPredicate::size)
                    )
                    .apply(p_341197_, CollectionPredicate::new)
        );
    }

    public boolean test(Iterable<T> p_341361_) {
        if (this.contains.isPresent() && !this.contains.get().test(p_341361_)) {
            return false;
        } else {
            return this.counts.isPresent() && !this.counts.get().test(p_341361_)
                ? false
                : !this.size.isPresent() || this.size.get().matches(Iterables.size(p_341361_));
        }
    }
}
