package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public interface DataComponentType<T> {
    Codec<DataComponentType<?>> CODEC = Codec.lazyInitialized(() -> BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec());
    StreamCodec<RegistryFriendlyByteBuf, DataComponentType<?>> STREAM_CODEC = StreamCodec.recursive(
        p_330812_ -> ByteBufCodecs.registry(Registries.DATA_COMPONENT_TYPE)
    );
    Codec<DataComponentType<?>> PERSISTENT_CODEC = CODEC.validate(
        p_337456_ -> p_337456_.isTransient()
                ? DataResult.error(() -> "Encountered transient component " + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(p_337456_))
                : DataResult.success(p_337456_)
    );
    Codec<Map<DataComponentType<?>, Object>> VALUE_MAP_CODEC = Codec.dispatchedMap(PERSISTENT_CODEC, DataComponentType::codecOrThrow);

    static <T> DataComponentType.Builder<T> builder() {
        return new DataComponentType.Builder<>();
    }

    @Nullable
    Codec<T> codec();

    default Codec<T> codecOrThrow() {
        Codec<T> codec = this.codec();
        if (codec == null) {
            throw new IllegalStateException(this + " is not a persistent component");
        } else {
            return codec;
        }
    }

    default boolean isTransient() {
        return this.codec() == null;
    }

    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec();

    public static class Builder<T> {
        @Nullable
        private Codec<T> codec;
        @Nullable
        private StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;
        private boolean cacheEncoding;

        public DataComponentType.Builder<T> persistent(Codec<T> p_331936_) {
            this.codec = p_331936_;
            return this;
        }

        public DataComponentType.Builder<T> networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T> p_331364_) {
            this.streamCodec = p_331364_;
            return this;
        }

        public DataComponentType.Builder<T> cacheEncoding() {
            this.cacheEncoding = true;
            return this;
        }

        public DataComponentType<T> build() {
            StreamCodec<? super RegistryFriendlyByteBuf, T> streamcodec = Objects.requireNonNullElseGet(
                this.streamCodec, () -> ByteBufCodecs.fromCodecWithRegistries(Objects.requireNonNull(this.codec, "Missing Codec for component"))
            );
            Codec<T> codec = this.cacheEncoding && this.codec != null ? DataComponents.ENCODER_CACHE.wrap(this.codec) : this.codec;
            return new DataComponentType.Builder.SimpleType<>(codec, streamcodec);
        }

        static class SimpleType<T> implements DataComponentType<T> {
            @Nullable
            private final Codec<T> codec;
            private final StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;

            SimpleType(@Nullable Codec<T> p_331492_, StreamCodec<? super RegistryFriendlyByteBuf, T> p_330863_) {
                this.codec = p_331492_;
                this.streamCodec = p_330863_;
            }

            @Nullable
            @Override
            public Codec<T> codec() {
                return this.codec;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return this.streamCodec;
            }

            @Override
            public String toString() {
                return Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, this);
            }
        }
    }
}
