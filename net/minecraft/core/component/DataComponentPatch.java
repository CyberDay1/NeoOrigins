package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;

public final class DataComponentPatch {
    public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
    public static final Codec<DataComponentPatch> CODEC = Codec.<DataComponentPatch.PatchKey, Object>dispatchedMap(DataComponentPatch.PatchKey.CODEC, DataComponentPatch.PatchKey::valueCodec)
        .xmap(p_330885_ -> {
            if (p_330885_.isEmpty()) {
                return EMPTY;
            } else {
                Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap<>(p_330885_.size());

                for (Entry<DataComponentPatch.PatchKey, ?> entry : p_330885_.entrySet()) {
                    DataComponentPatch.PatchKey datacomponentpatch$patchkey = entry.getKey();
                    if (datacomponentpatch$patchkey.removed()) {
                        reference2objectmap.put(datacomponentpatch$patchkey.type(), Optional.empty());
                    } else {
                        reference2objectmap.put(datacomponentpatch$patchkey.type(), Optional.of(entry.getValue()));
                    }
                }

                return new DataComponentPatch(reference2objectmap);
            }
        }, p_332132_ -> {
            Reference2ObjectMap<DataComponentPatch.PatchKey, Object> reference2objectmap = new Reference2ObjectArrayMap<>(p_332132_.map.size());

            for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(p_332132_.map)) {
                DataComponentType<?> datacomponenttype = entry.getKey();
                if (!datacomponenttype.isTransient()) {
                    Optional<?> optional = entry.getValue();
                    if (optional.isPresent()) {
                        reference2objectmap.put(new DataComponentPatch.PatchKey(datacomponenttype, false), optional.get());
                    } else {
                        reference2objectmap.put(new DataComponentPatch.PatchKey(datacomponenttype, true), Unit.INSTANCE);
                    }
                }
            }

            return reference2objectmap;
        });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
        public DataComponentPatch decode(RegistryFriendlyByteBuf p_331850_) {
            int i = p_331850_.readVarInt();
            int j = p_331850_.readVarInt();
            if (i == 0 && j == 0) {
                return DataComponentPatch.EMPTY;
            } else {
                int k = i + j;
                Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap<>(Math.min(k, 65536));

                for (int l = 0; l < i; l++) {
                    DataComponentType<?> datacomponenttype = DataComponentType.STREAM_CODEC.decode(p_331850_);
                    Object object = datacomponenttype.streamCodec().decode(p_331850_);
                    reference2objectmap.put(datacomponenttype, Optional.of(object));
                }

                for (int i1 = 0; i1 < j; i1++) {
                    DataComponentType<?> datacomponenttype1 = DataComponentType.STREAM_CODEC.decode(p_331850_);
                    reference2objectmap.put(datacomponenttype1, Optional.empty());
                }

                return new DataComponentPatch(reference2objectmap);
            }
        }

        public void encode(RegistryFriendlyByteBuf p_330636_, DataComponentPatch p_330346_) {
            if (p_330346_.isEmpty()) {
                p_330636_.writeVarInt(0);
                p_330636_.writeVarInt(0);
            } else {
                int i = 0;
                int j = 0;

                for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                    p_330346_.map
                )) {
                    if (entry.getValue().isPresent()) {
                        i++;
                    } else {
                        j++;
                    }
                }

                p_330636_.writeVarInt(i);
                p_330636_.writeVarInt(j);

                for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry1 : Reference2ObjectMaps.fastIterable(
                    p_330346_.map
                )) {
                    Optional<?> optional = entry1.getValue();
                    if (optional.isPresent()) {
                        DataComponentType<?> datacomponenttype = entry1.getKey();
                        DataComponentType.STREAM_CODEC.encode(p_330636_, datacomponenttype);
                        encodeComponent(p_330636_, datacomponenttype, optional.get());
                    }
                }

                for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry2 : Reference2ObjectMaps.fastIterable(
                    p_330346_.map
                )) {
                    if (entry2.getValue().isEmpty()) {
                        DataComponentType<?> datacomponenttype1 = entry2.getKey();
                        DataComponentType.STREAM_CODEC.encode(p_330636_, datacomponenttype1);
                    }
                }
            }
        }

        private static <T> void encodeComponent(RegistryFriendlyByteBuf p_331735_, DataComponentType<T> p_330633_, Object p_331089_) {
            p_330633_.streamCodec().encode(p_331735_, (T)p_331089_);
        }
    };
    private static final String REMOVED_PREFIX = "!";
    final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

    DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> p_331816_) {
        this.map = p_331816_;
    }

    public static DataComponentPatch.Builder builder() {
        return new DataComponentPatch.Builder();
    }

    @Nullable
    public <T> Optional<? extends T> get(DataComponentType<? extends T> p_331532_) {
        return (Optional<? extends T>)this.map.get(p_331532_);
    }

    public Set<Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return this.map.entrySet();
    }

    public int size() {
        return this.map.size();
    }

    public DataComponentPatch forget(Predicate<DataComponentType<?>> p_338629_) {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap<>(this.map);
            reference2objectmap.keySet().removeIf(p_338629_);
            return reference2objectmap.isEmpty() ? EMPTY : new DataComponentPatch(reference2objectmap);
        }
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public DataComponentPatch.SplitResult split() {
        if (this.isEmpty()) {
            return DataComponentPatch.SplitResult.EMPTY;
        } else {
            DataComponentMap.Builder datacomponentmap$builder = DataComponentMap.builder();
            Set<DataComponentType<?>> set = Sets.newIdentityHashSet();
            this.map.forEach((p_337451_, p_337452_) -> {
                if (p_337452_.isPresent()) {
                    datacomponentmap$builder.setUnchecked((DataComponentType<?>)p_337451_, p_337452_.get());
                } else {
                    set.add((DataComponentType<?>)p_337451_);
                }
            });
            return new DataComponentPatch.SplitResult(datacomponentmap$builder.build(), set);
        }
    }

    @Override
    public boolean equals(Object p_331686_) {
        if (this == p_331686_) {
            return true;
        } else {
            if (p_331686_ instanceof DataComponentPatch datacomponentpatch && this.map.equals(datacomponentpatch.map)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    @Override
    public String toString() {
        return toString(this.map);
    }

    static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> p_330935_) {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append('{');
        boolean flag = true;

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(p_330935_)) {
            if (flag) {
                flag = false;
            } else {
                stringbuilder.append(", ");
            }

            Optional<?> optional = entry.getValue();
            if (optional.isPresent()) {
                stringbuilder.append(entry.getKey());
                stringbuilder.append("=>");
                stringbuilder.append(optional.get());
            } else {
                stringbuilder.append("!");
                stringbuilder.append(entry.getKey());
            }
        }

        stringbuilder.append('}');
        return stringbuilder.toString();
    }

    public static class Builder {
        private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap<>();

        Builder() {
        }

        public <T> DataComponentPatch.Builder set(DataComponentType<T> p_332004_, T p_331566_) {
            net.neoforged.neoforge.common.CommonHooks.validateComponent(p_331566_);
            this.map.put(p_332004_, Optional.of(p_331566_));
            return this;
        }

        public <T> DataComponentPatch.Builder remove(DataComponentType<T> p_330845_) {
            this.map.put(p_330845_, Optional.empty());
            return this;
        }

        public <T> DataComponentPatch.Builder set(TypedDataComponent<T> p_331321_) {
            return this.set(p_331321_.type(), p_331321_.value());
        }

        public DataComponentPatch build() {
            return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
        }
    }

    static record PatchKey(DataComponentType<?> type, boolean removed) {
        public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING
            .flatXmap(
                p_330929_ -> {
                    boolean flag = p_330929_.startsWith("!");
                    if (flag) {
                        p_330929_ = p_330929_.substring("!".length());
                    }

                    ResourceLocation resourcelocation = ResourceLocation.tryParse(p_330929_);
                    DataComponentType<?> datacomponenttype = BuiltInRegistries.DATA_COMPONENT_TYPE.get(resourcelocation);
                    if (datacomponenttype == null) {
                        return DataResult.error(() -> "No component with type: '" + resourcelocation + "'");
                    } else {
                        return datacomponenttype.isTransient()
                            ? DataResult.error(() -> "'" + resourcelocation + "' is not a persistent component")
                            : DataResult.success(new DataComponentPatch.PatchKey(datacomponenttype, flag));
                    }
                },
                p_339345_ -> {
                    DataComponentType<?> datacomponenttype = p_339345_.type();
                    ResourceLocation resourcelocation = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(datacomponenttype);
                    return resourcelocation == null
                        ? DataResult.error(() -> "Unregistered component: " + datacomponenttype)
                        : DataResult.success(p_339345_.removed() ? "!" + resourcelocation : resourcelocation.toString());
                }
            );

        public Codec<?> valueCodec() {
            return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
        }
    }

    public static record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {
        public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
    }
}
