package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Map.Entry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TypedDataComponent<T>(DataComponentType<T> type, T value) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>>() {
        public TypedDataComponent<?> decode(RegistryFriendlyByteBuf p_331219_) {
            DataComponentType<?> datacomponenttype = DataComponentType.STREAM_CODEC.decode(p_331219_);
            return decodeTyped(p_331219_, datacomponenttype);
        }

        private static <T> TypedDataComponent<T> decodeTyped(RegistryFriendlyByteBuf p_331307_, DataComponentType<T> p_330560_) {
            return new TypedDataComponent<>(p_330560_, p_330560_.streamCodec().decode(p_331307_));
        }

        public void encode(RegistryFriendlyByteBuf p_330591_, TypedDataComponent<?> p_331491_) {
            encodeCap(p_330591_, p_331491_);
        }

        private static <T> void encodeCap(RegistryFriendlyByteBuf p_331348_, TypedDataComponent<T> p_331234_) {
            DataComponentType.STREAM_CODEC.encode(p_331348_, p_331234_.type());
            p_331234_.type().streamCodec().encode(p_331348_, p_331234_.value());
        }
    };

    static TypedDataComponent<?> fromEntryUnchecked(Entry<DataComponentType<?>, Object> p_332125_) {
        return createUnchecked(p_332125_.getKey(), p_332125_.getValue());
    }

    public static <T> TypedDataComponent<T> createUnchecked(DataComponentType<T> p_332026_, Object p_331462_) {
        return new TypedDataComponent<>(p_332026_, (T)p_331462_);
    }

    public void applyTo(PatchedDataComponentMap p_332099_) {
        p_332099_.set(this.type, this.value);
    }

    public <D> DataResult<D> encodeValue(DynamicOps<D> p_331363_) {
        Codec<T> codec = this.type.codec();
        return codec == null ? DataResult.error(() -> "Component of type " + this.type + " is not encodable") : codec.encodeStart(p_331363_, this.value);
    }

    @Override
    public String toString() {
        return this.type + "=>" + this.value;
    }
}
