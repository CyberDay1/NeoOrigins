package net.minecraft.server.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Filterable<T>(T raw, Optional<T> filtered) {
    public static <T> Codec<Filterable<T>> codec(Codec<T> p_331745_) {
        Codec<Filterable<T>> codec = RecordCodecBuilder.create(
            p_337552_ -> p_337552_.group(
                        p_331745_.fieldOf("raw").forGetter(Filterable::raw), p_331745_.optionalFieldOf("filtered").forGetter(Filterable::filtered)
                    )
                    .apply(p_337552_, Filterable::new)
        );
        Codec<Filterable<T>> codec1 = p_331745_.xmap(Filterable::passThrough, Filterable::raw);
        return Codec.withAlternative(codec, codec1);
    }

    public static <B extends ByteBuf, T> StreamCodec<B, Filterable<T>> streamCodec(StreamCodec<B, T> p_330521_) {
        return StreamCodec.composite(p_330521_, Filterable::raw, p_330521_.apply(ByteBufCodecs::optional), Filterable::filtered, Filterable::new);
    }

    public static <T> Filterable<T> passThrough(T p_330890_) {
        return new Filterable<>(p_330890_, Optional.empty());
    }

    public static Filterable<String> from(FilteredText p_330414_) {
        return new Filterable<>(p_330414_.raw(), p_330414_.isFiltered() ? Optional.of(p_330414_.filteredOrEmpty()) : Optional.empty());
    }

    public T get(boolean p_331777_) {
        return p_331777_ ? this.filtered.orElse(this.raw) : this.raw;
    }

    public <U> Filterable<U> map(Function<T, U> p_331765_) {
        return new Filterable<>(p_331765_.apply(this.raw), this.filtered.map(p_331765_));
    }

    public <U> Optional<Filterable<U>> resolve(Function<T, Optional<U>> p_330635_) {
        Optional<U> optional = p_330635_.apply(this.raw);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else if (this.filtered.isPresent()) {
            Optional<U> optional1 = p_330635_.apply(this.filtered.get());
            return optional1.isEmpty() ? Optional.empty() : Optional.of(new Filterable<>(optional.get(), optional1));
        } else {
            return Optional.of(new Filterable<>(optional.get(), Optional.empty()));
        }
    }
}
