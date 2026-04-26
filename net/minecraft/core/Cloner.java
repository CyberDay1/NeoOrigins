package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;

public class Cloner<T> {
    private final Codec<T> directCodec;

    Cloner(Codec<T> p_311867_) {
        this.directCodec = p_311867_;
    }

    public T clone(T p_312894_, HolderLookup.Provider p_312425_, HolderLookup.Provider p_312105_) {
        DynamicOps<Object> dynamicops = p_312425_.createSerializationContext(JavaOps.INSTANCE);
        DynamicOps<Object> dynamicops1 = p_312105_.createSerializationContext(JavaOps.INSTANCE);
        Object object = this.directCodec
            .encodeStart(dynamicops, p_312894_)
            .getOrThrow(p_312200_ -> new IllegalStateException("Failed to encode: " + p_312200_));
        return this.directCodec.parse(dynamicops1, object).getOrThrow(p_312832_ -> new IllegalStateException("Failed to decode: " + p_312832_));
    }

    public static class Factory {
        private final Map<ResourceKey<? extends Registry<?>>, Cloner<?>> codecs = new HashMap<>();

        public <T> Cloner.Factory addCodec(ResourceKey<? extends Registry<? extends T>> p_311972_, Codec<T> p_312658_) {
            this.codecs.put(p_311972_, new Cloner<>(p_312658_));
            return this;
        }

        @Nullable
        public <T> Cloner<T> cloner(ResourceKey<? extends Registry<? extends T>> p_312530_) {
            return (Cloner<T>)this.codecs.get(p_312530_);
        }
    }
}
