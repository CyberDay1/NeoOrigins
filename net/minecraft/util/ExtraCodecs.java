package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.primitives.UnsignedBytes;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.Codec.ResultFunction;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ExtraCodecs {
    public static final Codec<JsonElement> JSON = converter(JsonOps.INSTANCE);
    public static final Codec<Object> JAVA = converter(JavaOps.INSTANCE);
    public static final Codec<Vector3f> VECTOR3F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            p_337581_ -> Util.fixedSize((List<Float>)p_337581_, 3).map(p_253489_ -> new Vector3f(p_253489_.get(0), p_253489_.get(1), p_253489_.get(2))),
            p_269787_ -> List.of(p_269787_.x(), p_269787_.y(), p_269787_.z())
        );
    public static final Codec<Vector4f> VECTOR4F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            p_337575_ -> Util.fixedSize((List<Float>)p_337575_, 4)
                    .map(p_340675_ -> new Vector4f(p_340675_.get(0), p_340675_.get(1), p_340675_.get(2), p_340675_.get(3))),
            p_340674_ -> List.of(p_340674_.x(), p_340674_.y(), p_340674_.z(), p_340674_.w())
        );
    public static final Codec<Quaternionf> QUATERNIONF_COMPONENTS = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            p_340677_ -> Util.fixedSize((List<Float>)p_340677_, 4)
                    .map(p_351780_ -> new Quaternionf(p_351780_.get(0), p_351780_.get(1), p_351780_.get(2), p_351780_.get(3)).normalize()),
            p_269780_ -> List.of(p_269780_.x, p_269780_.y, p_269780_.z, p_269780_.w)
        );
    public static final Codec<AxisAngle4f> AXISANGLE4F = RecordCodecBuilder.create(
        p_269774_ -> p_269774_.group(
                    Codec.FLOAT.fieldOf("angle").forGetter(p_269776_ -> p_269776_.angle),
                    VECTOR3F.fieldOf("axis").forGetter(p_269778_ -> new Vector3f(p_269778_.x, p_269778_.y, p_269778_.z))
                )
                .apply(p_269774_, AxisAngle4f::new)
    );
    public static final Codec<Quaternionf> QUATERNIONF = Codec.withAlternative(QUATERNIONF_COMPONENTS, AXISANGLE4F.xmap(Quaternionf::new, AxisAngle4f::new));
    public static Codec<Matrix4f> MATRIX4F = Codec.FLOAT.listOf().comapFlatMap(p_337582_ -> Util.fixedSize((List<Float>)p_337582_, 16).map(p_269777_ -> {
            Matrix4f matrix4f = new Matrix4f();

            for (int i = 0; i < p_269777_.size(); i++) {
                matrix4f.setRowColumn(i >> 2, i & 3, p_269777_.get(i));
            }

            return matrix4f.determineProperties();
        }), p_269775_ -> {
        FloatList floatlist = new FloatArrayList(16);

        for (int i = 0; i < 16; i++) {
            floatlist.add(p_269775_.getRowColumn(i >> 2, i & 3));
        }

        return floatlist;
    });
    public static final Codec<Integer> ARGB_COLOR_CODEC = Codec.withAlternative(
        Codec.INT, VECTOR4F, p_340676_ -> FastColor.ARGB32.colorFromFloat(p_340676_.w(), p_340676_.x(), p_340676_.y(), p_340676_.z())
    );
    public static final Codec<Integer> UNSIGNED_BYTE = Codec.BYTE
        .flatComapMap(
            UnsignedBytes::toInt,
            p_324632_ -> p_324632_ > 255
                    ? DataResult.error(() -> "Unsigned byte was too large: " + p_324632_ + " > 255")
                    : DataResult.success(p_324632_.byteValue())
        );
    public static final Codec<Integer> NON_NEGATIVE_INT = intRangeWithMessage(0, Integer.MAX_VALUE, p_275703_ -> "Value must be non-negative: " + p_275703_);
    public static final Codec<Integer> POSITIVE_INT = intRangeWithMessage(1, Integer.MAX_VALUE, p_274847_ -> "Value must be positive: " + p_274847_);
    public static final Codec<Float> POSITIVE_FLOAT = floatRangeMinExclusiveWithMessage(
        0.0F, Float.MAX_VALUE, p_339597_ -> "Value must be positive: " + p_339597_
    );
    public static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap(p_274857_ -> {
        try {
            return DataResult.success(Pattern.compile(p_274857_));
        } catch (PatternSyntaxException patternsyntaxexception) {
            return DataResult.error(() -> "Invalid regex pattern '" + p_274857_ + "': " + patternsyntaxexception.getMessage());
        }
    }, Pattern::pattern);
    public static final Codec<Instant> INSTANT_ISO8601 = temporalCodec(DateTimeFormatter.ISO_INSTANT).xmap(Instant::from, Function.identity());
    public static final Codec<byte[]> BASE64_STRING = Codec.STRING.comapFlatMap(p_274852_ -> {
        try {
            return DataResult.success(Base64.getDecoder().decode(p_274852_));
        } catch (IllegalArgumentException illegalargumentexception) {
            return DataResult.error(() -> "Malformed base64 string");
        }
    }, p_216180_ -> Base64.getEncoder().encodeToString(p_216180_));
    public static final Codec<String> ESCAPED_STRING = Codec.STRING
        .comapFlatMap(p_301741_ -> DataResult.success(StringEscapeUtils.unescapeJava(p_301741_)), StringEscapeUtils::escapeJava);
    public static final Codec<ExtraCodecs.TagOrElementLocation> TAG_OR_ELEMENT_ID = Codec.STRING
        .comapFlatMap(
            p_337578_ -> p_337578_.startsWith("#")
                    ? ResourceLocation.read(p_337578_.substring(1)).map(p_216182_ -> new ExtraCodecs.TagOrElementLocation(p_216182_, true))
                    : ResourceLocation.read(p_337578_).map(p_216165_ -> new ExtraCodecs.TagOrElementLocation(p_216165_, false)),
            ExtraCodecs.TagOrElementLocation::decoratedId
        );
    public static final Function<Optional<Long>, OptionalLong> toOptionalLong = p_216176_ -> p_216176_.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    public static final Function<OptionalLong, Optional<Long>> fromOptionalLong = p_216178_ -> p_216178_.isPresent()
            ? Optional.of(p_216178_.getAsLong())
            : Optional.empty();
    public static final Codec<BitSet> BIT_SET = Codec.LONG_STREAM
        .xmap(p_253514_ -> BitSet.valueOf(p_253514_.toArray()), p_253493_ -> Arrays.stream(p_253493_.toLongArray()));
    private static final Codec<Property> PROPERTY = RecordCodecBuilder.create(
        p_337580_ -> p_337580_.group(
                    Codec.STRING.fieldOf("name").forGetter(Property::name),
                    Codec.STRING.fieldOf("value").forGetter(Property::value),
                    Codec.STRING.lenientOptionalFieldOf("signature").forGetter(p_293823_ -> Optional.ofNullable(p_293823_.signature()))
                )
                .apply(p_337580_, (p_253494_, p_253495_, p_253496_) -> new Property(p_253494_, p_253495_, p_253496_.orElse(null)))
    );
    public static final Codec<PropertyMap> PROPERTY_MAP = Codec.either(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()), PROPERTY.listOf())
        .xmap(p_253515_ -> {
            PropertyMap propertymap = new PropertyMap();
            p_253515_.ifLeft(p_253506_ -> p_253506_.forEach((p_253500_, p_253501_) -> {
                    for (String s : p_253501_) {
                        propertymap.put(p_253500_, new Property(p_253500_, s));
                    }
                })).ifRight(p_293821_ -> {
                for (Property property : p_293821_) {
                    propertymap.put(property.name(), property);
                }
            });
            return propertymap;
        }, p_253504_ -> Either.right(p_253504_.values().stream().toList()));
    public static final Codec<String> PLAYER_NAME = Codec.string(0, 16)
        .validate(
            p_329972_ -> StringUtil.isValidPlayerName(p_329972_)
                    ? DataResult.success(p_329972_)
                    : DataResult.error(() -> "Player name contained disallowed characters: '" + p_329972_ + "'")
        );
    private static final MapCodec<GameProfile> GAME_PROFILE_WITHOUT_PROPERTIES = RecordCodecBuilder.mapCodec(
        p_329970_ -> p_329970_.group(
                    UUIDUtil.AUTHLIB_CODEC.fieldOf("id").forGetter(GameProfile::getId), PLAYER_NAME.fieldOf("name").forGetter(GameProfile::getName)
                )
                .apply(p_329970_, GameProfile::new)
    );
    public static final Codec<GameProfile> GAME_PROFILE = RecordCodecBuilder.create(
        p_337579_ -> p_337579_.group(
                    GAME_PROFILE_WITHOUT_PROPERTIES.forGetter(Function.identity()),
                    PROPERTY_MAP.lenientOptionalFieldOf("properties", new PropertyMap()).forGetter(GameProfile::getProperties)
                )
                .apply(p_337579_, (p_253518_, p_253519_) -> {
                    p_253519_.forEach((p_253511_, p_253512_) -> p_253518_.getProperties().put(p_253511_, p_253512_));
                    return p_253518_;
                })
    );
    public static final Codec<String> NON_EMPTY_STRING = Codec.STRING
        .validate(p_274858_ -> p_274858_.isEmpty() ? DataResult.error(() -> "Expected non-empty string") : DataResult.success(p_274858_));
    public static final Codec<Integer> CODEPOINT = Codec.STRING.comapFlatMap(p_284688_ -> {
        int[] aint = p_284688_.codePoints().toArray();
        return aint.length != 1 ? DataResult.error(() -> "Expected one codepoint, got: " + p_284688_) : DataResult.success(aint[0]);
    }, Character::toString);
    public static Codec<String> RESOURCE_PATH_CODEC = Codec.STRING
        .validate(
            p_293822_ -> !ResourceLocation.isValidPath(p_293822_)
                    ? DataResult.error(() -> "Invalid string to use as a resource path element: " + p_293822_)
                    : DataResult.success(p_293822_)
        );

    public static <T> Codec<T> converter(DynamicOps<T> p_304929_) {
        return Codec.PASSTHROUGH.xmap(p_304323_ -> p_304323_.convert(p_304929_).getValue(), p_304327_ -> new Dynamic<>(p_304929_, (T)p_304327_));
    }

    public static <P, I> Codec<I> intervalCodec(
        Codec<P> p_184362_, String p_184363_, String p_184364_, BiFunction<P, P, DataResult<I>> p_184365_, Function<I, P> p_184366_, Function<I, P> p_184367_
    ) {
        Codec<I> codec = Codec.list(p_184362_).comapFlatMap(p_337577_ -> Util.fixedSize((List<P>)p_337577_, 2).flatMap(p_184445_ -> {
                P p = p_184445_.get(0);
                P p1 = p_184445_.get(1);
                return p_184365_.apply(p, p1);
            }), p_184459_ -> ImmutableList.of(p_184366_.apply((I)p_184459_), p_184367_.apply((I)p_184459_)));
        Codec<I> codec1 = RecordCodecBuilder.<Pair<P, P>>create(
                p_184360_ -> p_184360_.group(p_184362_.fieldOf(p_184363_).forGetter(Pair::getFirst), p_184362_.fieldOf(p_184364_).forGetter(Pair::getSecond))
                        .apply(p_184360_, Pair::of)
            )
            .comapFlatMap(
                p_184392_ -> p_184365_.apply((P)p_184392_.getFirst(), (P)p_184392_.getSecond()),
                p_184449_ -> Pair.of(p_184366_.apply((I)p_184449_), p_184367_.apply((I)p_184449_))
            );
        Codec<I> codec2 = Codec.withAlternative(codec, codec1);
        return Codec.either(p_184362_, codec2)
            .comapFlatMap(p_184389_ -> p_184389_.map(p_184395_ -> p_184365_.apply((P)p_184395_, (P)p_184395_), DataResult::success), p_184411_ -> {
                P p = p_184366_.apply((I)p_184411_);
                P p1 = p_184367_.apply((I)p_184411_);
                return Objects.equals(p, p1) ? Either.left(p) : Either.right((I)p_184411_);
            });
    }

    public static <A> ResultFunction<A> orElsePartial(final A p_184382_) {
        return new ResultFunction<A>() {
            @Override
            public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> p_184466_, T p_184467_, DataResult<Pair<A, T>> p_184468_) {
                MutableObject<String> mutableobject = new MutableObject<>();
                Optional<Pair<A, T>> optional = p_184468_.resultOrPartial(mutableobject::setValue);
                return optional.isPresent()
                    ? p_184468_
                    : DataResult.error(() -> "(" + mutableobject.getValue() + " -> using default)", Pair.of(p_184382_, p_184467_));
            }

            @Override
            public <T> DataResult<T> coApply(DynamicOps<T> p_184470_, A p_184471_, DataResult<T> p_184472_) {
                return p_184472_;
            }

            @Override
            public String toString() {
                return "OrElsePartial[" + p_184382_ + "]";
            }
        };
    }

    public static <E> Codec<E> idResolverCodec(ToIntFunction<E> p_184422_, IntFunction<E> p_184423_, int p_184424_) {
        return Codec.INT
            .flatXmap(
                p_184414_ -> Optional.ofNullable(p_184423_.apply(p_184414_))
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unknown element id: " + p_184414_)),
                p_274850_ -> {
                    int i = p_184422_.applyAsInt((E)p_274850_);
                    return i == p_184424_ ? DataResult.error(() -> "Element with unknown id: " + p_274850_) : DataResult.success(i);
                }
            );
    }

    public static <E> Codec<E> orCompressed(final Codec<E> p_184426_, final Codec<E> p_184427_) {
        return new Codec<E>() {
            @Override
            public <T> DataResult<T> encode(E p_184483_, DynamicOps<T> p_184484_, T p_184485_) {
                return p_184484_.compressMaps() ? p_184427_.encode(p_184483_, p_184484_, p_184485_) : p_184426_.encode(p_184483_, p_184484_, p_184485_);
            }

            @Override
            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> p_184480_, T p_184481_) {
                return p_184480_.compressMaps() ? p_184427_.decode(p_184480_, p_184481_) : p_184426_.decode(p_184480_, p_184481_);
            }

            @Override
            public String toString() {
                return p_184426_ + " orCompressed " + p_184427_;
            }
        };
    }

    public static <E> MapCodec<E> orCompressed(final MapCodec<E> p_304545_, final MapCodec<E> p_304716_) {
        return new MapCodec<E>() {
            @Override
            public <T> RecordBuilder<T> encode(E p_304635_, DynamicOps<T> p_304428_, RecordBuilder<T> p_304777_) {
                return p_304428_.compressMaps() ? p_304716_.encode(p_304635_, p_304428_, p_304777_) : p_304545_.encode(p_304635_, p_304428_, p_304777_);
            }

            @Override
            public <T> DataResult<E> decode(DynamicOps<T> p_304666_, MapLike<T> p_304870_) {
                return p_304666_.compressMaps() ? p_304716_.decode(p_304666_, p_304870_) : p_304545_.decode(p_304666_, p_304870_);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> p_304533_) {
                return p_304716_.keys(p_304533_);
            }

            @Override
            public String toString() {
                return p_304545_ + " orCompressed " + p_304716_;
            }
        };
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> p_184369_, final Function<E, Lifecycle> p_184370_, final Function<E, Lifecycle> p_184371_) {
        return p_184369_.mapResult(new ResultFunction<E>() {
            @Override
            public <T> DataResult<Pair<E, T>> apply(DynamicOps<T> p_304965_, T p_304933_, DataResult<Pair<E, T>> p_304851_) {
                return p_304851_.result().map(p_337585_ -> p_304851_.setLifecycle(p_184370_.apply(p_337585_.getFirst()))).orElse(p_304851_);
            }

            @Override
            public <T> DataResult<T> coApply(DynamicOps<T> p_304780_, E p_304414_, DataResult<T> p_304779_) {
                return p_304779_.setLifecycle(p_184371_.apply(p_304414_));
            }

            @Override
            public String toString() {
                return "WithLifecycle[" + p_184370_ + " " + p_184371_ + "]";
            }
        });
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> p_326460_, Function<E, Lifecycle> p_326480_) {
        return overrideLifecycle(p_326460_, p_326480_, p_326480_);
    }

    public static <K, V> ExtraCodecs.StrictUnboundedMapCodec<K, V> strictUnboundedMap(Codec<K> p_301201_, Codec<V> p_300984_) {
        return new ExtraCodecs.StrictUnboundedMapCodec<>(p_301201_, p_300984_);
    }

    private static Codec<Integer> intRangeWithMessage(int p_144634_, int p_144635_, Function<Integer, String> p_144636_) {
        return Codec.INT
            .validate(
                p_274889_ -> p_274889_.compareTo(p_144634_) >= 0 && p_274889_.compareTo(p_144635_) <= 0
                        ? DataResult.success(p_274889_)
                        : DataResult.error(() -> p_144636_.apply(p_274889_))
            );
    }

    public static Codec<Integer> intRange(int p_270883_, int p_270323_) {
        return intRangeWithMessage(p_270883_, p_270323_, p_269784_ -> "Value must be within range [" + p_270883_ + ";" + p_270323_ + "]: " + p_269784_);
    }

    private static Codec<Float> floatRangeMinExclusiveWithMessage(float p_184351_, float p_184352_, Function<Float, String> p_184353_) {
        return Codec.FLOAT
            .validate(
                p_274865_ -> p_274865_.compareTo(p_184351_) > 0 && p_274865_.compareTo(p_184352_) <= 0
                        ? DataResult.success(p_274865_)
                        : DataResult.error(() -> p_184353_.apply(p_274865_))
            );
    }

    public static <T> Codec<List<T>> nonEmptyList(Codec<List<T>> p_144638_) {
        return p_144638_.validate(p_274853_ -> p_274853_.isEmpty() ? DataResult.error(() -> "List must have contents") : DataResult.success(p_274853_));
    }

    public static <T> Codec<HolderSet<T>> nonEmptyHolderSet(Codec<HolderSet<T>> p_203983_) {
        return p_203983_.validate(
            p_274860_ -> p_274860_.unwrap().right().filter(List::isEmpty).isPresent()
                    ? DataResult.error(() -> "List must have contents")
                    : DataResult.success(p_274860_)
        );
    }

    public static <E> MapCodec<E> retrieveContext(final Function<DynamicOps<?>, DataResult<E>> p_203977_) {
        class ContextRetrievalCodec extends MapCodec<E> {
            @Override
            public <T> RecordBuilder<T> encode(E p_203993_, DynamicOps<T> p_203994_, RecordBuilder<T> p_203995_) {
                return p_203995_;
            }

            @Override
            public <T> DataResult<E> decode(DynamicOps<T> p_203990_, MapLike<T> p_203991_) {
                return p_203977_.apply(p_203990_);
            }

            @Override
            public String toString() {
                return "ContextRetrievalCodec[" + p_203977_ + "]";
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> p_203997_) {
                return Stream.empty();
            }
        }

        return new ContextRetrievalCodec();
    }

    public static <E, L extends Collection<E>, T> Function<L, DataResult<L>> ensureHomogenous(Function<E, T> p_203985_) {
        return p_203980_ -> {
            Iterator<E> iterator = p_203980_.iterator();
            if (iterator.hasNext()) {
                T t = p_203985_.apply(iterator.next());

                while (iterator.hasNext()) {
                    E e = iterator.next();
                    T t1 = p_203985_.apply(e);
                    if (t1 != t) {
                        return DataResult.error(() -> "Mixed type list: element " + e + " had type " + t1 + ", but list is of type " + t);
                    }
                }
            }

            return DataResult.success(p_203980_, Lifecycle.stable());
        };
    }

    public static <A> Codec<A> catchDecoderException(final Codec<A> p_216186_) {
        return Codec.of(p_216186_, new Decoder<A>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> p_304559_, T p_304739_) {
                try {
                    return p_216186_.decode(p_304559_, p_304739_);
                } catch (Exception exception) {
                    return DataResult.error(() -> "Caught exception decoding " + p_304739_ + ": " + exception.getMessage());
                }
            }
        });
    }

    public static Codec<TemporalAccessor> temporalCodec(DateTimeFormatter p_216171_) {
        return Codec.STRING.comapFlatMap(p_300789_ -> {
            try {
                return DataResult.success(p_216171_.parse(p_300789_));
            } catch (Exception exception) {
                return DataResult.error(exception::getMessage);
            }
        }, p_216171_::format);
    }

    public static MapCodec<OptionalLong> asOptionalLong(MapCodec<Optional<Long>> p_216167_) {
        return p_216167_.xmap(toOptionalLong, fromOptionalLong);
    }

    public static <K, V> Codec<Map<K, V>> sizeLimitedMap(Codec<Map<K, V>> p_330976_, int p_331151_) {
        return p_330976_.validate(
            p_329967_ -> p_329967_.size() > p_331151_
                    ? DataResult.error(() -> "Map is too long: " + p_329967_.size() + ", expected range [0-" + p_331151_ + "]")
                    : DataResult.success(p_329967_)
        );
    }

    public static <T> Codec<Object2BooleanMap<T>> object2BooleanMap(Codec<T> p_298255_) {
        return Codec.unboundedMap(p_298255_, Codec.BOOL).xmap(Object2BooleanOpenHashMap::new, Object2ObjectOpenHashMap::new);
    }

    @Deprecated
    public static <K, V> MapCodec<V> dispatchOptionalValue(
        final String p_312812_,
        final String p_312135_,
        final Codec<K> p_312848_,
        final Function<? super V, ? extends K> p_312622_,
        final Function<? super K, ? extends Codec<? extends V>> p_312836_
    ) {
        return new MapCodec<V>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> p_312501_) {
                return Stream.of(p_312501_.createString(p_312812_), p_312501_.createString(p_312135_));
            }

            @Override
            public <T> DataResult<V> decode(DynamicOps<T> p_312013_, MapLike<T> p_312527_) {
                T t = p_312527_.get(p_312812_);
                return t == null
                    ? DataResult.error(() -> "Missing \"" + p_312812_ + "\" in: " + p_312527_)
                    : p_312848_.decode(p_312013_, t).flatMap(p_337590_ -> {
                        T t1 = Objects.requireNonNullElseGet(p_312527_.get(p_312135_), p_312013_::emptyMap);
                        return p_312836_.apply(p_337590_.getFirst()).decode(p_312013_, t1).map(Pair::getFirst);
                    });
            }

            @Override
            public <T> RecordBuilder<T> encode(V p_312741_, DynamicOps<T> p_312649_, RecordBuilder<T> p_312297_) {
                K k = (K)p_312622_.apply(p_312741_);
                p_312297_.add(p_312812_, p_312848_.encodeStart(p_312649_, k));
                DataResult<T> dataresult = this.encode((Codec<? extends V>)p_312836_.apply(k), p_312741_, p_312649_);
                if (dataresult.result().isEmpty() || !Objects.equals(dataresult.result().get(), p_312649_.emptyMap())) {
                    p_312297_.add(p_312135_, dataresult);
                }

                return p_312297_;
            }

            private <T, V2 extends V> DataResult<T> encode(Codec<V2> p_312014_, V p_312190_, DynamicOps<T> p_312075_) {
                return p_312014_.encodeStart(p_312075_, (V2)p_312190_);
            }
        };
    }

    public static <A> Codec<Optional<A>> optionalEmptyMap(final Codec<A> p_330630_) {
        return new Codec<Optional<A>>() {
            @Override
            public <T> DataResult<Pair<Optional<A>, T>> decode(DynamicOps<T> p_330879_, T p_330924_) {
                return isEmptyMap(p_330879_, p_330924_)
                    ? DataResult.success(Pair.of(Optional.empty(), p_330924_))
                    : p_330630_.decode(p_330879_, p_330924_).map(p_337591_ -> p_337591_.mapFirst(Optional::of));
            }

            private static <T> boolean isEmptyMap(DynamicOps<T> p_338754_, T p_338581_) {
                Optional<MapLike<T>> optional = p_338754_.getMap(p_338581_).result();
                return optional.isPresent() && optional.get().entries().findAny().isEmpty();
            }

            public <T> DataResult<T> encode(Optional<A> p_338508_, DynamicOps<T> p_331521_, T p_331876_) {
                return p_338508_.isEmpty() ? DataResult.success(p_331521_.emptyMap()) : p_330630_.encode(p_338508_.get(), p_331521_, p_331876_);
            }
        };
    }

    public static record StrictUnboundedMapCodec<K, V>(Codec<K> keyCodec, Codec<V> elementCodec) implements Codec<Map<K, V>>, BaseMapCodec<K, V> {
        @Override
        public <T> DataResult<Map<K, V>> decode(DynamicOps<T> p_301018_, MapLike<T> p_301263_) {
            Builder<K, V> builder = ImmutableMap.builder();

            for (Pair<T, T> pair : p_301263_.entries().toList()) {
                DataResult<K> dataresult = this.keyCodec().parse(p_301018_, pair.getFirst());
                DataResult<V> dataresult1 = this.elementCodec().parse(p_301018_, pair.getSecond());
                DataResult<Pair<K, V>> dataresult2 = dataresult.apply2stable(Pair::of, dataresult1);
                Optional<Error<Pair<K, V>>> optional = dataresult2.error();
                if (optional.isPresent()) {
                    String s = optional.get().message();
                    return DataResult.error(() -> dataresult.result().isPresent() ? "Map entry '" + dataresult.result().get() + "' : " + s : s);
                }

                if (!dataresult2.result().isPresent()) {
                    return DataResult.error(() -> "Empty or invalid map contents are not allowed");
                }

                Pair<K, V> pair1 = dataresult2.result().get();
                builder.put(pair1.getFirst(), pair1.getSecond());
            }

            Map<K, V> map = builder.build();
            return DataResult.success(map);
        }

        @Override
        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> p_301258_, T p_301052_) {
            return p_301258_.getMap(p_301052_)
                .setLifecycle(Lifecycle.stable())
                .flatMap(p_301208_ -> this.decode(p_301258_, (MapLike<T>)p_301208_))
                .map(p_300941_ -> Pair.of((Map<K, V>)p_300941_, p_301052_));
        }

        public <T> DataResult<T> encode(Map<K, V> p_301101_, DynamicOps<T> p_301252_, T p_301326_) {
            return this.encode(p_301101_, p_301252_, p_301252_.mapBuilder()).build(p_301326_);
        }

        @Override
        public String toString() {
            return "StrictUnboundedMapCodec[" + this.keyCodec + " -> " + this.elementCodec + "]";
        }
    }

    public static record TagOrElementLocation(ResourceLocation id, boolean tag) {
        @Override
        public String toString() {
            return this.decoratedId();
        }

        private String decoratedId() {
            return this.tag ? "#" + this.id : this.id.toString();
        }
    }
}
