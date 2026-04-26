package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;

public interface StringRepresentable {
    int PRE_BUILT_MAP_THRESHOLD = 16;

    String getSerializedName();

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnum(Supplier<E[]> p_216440_) {
        return fromEnumWithMapping(p_216440_, p_304817_ -> p_304817_);
    }

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnumWithMapping(
        Supplier<E[]> p_275615_, Function<String, String> p_275259_
    ) {
        E[] ae = (E[])p_275615_.get();
        Function<String, E> function = createNameLookup(ae, p_275259_);
        return new StringRepresentable.EnumCodec<>(ae, function);
    }

    static <T extends StringRepresentable> Codec<T> fromValues(Supplier<T[]> p_304543_) {
        T[] at = (T[])p_304543_.get();
        Function<String, T> function = createNameLookup(at, p_304333_ -> p_304333_);
        ToIntFunction<T> tointfunction = Util.createIndexLookup(Arrays.asList(at));
        return new StringRepresentable.StringRepresentableCodec<>(at, function, tointfunction);
    }

    static <T extends StringRepresentable> Function<String, T> createNameLookup(T[] p_304419_, Function<String, String> p_304658_) {
        if (p_304419_.length > 16) {
            Map<String, T> map = Arrays.<StringRepresentable>stream(p_304419_)
                .collect(Collectors.toMap(p_304335_ -> p_304658_.apply(p_304335_.getSerializedName()), p_304719_ -> (T)p_304719_));
            return p_304332_ -> p_304332_ == null ? null : map.get(p_304332_);
        } else {
            return p_304338_ -> {
                for (T t : p_304419_) {
                    if (p_304658_.apply(t.getSerializedName()).equals(p_304338_)) {
                        return t;
                    }
                }

                return null;
            };
        }
    }

    static Keyable keys(final StringRepresentable[] p_14358_) {
        return new Keyable() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> p_184758_) {
                return Arrays.stream(p_14358_).map(StringRepresentable::getSerializedName).map(p_184758_::createString);
            }
        };
    }

    @Deprecated
    public static class EnumCodec<E extends Enum<E> & StringRepresentable> extends StringRepresentable.StringRepresentableCodec<E> {
        private final Function<String, E> resolver;

        public EnumCodec(E[] p_216447_, Function<String, E> p_216448_) {
            super(p_216447_, p_216448_, p_216454_ -> p_216454_.ordinal());
            this.resolver = p_216448_;
        }

        @Nullable
        public E byName(@Nullable String p_216456_) {
            return this.resolver.apply(p_216456_);
        }

        public E byName(@Nullable String p_263077_, E p_263115_) {
            return Objects.requireNonNullElse(this.byName(p_263077_), p_263115_);
        }
    }

    public static class StringRepresentableCodec<S extends StringRepresentable> implements Codec<S> {
        private final Codec<S> codec;

        public StringRepresentableCodec(S[] p_304774_, Function<String, S> p_304834_, ToIntFunction<S> p_304925_) {
            this.codec = ExtraCodecs.orCompressed(
                Codec.stringResolver(StringRepresentable::getSerializedName, p_304834_),
                ExtraCodecs.idResolverCodec(p_304925_, p_304986_ -> p_304986_ >= 0 && p_304986_ < p_304774_.length ? p_304774_[p_304986_] : null, -1)
            );
        }

        @Override
        public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> p_304586_, T p_304692_) {
            return this.codec.decode(p_304586_, p_304692_);
        }

        public <T> DataResult<T> encode(S p_304936_, DynamicOps<T> p_304952_, T p_304437_) {
            return this.codec.encode(p_304936_, p_304952_, p_304437_);
        }
    }
}
