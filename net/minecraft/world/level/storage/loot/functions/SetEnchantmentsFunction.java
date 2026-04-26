package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetEnchantmentsFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetEnchantmentsFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_344693_ -> commonFields(p_344693_)
                .and(
                    p_344693_.group(
                        Codec.unboundedMap(Enchantment.CODEC, NumberProviders.CODEC)
                            .optionalFieldOf("enchantments", Map.of())
                            .forGetter(p_298120_ -> p_298120_.enchantments),
                        Codec.BOOL.fieldOf("add").orElse(false).forGetter(p_298121_ -> p_298121_.add)
                    )
                )
                .apply(p_344693_, SetEnchantmentsFunction::new)
    );
    private final Map<Holder<Enchantment>, NumberProvider> enchantments;
    private final boolean add;

    SetEnchantmentsFunction(List<LootItemCondition> p_299192_, Map<Holder<Enchantment>, NumberProvider> p_165338_, boolean p_165339_) {
        super(p_299192_);
        this.enchantments = Map.copyOf(p_165338_);
        this.add = p_165339_;
    }

    @Override
    public LootItemFunctionType<SetEnchantmentsFunction> getType() {
        return LootItemFunctions.SET_ENCHANTMENTS;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.enchantments.values().stream().flatMap(p_279081_ -> p_279081_.getReferencedContextParams().stream()).collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack p_165346_, LootContext p_165347_) {
        if (p_165346_.is(Items.BOOK)) {
            p_165346_ = p_165346_.transmuteCopy(Items.ENCHANTED_BOOK);
            p_165346_.set(DataComponents.STORED_ENCHANTMENTS, p_165346_.remove(DataComponents.ENCHANTMENTS));
        }

        EnchantmentHelper.updateEnchantments(
            p_165346_,
            p_344695_ -> {
                if (this.add) {
                    this.enchantments
                        .forEach(
                            (p_344698_, p_344699_) -> p_344695_.set(
                                    (Holder<Enchantment>)p_344698_,
                                    Mth.clamp(p_344695_.getLevel((Holder<Enchantment>)p_344698_) + p_344699_.getInt(p_165347_), 0, 255)
                                )
                        );
                } else {
                    this.enchantments
                        .forEach((p_344702_, p_344703_) -> p_344695_.set((Holder<Enchantment>)p_344702_, Mth.clamp(p_344703_.getInt(p_165347_), 0, 255)));
                }
            }
        );
        return p_165346_;
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetEnchantmentsFunction.Builder> {
        private final ImmutableMap.Builder<Holder<Enchantment>, NumberProvider> enchantments = ImmutableMap.builder();
        private final boolean add;

        public Builder() {
            this(false);
        }

        public Builder(boolean p_165372_) {
            this.add = p_165372_;
        }

        protected SetEnchantmentsFunction.Builder getThis() {
            return this;
        }

        public SetEnchantmentsFunction.Builder withEnchantment(Holder<Enchantment> p_346114_, NumberProvider p_165376_) {
            this.enchantments.put(p_346114_, p_165376_);
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetEnchantmentsFunction(this.getConditions(), this.enchantments.build(), this.add);
        }
    }
}
