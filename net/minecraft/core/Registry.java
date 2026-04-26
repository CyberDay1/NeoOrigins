package net.minecraft.core;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import com.mojang.serialization.Lifecycle;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

public interface Registry<T> extends Keyable, IdMap<T>, net.neoforged.neoforge.registries.IRegistryExtension<T> {
    ResourceKey<? extends Registry<T>> key();

    default Codec<T> byNameCodec() {
        return this.referenceHolderWithLifecycle()
            .flatComapMap(Holder.Reference::value, p_325515_ -> this.safeCastToReference(this.wrapAsHolder((T)p_325515_)));
    }

    default Codec<Holder<T>> holderByNameCodec() {
        return this.referenceHolderWithLifecycle().flatComapMap(p_325516_ -> (Holder<T>)p_325516_, this::safeCastToReference);
    }

    private Codec<Holder.Reference<T>> referenceHolderWithLifecycle() {
        Codec<Holder.Reference<T>> codec = ResourceLocation.CODEC
            .comapFlatMap(
                p_315852_ -> this.getHolder(p_315852_)
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unknown registry key in " + this.key() + ": " + p_315852_)),
                p_325513_ -> p_325513_.key().location()
            );
        return ExtraCodecs.overrideLifecycle(
            codec, p_325514_ -> this.registrationInfo(p_325514_.key()).map(RegistrationInfo::lifecycle).orElse(Lifecycle.experimental())
        );
    }

    private DataResult<Holder.Reference<T>> safeCastToReference(Holder<T> p_326365_) {
        return p_326365_.getDelegate() instanceof Holder.Reference reference
            ? DataResult.success(reference)
            : DataResult.error(() -> "Unregistered holder in " + this.key() + ": " + p_326365_);
    }

    @Override
    default <U> Stream<U> keys(DynamicOps<U> p_123030_) {
        return this.keySet().stream().map(p_235784_ -> p_123030_.createString(p_235784_.toString()));
    }

    @Nullable
    ResourceLocation getKey(T p_123006_);

    Optional<ResourceKey<T>> getResourceKey(T p_123008_);

    @Override
    int getId(@Nullable T p_122977_);

    @Nullable
    T get(@Nullable ResourceKey<T> p_122980_);

    @Nullable
    T get(@Nullable ResourceLocation p_123002_);

    Optional<RegistrationInfo> registrationInfo(ResourceKey<T> p_326162_);

    Lifecycle registryLifecycle();

    default Optional<T> getOptional(@Nullable ResourceLocation p_123007_) {
        return Optional.ofNullable(this.get(p_123007_));
    }

    default Optional<T> getOptional(@Nullable ResourceKey<T> p_123010_) {
        return Optional.ofNullable(this.get(p_123010_));
    }

    Optional<Holder.Reference<T>> getAny();

    default T getOrThrow(ResourceKey<T> p_123014_) {
        T t = this.get(p_123014_);
        if (t == null) {
            throw new IllegalStateException("Missing key in " + this.key() + ": " + p_123014_);
        } else {
            return t;
        }
    }

    Set<ResourceLocation> keySet();

    Set<Entry<ResourceKey<T>, T>> entrySet();

    Set<ResourceKey<T>> registryKeySet();

    Optional<Holder.Reference<T>> getRandom(RandomSource p_235781_);

    default Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    boolean containsKey(ResourceLocation p_123011_);

    boolean containsKey(ResourceKey<T> p_175475_);

    static <T> T register(Registry<? super T> p_122962_, String p_122963_, T p_122964_) {
        return register(p_122962_, ResourceLocation.parse(p_122963_), p_122964_);
    }

    static <V, T extends V> T register(Registry<V> p_122966_, ResourceLocation p_122967_, T p_122968_) {
        return register(p_122966_, ResourceKey.create(p_122966_.key(), p_122967_), p_122968_);
    }

    static <V, T extends V> T register(Registry<V> p_194580_, ResourceKey<V> p_194581_, T p_194582_) {
        ((WritableRegistry)p_194580_).register(p_194581_, (V)p_194582_, RegistrationInfo.BUILT_IN);
        return p_194582_;
    }

    static <T> Holder.Reference<T> registerForHolder(Registry<T> p_263347_, ResourceKey<T> p_263355_, T p_263428_) {
        return ((WritableRegistry)p_263347_).register(p_263355_, p_263428_, RegistrationInfo.BUILT_IN);
    }

    static <T> Holder.Reference<T> registerForHolder(Registry<T> p_263351_, ResourceLocation p_263363_, T p_263423_) {
        return registerForHolder(p_263351_, ResourceKey.create(p_263351_.key(), p_263363_), p_263423_);
    }

    Registry<T> freeze();

    Holder.Reference<T> createIntrusiveHolder(T p_206068_);

    Optional<Holder.Reference<T>> getHolder(int p_206051_);

    Optional<Holder.Reference<T>> getHolder(ResourceLocation p_316492_);

    Optional<Holder.Reference<T>> getHolder(ResourceKey<T> p_206050_);

    Holder<T> wrapAsHolder(T p_263382_);

    default Holder.Reference<T> getHolderOrThrow(ResourceKey<T> p_249087_) {
        return this.getHolder(p_249087_).orElseThrow(() -> new IllegalStateException("Missing key in " + this.key() + ": " + p_249087_));
    }

    Stream<Holder.Reference<T>> holders();

    Optional<HolderSet.Named<T>> getTag(TagKey<T> p_206052_);

    default Iterable<Holder<T>> getTagOrEmpty(TagKey<T> p_206059_) {
        return DataFixUtils.orElse(this.getTag(p_206059_), List.of());
    }

    default Optional<Holder<T>> getRandomElementOf(TagKey<T> p_320357_, RandomSource p_320935_) {
        return this.getTag(p_320357_).flatMap(p_319421_ -> p_319421_.getRandomElement(p_320935_));
    }

    HolderSet.Named<T> getOrCreateTag(TagKey<T> p_206045_);

    Stream<Pair<TagKey<T>, HolderSet.Named<T>>> getTags();

    Stream<TagKey<T>> getTagNames();

    void resetTags();

    void bindTags(Map<TagKey<T>, List<Holder<T>>> p_205997_);

    default IdMap<Holder<T>> asHolderIdMap() {
        return new IdMap<Holder<T>>() {
            public int getId(Holder<T> p_259992_) {
                return Registry.this.getId(p_259992_.value());
            }

            @Nullable
            public Holder<T> byId(int p_259972_) {
                return (Holder<T>)Registry.this.getHolder(p_259972_).orElse(null);
            }

            @Override
            public int size() {
                return Registry.this.size();
            }

            @Override
            public Iterator<Holder<T>> iterator() {
                return Registry.this.holders().map(p_260061_ -> (Holder<T>)p_260061_).iterator();
            }
        };
    }

    HolderOwner<T> holderOwner();

    HolderLookup.RegistryLookup<T> asLookup();

    default HolderLookup.RegistryLookup<T> asTagAddingLookup() {
        return new HolderLookup.RegistryLookup.Delegate<T>() {
            @Override
            public HolderLookup.RegistryLookup<T> parent() {
                return Registry.this.asLookup();
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> p_259111_) {
                return Optional.of(this.getOrThrow(p_259111_));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> p_259653_) {
                return Registry.this.getOrCreateTag(p_259653_);
            }
        };
    }
}
