package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringRepresentable;

public class ComponentSerialization {
    public static final Codec<Component> CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_STREAM_CODEC.apply(
        ByteBufCodecs::optional
    );
    public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CODEC);
    public static final Codec<Component> FLAT_CODEC = flatCodec(Integer.MAX_VALUE);

    public static Codec<Component> flatCodec(int p_331605_) {
        final Codec<String> codec = Codec.string(0, p_331605_);
        return new Codec<Component>() {
            @Override
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> p_330684_, T p_331653_) {
                DynamicOps<JsonElement> dynamicops = asJsonOps(p_330684_);
                return codec.decode(p_330684_, p_331653_).flatMap(p_337496_ -> {
                    try {
                        JsonElement jsonelement = JsonParser.parseString(p_337496_.getFirst());
                        return ComponentSerialization.CODEC.parse(dynamicops, jsonelement).map(p_330859_ -> Pair.of(p_330859_, (T)p_337496_.getSecond()));
                    } catch (JsonParseException jsonparseexception) {
                        return DataResult.error(jsonparseexception::getMessage);
                    }
                });
            }

            public <T> DataResult<T> encode(Component p_330452_, DynamicOps<T> p_330865_, T p_331460_) {
                DynamicOps<JsonElement> dynamicops = asJsonOps(p_330865_);
                return ComponentSerialization.CODEC.encodeStart(dynamicops, p_330452_).flatMap(p_331436_ -> {
                    try {
                        return codec.encodeStart(p_330865_, GsonHelper.toStableString(p_331436_));
                    } catch (IllegalArgumentException illegalargumentexception) {
                        return DataResult.error(illegalargumentexception::getMessage);
                    }
                });
            }

            private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> p_330749_) {
                return (DynamicOps<JsonElement>)(p_330749_ instanceof RegistryOps<T> registryops ? registryops.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
            }
        };
    }

    private static MutableComponent createFromList(List<Component> p_304405_) {
        MutableComponent mutablecomponent = p_304405_.get(0).copy();

        for (int i = 1; i < p_304405_.size(); i++) {
            mutablecomponent.append(p_304405_.get(i));
        }

        return mutablecomponent;
    }

    public static <T extends StringRepresentable, E> MapCodec<E> createLegacyComponentMatcher(
        T[] p_304815_, Function<T, MapCodec<? extends E>> p_304852_, Function<E, T> p_304510_, String p_307551_
    ) {
        MapCodec<E> mapcodec = new ComponentSerialization.FuzzyCodec<>(
            Stream.<T>of(p_304815_).map(p_304852_).toList(), p_304601_ -> p_304852_.apply(p_304510_.apply(p_304601_))
        );
        Codec<T> codec = StringRepresentable.fromValues((Supplier<T[]>)(() -> p_304815_));
        MapCodec<E> mapcodec1 = codec.dispatchMap(p_307551_, p_304510_, p_304852_);
        MapCodec<E> mapcodec2 = new ComponentSerialization.StrictEither<>(p_307551_, mapcodec1, mapcodec);
        return ExtraCodecs.orCompressed(mapcodec2, mapcodec1);
    }

    private static Codec<Component> createCodec(Codec<Component> p_304554_) {
        ComponentContents.Type<?>[] type = new ComponentContents.Type[]{
            PlainTextContents.TYPE, TranslatableContents.TYPE, KeybindContents.TYPE, ScoreContents.TYPE, SelectorContents.TYPE, NbtContents.TYPE, net.neoforged.neoforge.common.util.InsertingContents.TYPE
        };
        MapCodec<ComponentContents> mapcodec = createLegacyComponentMatcher(type, ComponentContents.Type::codec, ComponentContents::type, "type");
        Codec<Component> codec = RecordCodecBuilder.create(
            p_337494_ -> p_337494_.group(
                        mapcodec.forGetter(Component::getContents),
                        ExtraCodecs.nonEmptyList(p_304554_.listOf()).optionalFieldOf("extra", List.of()).forGetter(Component::getSiblings),
                        Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)
                    )
                    .apply(p_337494_, MutableComponent::new)
        );
        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(p_304554_.listOf())), codec)
            .xmap(
                p_304547_ -> p_304547_.map(
                        p_304568_ -> p_304568_.map(Component::literal, ComponentSerialization::createFromList), p_304887_ -> (Component)p_304887_
                    ),
                p_304501_ -> {
                    String s = p_304501_.tryCollapseToString();
                    return s != null ? Either.left(Either.left(s)) : Either.right(p_304501_);
                }
            );
    }

    static class FuzzyCodec<T> extends MapCodec<T> {
        private final List<MapCodec<? extends T>> codecs;
        private final Function<T, MapEncoder<? extends T>> encoderGetter;

        public FuzzyCodec(List<MapCodec<? extends T>> p_304495_, Function<T, MapEncoder<? extends T>> p_304594_) {
            this.codecs = p_304495_;
            this.encoderGetter = p_304594_;
        }

        @Override
        public <S> DataResult<T> decode(DynamicOps<S> p_304922_, MapLike<S> p_304447_) {
            for (MapDecoder<? extends T> mapdecoder : this.codecs) {
                DataResult<? extends T> dataresult = mapdecoder.decode(p_304922_, p_304447_);
                if (dataresult.result().isPresent()) {
                    return (DataResult<T>)dataresult;
                }
            }

            return DataResult.error(() -> "No matching codec found");
        }

        @Override
        public <S> RecordBuilder<S> encode(T p_304409_, DynamicOps<S> p_304945_, RecordBuilder<S> p_304823_) {
            MapEncoder<T> mapencoder = (MapEncoder<T>)this.encoderGetter.apply(p_304409_);
            return mapencoder.encode(p_304409_, p_304945_, p_304823_);
        }

        @Override
        public <S> Stream<S> keys(DynamicOps<S> p_304770_) {
            return this.codecs.stream().flatMap(p_304401_ -> p_304401_.keys(p_304770_)).distinct();
        }

        @Override
        public String toString() {
            return "FuzzyCodec[" + this.codecs + "]";
        }
    }

    static class StrictEither<T> extends MapCodec<T> {
        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public StrictEither(String p_307439_, MapCodec<T> p_307644_, MapCodec<T> p_307415_) {
            this.typeFieldName = p_307439_;
            this.typed = p_307644_;
            this.fuzzy = p_307415_;
        }

        @Override
        public <O> DataResult<T> decode(DynamicOps<O> p_307279_, MapLike<O> p_307655_) {
            return p_307655_.get(this.typeFieldName) != null ? this.typed.decode(p_307279_, p_307655_) : this.fuzzy.decode(p_307279_, p_307655_);
        }

        @Override
        public <O> RecordBuilder<O> encode(T p_307352_, DynamicOps<O> p_307510_, RecordBuilder<O> p_307297_) {
            return this.fuzzy.encode(p_307352_, p_307510_, p_307297_);
        }

        @Override
        public <T1> Stream<T1> keys(DynamicOps<T1> p_307616_) {
            return Stream.concat(this.typed.keys(p_307616_), this.fuzzy.keys(p_307616_)).distinct();
        }
    }
}
