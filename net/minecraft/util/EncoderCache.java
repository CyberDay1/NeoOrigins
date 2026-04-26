package net.minecraft.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.Tag;

public class EncoderCache {
    final LoadingCache<EncoderCache.Key<?, ?>, DataResult<?>> cache;

    public EncoderCache(int p_341936_) {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize((long)p_341936_)
            .concurrencyLevel(1)
            .softValues()
            .build(new CacheLoader<EncoderCache.Key<?, ?>, DataResult<?>>() {
                public DataResult<?> load(EncoderCache.Key<?, ?> p_341934_) {
                    return p_341934_.resolve();
                }
            });
    }

    public <A> Codec<A> wrap(final Codec<A> p_341888_) {
        return new Codec<A>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> p_341931_, T p_341946_) {
                return p_341888_.decode(p_341931_, p_341946_);
            }

            @Override
            public <T> DataResult<T> encode(A p_341885_, DynamicOps<T> p_341925_, T p_341904_) {
                return EncoderCache.this.cache
                    .getUnchecked(new EncoderCache.Key<>(p_341888_, p_341885_, p_341925_))
                    .map(p_342020_ -> (T)(p_342020_ instanceof Tag tag ? tag.copy() : p_342020_));
            }
        };
    }

    static record Key<A, T>(Codec<A> codec, A value, DynamicOps<T> ops) {
        public DataResult<T> resolve() {
            return this.codec.encodeStart(this.ops, this.value);
        }

        @Override
        public boolean equals(Object p_341937_) {
            if (this == p_341937_) {
                return true;
            } else {
                return !(p_341937_ instanceof EncoderCache.Key<?, ?> key)
                    ? false
                    : this.codec == key.codec && this.value.equals(key.value) && this.ops.equals(key.ops);
            }
        }

        @Override
        public int hashCode() {
            int i = System.identityHashCode(this.codec);
            i = 31 * i + this.value.hashCode();
            return 31 * i + this.ops.hashCode();
        }
    }
}
