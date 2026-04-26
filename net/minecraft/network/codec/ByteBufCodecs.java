package net.minecraft.network.codec;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;
    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<ByteBuf, Boolean>() {
        public Boolean decode(ByteBuf p_320813_) {
            return p_320813_.readBoolean();
        }

        public void encode(ByteBuf p_319896_, Boolean p_320251_) {
            p_319896_.writeBoolean(p_320251_);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<ByteBuf, Byte>() {
        public Byte decode(ByteBuf p_320628_) {
            return p_320628_.readByte();
        }

        public void encode(ByteBuf p_320364_, Byte p_320618_) {
            p_320364_.writeByte(p_320618_);
        }
    };
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<ByteBuf, Short>() {
        public Short decode(ByteBuf p_320513_) {
            return p_320513_.readShort();
        }

        public void encode(ByteBuf p_320028_, Short p_320388_) {
            p_320028_.writeShort(p_320388_);
        }
    };
    StreamCodec<ByteBuf, Integer> UNSIGNED_SHORT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf p_320319_) {
            return p_320319_.readUnsignedShort();
        }

        public void encode(ByteBuf p_320669_, Integer p_320205_) {
            p_320669_.writeShort(p_320205_);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf p_320253_) {
            return p_320253_.readInt();
        }

        public void encode(ByteBuf p_320753_, Integer p_330380_) {
            p_320753_.writeInt(p_330380_);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf p_320759_) {
            return VarInt.read(p_320759_);
        }

        public void encode(ByteBuf p_320314_, Integer p_341414_) {
            VarInt.write(p_320314_, p_341414_);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<ByteBuf, Long>() {
        public Long decode(ByteBuf p_320635_) {
            return VarLong.read(p_320635_);
        }

        public void encode(ByteBuf p_320545_, Long p_341419_) {
            VarLong.write(p_320545_, p_341419_);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<ByteBuf, Float>() {
        public Float decode(ByteBuf p_320259_) {
            return p_320259_.readFloat();
        }

        public void encode(ByteBuf p_320199_, Float p_341020_) {
            p_320199_.writeFloat(p_341020_);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<ByteBuf, Double>() {
        public Double decode(ByteBuf p_320599_) {
            return p_320599_.readDouble();
        }

        public void encode(ByteBuf p_320880_, Double p_340812_) {
            p_320880_.writeDouble(p_340812_);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<ByteBuf, byte[]>() {
        public byte[] decode(ByteBuf p_320167_) {
            return FriendlyByteBuf.readByteArray(p_320167_);
        }

        public void encode(ByteBuf p_320240_, byte[] p_341316_) {
            FriendlyByteBuf.writeByteArray(p_320240_, p_341316_);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, Tag> TAG = tagCodec(() -> NbtAccounter.create(2097152L));
    StreamCodec<ByteBuf, Tag> TRUSTED_TAG = tagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = compoundTagCodec(() -> NbtAccounter.create(2097152L));
    StreamCodec<ByteBuf, CompoundTag> TRUSTED_COMPOUND_TAG = compoundTagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<ByteBuf, Optional<CompoundTag>>() {
        public Optional<CompoundTag> decode(ByteBuf p_320103_) {
            return Optional.ofNullable(FriendlyByteBuf.readNbt(p_320103_));
        }

        public void encode(ByteBuf p_320012_, Optional<CompoundTag> p_341059_) {
            FriendlyByteBuf.writeNbt(p_320012_, p_341059_.orElse(null));
        }
    };
    StreamCodec<ByteBuf, Vector3f> VECTOR3F = new StreamCodec<ByteBuf, Vector3f>() {
        public Vector3f decode(ByteBuf p_319897_) {
            return FriendlyByteBuf.readVector3f(p_319897_);
        }

        public void encode(ByteBuf p_320441_, Vector3f p_340932_) {
            FriendlyByteBuf.writeVector3f(p_320441_, p_340932_);
        }
    };
    StreamCodec<ByteBuf, Quaternionf> QUATERNIONF = new StreamCodec<ByteBuf, Quaternionf>() {
        public Quaternionf decode(ByteBuf p_324083_) {
            return FriendlyByteBuf.readQuaternion(p_324083_);
        }

        public void encode(ByteBuf p_324192_, Quaternionf p_341304_) {
            FriendlyByteBuf.writeQuaternion(p_324192_, p_341304_);
        }
    };
    StreamCodec<ByteBuf, PropertyMap> GAME_PROFILE_PROPERTIES = new StreamCodec<ByteBuf, PropertyMap>() {
        private static final int MAX_PROPERTY_NAME_LENGTH = 64;
        private static final int MAX_PROPERTY_VALUE_LENGTH = 32767;
        private static final int MAX_PROPERTY_SIGNATURE_LENGTH = 1024;
        private static final int MAX_PROPERTIES = 16;

        public PropertyMap decode(ByteBuf p_331129_) {
            int i = ByteBufCodecs.readCount(p_331129_, 16);
            PropertyMap propertymap = new PropertyMap();

            for (int j = 0; j < i; j++) {
                String s = Utf8String.read(p_331129_, 64);
                String s1 = Utf8String.read(p_331129_, 32767);
                String s2 = FriendlyByteBuf.readNullable(p_331129_, p_341239_ -> Utf8String.read(p_341239_, 1024));
                Property property = new Property(s, s1, s2);
                propertymap.put(property.name(), property);
            }

            return propertymap;
        }

        public void encode(ByteBuf p_331394_, PropertyMap p_341001_) {
            ByteBufCodecs.writeCount(p_331394_, p_341001_.size(), 16);

            for (Property property : p_341001_.values()) {
                Utf8String.write(p_331394_, property.name(), 64);
                Utf8String.write(p_331394_, property.value(), 32767);
                FriendlyByteBuf.writeNullable(p_331394_, property.signature(), (p_340917_, p_341030_) -> Utf8String.write(p_340917_, p_341030_, 1024));
            }
        }
    };
    StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = new StreamCodec<ByteBuf, GameProfile>() {
        public GameProfile decode(ByteBuf p_341302_) {
            UUID uuid = UUIDUtil.STREAM_CODEC.decode(p_341302_);
            String s = Utf8String.read(p_341302_, 16);
            GameProfile gameprofile = new GameProfile(uuid, s);
            gameprofile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(p_341302_));
            return gameprofile;
        }

        public void encode(ByteBuf p_340881_, GameProfile p_341071_) {
            UUIDUtil.STREAM_CODEC.encode(p_340881_, p_341071_.getId());
            Utf8String.write(p_340881_, p_341071_.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(p_340881_, p_341071_.getProperties());
        }
    };

    static StreamCodec<ByteBuf, byte[]> byteArray(final int p_324182_) {
        return new StreamCodec<ByteBuf, byte[]>() {
            public byte[] decode(ByteBuf p_319947_) {
                return FriendlyByteBuf.readByteArray(p_319947_, p_324182_);
            }

            public void encode(ByteBuf p_320370_, byte[] p_331189_) {
                if (p_331189_.length > p_324182_) {
                    throw new EncoderException("ByteArray with size " + p_331189_.length + " is bigger than allowed " + p_324182_);
                } else {
                    FriendlyByteBuf.writeByteArray(p_320370_, p_331189_);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, String> stringUtf8(final int p_320693_) {
        return new StreamCodec<ByteBuf, String>() {
            public String decode(ByteBuf p_332176_) {
                return Utf8String.read(p_332176_, p_320693_);
            }

            public void encode(ByteBuf p_331068_, String p_341104_) {
                Utf8String.write(p_331068_, p_341104_, p_320693_);
            }
        };
    }

    static StreamCodec<ByteBuf, Tag> tagCodec(final Supplier<NbtAccounter> p_320506_) {
        return new StreamCodec<ByteBuf, Tag>() {
            public Tag decode(ByteBuf p_341393_) {
                Tag tag = FriendlyByteBuf.readNbt(p_341393_, p_320506_.get());
                if (tag == null) {
                    throw new DecoderException("Expected non-null compound tag");
                } else {
                    return tag;
                }
            }

            public void encode(ByteBuf p_340857_, Tag p_341321_) {
                if (p_341321_ == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null compound tag");
                } else {
                    FriendlyByteBuf.writeNbt(p_340857_, p_341321_);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> p_331128_) {
        return tagCodec(p_331128_).map(p_339405_ -> {
            if (p_339405_ instanceof CompoundTag) {
                return (CompoundTag)p_339405_;
            } else {
                throw new DecoderException("Not a compound tag: " + p_339405_);
            }
        }, p_330975_ -> (Tag)p_330975_);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodecTrusted(Codec<T> p_331105_) {
        return fromCodec(p_331105_, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> p_320615_) {
        return fromCodec(p_320615_, () -> NbtAccounter.create(2097152L));
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> p_330943_, Supplier<NbtAccounter> p_330382_) {
        return tagCodec(p_330382_)
            .map(
                p_337514_ -> p_330943_.parse(NbtOps.INSTANCE, p_337514_)
                        .getOrThrow(p_339407_ -> new DecoderException("Failed to decode: " + p_339407_ + " " + p_337514_)),
                p_337516_ -> p_330943_.encodeStart(NbtOps.INSTANCE, (T)p_337516_)
                        .getOrThrow(p_339409_ -> new EncoderException("Failed to encode: " + p_339409_ + " " + p_337516_))
            );
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistriesTrusted(Codec<T> p_331713_) {
        return fromCodecWithRegistries(p_331713_, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> p_323797_) {
        return fromCodecWithRegistries(p_323797_, () -> NbtAccounter.create(2097152L));
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(final Codec<T> p_331571_, Supplier<NbtAccounter> p_331922_) {
        final StreamCodec<ByteBuf, Tag> streamcodec = tagCodec(p_331922_);
        return new StreamCodec<RegistryFriendlyByteBuf, T>() {
            public T decode(RegistryFriendlyByteBuf p_340878_) {
                Tag tag = streamcodec.decode(p_340878_);
                RegistryOps<Tag> registryops = p_340878_.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                return p_331571_.parse(registryops, tag).getOrThrow(p_340924_ -> new DecoderException("Failed to decode: " + p_340924_ + " " + tag));
            }

            public void encode(RegistryFriendlyByteBuf p_341221_, T p_341320_) {
                RegistryOps<Tag> registryops = p_341221_.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                Tag tag = p_331571_.encodeStart(registryops, p_341320_)
                    .getOrThrow(p_341126_ -> new EncoderException("Failed to encode: " + p_341126_ + " " + p_341320_));
                streamcodec.encode(p_341221_, tag);
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<B, V> p_320522_) {
        return new StreamCodec<B, Optional<V>>() {
            public Optional<V> decode(B p_324595_) {
                return p_324595_.readBoolean() ? Optional.of(p_320522_.decode(p_324595_)) : Optional.empty();
            }

            public void encode(B p_324147_, Optional<V> p_340875_) {
                if (p_340875_.isPresent()) {
                    p_324147_.writeBoolean(true);
                    p_320522_.encode(p_324147_, p_340875_.get());
                } else {
                    p_324147_.writeBoolean(false);
                }
            }
        };
    }

    static int readCount(ByteBuf p_331813_, int p_331668_) {
        int i = VarInt.read(p_331813_);
        if (i > p_331668_) {
            throw new DecoderException(i + " elements exceeded max size of: " + p_331668_);
        } else {
            return i;
        }
    }

    static void writeCount(ByteBuf p_330907_, int p_330535_, int p_331447_) {
        if (p_330535_ > p_331447_) {
            throw new EncoderException(p_330535_ + " elements exceeded max size of: " + p_331447_);
        } else {
            VarInt.write(p_330907_, p_330535_);
        }
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> p_320579_, StreamCodec<? super B, V> p_319970_) {
        return collection(p_320579_, p_319970_, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
        final IntFunction<C> p_332198_, final StreamCodec<? super B, V> p_332183_, final int p_332173_
    ) {
        return new StreamCodec<B, C>() {
            public C decode(B p_324220_) {
                int i = ByteBufCodecs.readCount(p_324220_, p_332173_);
                C c = p_332198_.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    c.add(p_332183_.decode(p_324220_));
                }

                return c;
            }

            public void encode(B p_323874_, C p_340813_) {
                ByteBufCodecs.writeCount(p_323874_, p_340813_.size(), p_332173_);

                for (V v : p_340813_) {
                    p_332183_.encode(p_323874_, v);
                }
            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> p_319808_) {
        return p_319785_ -> collection(p_319808_, p_319785_);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return p_320272_ -> collection(ArrayList::new, p_320272_);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int p_330434_) {
        return p_329871_ -> collection(ArrayList::new, p_329871_, p_330434_);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        IntFunction<? extends M> p_320265_, StreamCodec<? super B, K> p_320113_, StreamCodec<? super B, V> p_320275_
    ) {
        return map(p_320265_, p_320113_, p_320275_, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        final IntFunction<? extends M> p_331325_, final StreamCodec<? super B, K> p_331975_, final StreamCodec<? super B, V> p_331254_, final int p_330938_
    ) {
        return new StreamCodec<B, M>() {
            public void encode(B p_331539_, M p_341314_) {
                ByteBufCodecs.writeCount(p_331539_, p_341314_.size(), p_330938_);
                p_341314_.forEach((p_340647_, p_340648_) -> {
                    p_331975_.encode(p_331539_, (K)p_340647_);
                    p_331254_.encode(p_331539_, (V)p_340648_);
                });
            }

            public M decode(B p_331901_) {
                int i = ByteBufCodecs.readCount(p_331901_, p_330938_);
                M m = (M)p_331325_.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    K k = p_331975_.decode(p_331901_);
                    V v = p_331254_.decode(p_331901_);
                    m.put(k, v);
                }

                return m;
            }
        };
    }

    static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(final StreamCodec<? super B, L> p_331983_, final StreamCodec<? super B, R> p_332156_) {
        return new StreamCodec<B, Either<L, R>>() {
            public Either<L, R> decode(B p_332082_) {
                return p_332082_.readBoolean() ? Either.left(p_331983_.decode(p_332082_)) : Either.right(p_332156_.decode(p_332082_));
            }

            public void encode(B p_331172_, Either<L, R> p_340944_) {
                p_340944_.ifLeft(p_341317_ -> {
                    p_331172_.writeBoolean(true);
                    p_331983_.encode(p_331172_, (L)p_341317_);
                }).ifRight(p_341155_ -> {
                    p_331172_.writeBoolean(false);
                    p_332156_.encode(p_331172_, (R)p_341155_);
                });
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(final IntFunction<T> p_320877_, final ToIntFunction<T> p_319985_) {
        return new StreamCodec<ByteBuf, T>() {
            public T decode(ByteBuf p_340809_) {
                int i = VarInt.read(p_340809_);
                return p_320877_.apply(i);
            }

            public void encode(ByteBuf p_341417_, T p_330257_) {
                int i = p_319985_.applyAsInt(p_330257_);
                VarInt.write(p_341417_, i);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> p_319822_) {
        return idMapper(p_319822_::byIdOrThrow, p_319822_::getIdOrThrow);
    }

    private static <T, R> StreamCodec<RegistryFriendlyByteBuf, R> registry(
        final ResourceKey<? extends Registry<T>> p_319942_, final Function<Registry<T>, IdMap<R>> p_320353_
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, R>() {
            private IdMap<R> getRegistryOrThrow(RegistryFriendlyByteBuf p_330361_) {
                var registry = p_330361_.registryAccess().registryOrThrow(p_319942_);
                if (net.neoforged.neoforge.registries.RegistryManager.isNonSyncedBuiltInRegistry(registry)) {
                    throw new IllegalStateException("Cannot use ID syncing for non-synced built-in registry: " + registry.key());
                }
                return p_320353_.apply(registry);
            }

            public R decode(RegistryFriendlyByteBuf p_331253_) {
                int i = VarInt.read(p_331253_);
                return (R)this.getRegistryOrThrow(p_331253_).byIdOrThrow(i);
            }

            public void encode(RegistryFriendlyByteBuf p_331775_, R p_341178_) {
                int i = this.getRegistryOrThrow(p_331775_).getIdOrThrow(p_341178_);
                VarInt.write(p_331775_, i);
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> p_320404_) {
        return registry(p_320404_, p_332056_ -> p_332056_);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> p_320387_) {
        return registry(p_320387_, Registry::asHolderIdMap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holder(
        final ResourceKey<? extends Registry<T>> p_320391_, final StreamCodec<? super RegistryFriendlyByteBuf, T> p_320595_
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, Holder<T>>() {
            private static final int DIRECT_HOLDER_ID = 0;

            private IdMap<Holder<T>> getRegistryOrThrow(RegistryFriendlyByteBuf p_341377_) {
                return p_341377_.registryAccess().registryOrThrow(p_320391_).asHolderIdMap();
            }

            public Holder<T> decode(RegistryFriendlyByteBuf p_330998_) {
                int i = VarInt.read(p_330998_);
                return i == 0 ? Holder.direct(p_320595_.decode(p_330998_)) : (Holder)this.getRegistryOrThrow(p_330998_).byIdOrThrow(i - 1);
            }

            public void encode(RegistryFriendlyByteBuf p_330557_, Holder<T> p_341109_) {
                switch (p_341109_.kind()) {
                    case REFERENCE:
                        int i = this.getRegistryOrThrow(p_330557_).getIdOrThrow(p_341109_);
                        VarInt.write(p_330557_, i + 1);
                        break;
                    case DIRECT:
                        VarInt.write(p_330557_, 0);
                        p_320595_.encode(p_330557_, p_341109_.value());
                }
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>> holderSet(final ResourceKey<? extends Registry<T>> p_332137_) {
        return new StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>>() {
            private static final int NAMED_SET = -1;
            private final StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderCodec = ByteBufCodecs.holderRegistry(p_332137_);

            private final Map<net.neoforged.neoforge.registries.holdersets.HolderSetType, StreamCodec<RegistryFriendlyByteBuf, ? extends net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T>>> holderSetCodecs = new java.util.concurrent.ConcurrentHashMap<>();

            private StreamCodec<RegistryFriendlyByteBuf, ? extends net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T>> holderSetCodec(net.neoforged.neoforge.registries.holdersets.HolderSetType type) {
                return this.holderSetCodecs.computeIfAbsent(type, key -> key.makeStreamCodec(p_332137_));
            }

            private <H extends net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T>> H cast(net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T> holderSet) {
                return (H) holderSet;
            }

            public HolderSet<T> decode(RegistryFriendlyByteBuf p_340887_) {
                int i = VarInt.read(p_340887_) - 1;
                // Neo: Co-opt negative VarInt values within the HolderSet codec as an HolderSetType id.
                // Vanilla uses 0 for tag and [1, Integer.MAX_VALUE] for list size [0, Integer.MAX_VALUE - 1].
                // So we may encode the registry id for custom holder set types in [Integer.MIN_VALUE + 1, -1] (local variable i must not be underflow).
                // The registry id for custom holder set types is (-1 - network id), while local variable i is (network id - 1), so the registry id would be (-2 - i).
                if (i < -1) {
                    return this.holderSetCodec(net.neoforged.neoforge.registries.NeoForgeRegistries.HOLDER_SET_TYPES.byIdOrThrow(-2 - i)).decode(p_340887_);
                }
                if (i == -1) {
                    Registry<T> registry = p_340887_.registryAccess().registryOrThrow(p_332137_);
                    return registry.getTag(TagKey.create(p_332137_, ResourceLocation.STREAM_CODEC.decode(p_340887_))).orElseThrow();
                } else {
                    List<Holder<T>> list = new ArrayList<>(Math.min(i, 65536));

                    for (int j = 0; j < i; j++) {
                        list.add(this.holderCodec.decode(p_340887_));
                    }

                    return HolderSet.direct(list);
                }
            }

            public void encode(RegistryFriendlyByteBuf p_341009_, HolderSet<T> p_340834_) {
                // Neo: Co-opt negative VarInt values within the HolderSet codec as an HolderSetType id.
                // Vanilla uses 0 for tag and [1, Integer.MAX_VALUE] for list size [0, Integer.MAX_VALUE - 1] (local variable i in decode() must not be underflow).
                // So we may encode the registry id for custom holder set types in [Integer.MIN_VALUE + 1, -1].
                // The network id for custom holder set types is (-1 - registry id)
                if (p_341009_.getConnectionType().isNeoForge() && p_340834_ instanceof net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T> customHolderSet) {
                    VarInt.write(p_341009_, -1 - net.neoforged.neoforge.registries.NeoForgeRegistries.HOLDER_SET_TYPES.getId(customHolderSet.type()));
                    this.holderSetCodec(customHolderSet.type()).encode(p_341009_, cast(customHolderSet));
                    return;
                }
                Optional<TagKey<T>> optional = p_340834_.unwrapKey();
                if (optional.isPresent()) {
                    VarInt.write(p_341009_, 0);
                    ResourceLocation.STREAM_CODEC.encode(p_341009_, optional.get().location());
                } else {
                    VarInt.write(p_341009_, p_340834_.size() + 1);

                    for (Holder<T> holder : p_340834_) {
                        this.holderCodec.encode(p_341009_, holder);
                    }
                }
            }
        };
    }
}
