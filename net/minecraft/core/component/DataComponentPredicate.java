package net.minecraft.core.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class DataComponentPredicate implements Predicate<DataComponentMap> {
    public static final Codec<DataComponentPredicate> CODEC = DataComponentType.VALUE_MAP_CODEC
        .xmap(
            p_330430_ -> new DataComponentPredicate(p_330430_.entrySet().stream().map(TypedDataComponent::fromEntryUnchecked).collect(Collectors.toList())),
            p_337454_ -> p_337454_.expectedComponents
                    .stream()
                    .filter(p_337453_ -> !p_337453_.type().isTransient())
                    .collect(Collectors.toMap(TypedDataComponent::type, TypedDataComponent::value))
        );
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate> STREAM_CODEC = TypedDataComponent.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(DataComponentPredicate::new, p_331347_ -> p_331347_.expectedComponents);
    public static final DataComponentPredicate EMPTY = new DataComponentPredicate(List.of());
    private final List<TypedDataComponent<?>> expectedComponents;

    DataComponentPredicate(List<TypedDataComponent<?>> p_330446_) {
        this.expectedComponents = p_330446_;
    }

    public static DataComponentPredicate.Builder builder() {
        return new DataComponentPredicate.Builder();
    }

    public static DataComponentPredicate allOf(DataComponentMap p_331623_) {
        return new DataComponentPredicate(ImmutableList.copyOf(p_331623_));
    }

    @Override
    public boolean equals(Object p_330889_) {
        if (p_330889_ instanceof DataComponentPredicate datacomponentpredicate && this.expectedComponents.equals(datacomponentpredicate.expectedComponents)) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return this.expectedComponents.hashCode();
    }

    @Override
    public String toString() {
        return this.expectedComponents.toString();
    }

    public boolean test(DataComponentMap p_331560_) {
        for (TypedDataComponent<?> typeddatacomponent : this.expectedComponents) {
            Object object = p_331560_.get(typeddatacomponent.type());
            if (!Objects.equals(typeddatacomponent.value(), object)) {
                return false;
            }
        }

        return true;
    }

    public boolean test(DataComponentHolder p_331666_) {
        return this.test(p_331666_.getComponents());
    }

    public boolean alwaysMatches() {
        return this.expectedComponents.isEmpty();
    }

    public DataComponentPatch asPatch() {
        DataComponentPatch.Builder datacomponentpatch$builder = DataComponentPatch.builder();

        for (TypedDataComponent<?> typeddatacomponent : this.expectedComponents) {
            datacomponentpatch$builder.set(typeddatacomponent);
        }

        return datacomponentpatch$builder.build();
    }

    public static class Builder {
        private final List<TypedDataComponent<?>> expectedComponents = new ArrayList<>();

        Builder() {
        }

        public <T> DataComponentPredicate.Builder expect(DataComponentType<? super T> p_331861_, T p_330813_) {
            for (TypedDataComponent<?> typeddatacomponent : this.expectedComponents) {
                if (typeddatacomponent.type() == p_331861_) {
                    throw new IllegalArgumentException("Predicate already has component of type: '" + p_331861_ + "'");
                }
            }

            this.expectedComponents.add(new TypedDataComponent<>(p_331861_, p_330813_));
            return this;
        }

        public DataComponentPredicate build() {
            return new DataComponentPredicate(List.copyOf(this.expectedComponents));
        }
    }
}
