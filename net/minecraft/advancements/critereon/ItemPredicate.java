package net.minecraft.advancements.critereon;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemPredicate(
    Optional<HolderSet<Item>> items, MinMaxBounds.Ints count, DataComponentPredicate components, Map<ItemSubPredicate.Type<?>, ItemSubPredicate> subPredicates
) implements Predicate<ItemStack> {
    public static final Codec<ItemPredicate> CODEC = RecordCodecBuilder.create(
        p_337371_ -> p_337371_.group(
                    RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(ItemPredicate::count),
                    DataComponentPredicate.CODEC.optionalFieldOf("components", DataComponentPredicate.EMPTY).forGetter(ItemPredicate::components),
                    ItemSubPredicate.CODEC.optionalFieldOf("predicates", Map.of()).forGetter(ItemPredicate::subPredicates)
                )
                .apply(p_337371_, ItemPredicate::new)
    );

    public boolean test(ItemStack p_45050_) {
        if (this.items.isPresent() && !p_45050_.is(this.items.get())) {
            return false;
        } else if (!this.count.matches(p_45050_.getCount())) {
            return false;
        } else if (!this.components.test((DataComponentHolder)p_45050_)) {
            return false;
        } else {
            for (ItemSubPredicate itemsubpredicate : this.subPredicates.values()) {
                if (!itemsubpredicate.matches(p_45050_)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static class Builder {
        private Optional<HolderSet<Item>> items = Optional.empty();
        private MinMaxBounds.Ints count = MinMaxBounds.Ints.ANY;
        private DataComponentPredicate components = DataComponentPredicate.EMPTY;
        private final ImmutableMap.Builder<ItemSubPredicate.Type<?>, ItemSubPredicate> subPredicates = ImmutableMap.builder();

        private Builder() {
        }

        public static ItemPredicate.Builder item() {
            return new ItemPredicate.Builder();
        }

        public ItemPredicate.Builder of(ItemLike... p_151446_) {
            this.items = Optional.of(HolderSet.direct(p_298756_ -> p_298756_.asItem().builtInRegistryHolder(), p_151446_));
            return this;
        }

        public ItemPredicate.Builder of(TagKey<Item> p_204146_) {
            this.items = Optional.of(BuiltInRegistries.ITEM.getOrCreateTag(p_204146_));
            return this;
        }

        public ItemPredicate.Builder withCount(MinMaxBounds.Ints p_151444_) {
            this.count = p_151444_;
            return this;
        }

        public <T extends ItemSubPredicate> ItemPredicate.Builder withSubPredicate(ItemSubPredicate.Type<T> p_333844_, T p_334018_) {
            this.subPredicates.put(p_333844_, p_334018_);
            return this;
        }

        public ItemPredicate.Builder hasComponents(DataComponentPredicate p_331791_) {
            this.components = p_331791_;
            return this;
        }

        public ItemPredicate build() {
            return new ItemPredicate(this.items, this.count, this.components, this.subPredicates.build());
        }
    }
}
