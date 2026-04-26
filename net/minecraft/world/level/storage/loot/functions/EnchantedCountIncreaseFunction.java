package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class EnchantedCountIncreaseFunction extends LootItemConditionalFunction {
    public static final int NO_LIMIT = 0;
    public static final MapCodec<EnchantedCountIncreaseFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_346073_ -> commonFields(p_346073_)
                .and(
                    p_346073_.group(
                        Enchantment.CODEC.fieldOf("enchantment").forGetter(p_346362_ -> p_346362_.enchantment),
                        NumberProviders.CODEC.fieldOf("count").forGetter(p_345798_ -> p_345798_.value),
                        Codec.INT.optionalFieldOf("limit", Integer.valueOf(0)).forGetter(p_344886_ -> p_344886_.limit)
                    )
                )
                .apply(p_346073_, EnchantedCountIncreaseFunction::new)
    );
    private final Holder<Enchantment> enchantment;
    private final NumberProvider value;
    private final int limit;

    EnchantedCountIncreaseFunction(List<LootItemCondition> p_344944_, Holder<Enchantment> p_345194_, NumberProvider p_344806_, int p_345432_) {
        super(p_344944_);
        this.enchantment = p_345194_;
        this.value = p_344806_;
        this.limit = p_345432_;
    }

    @Override
    public LootItemFunctionType<EnchantedCountIncreaseFunction> getType() {
        return LootItemFunctions.ENCHANTED_COUNT_INCREASE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Sets.union(ImmutableSet.of(LootContextParams.ATTACKING_ENTITY), this.value.getReferencedContextParams());
    }

    private boolean hasLimit() {
        return this.limit > 0;
    }

    @Override
    public ItemStack run(ItemStack p_345285_, LootContext p_345560_) {
        Entity entity = p_345560_.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        if (entity instanceof LivingEntity livingentity) {
            int i = EnchantmentHelper.getEnchantmentLevel(this.enchantment, livingentity);
            if (i == 0) {
                return p_345285_;
            }

            float f = (float)i * this.value.getFloat(p_345560_);
            p_345285_.grow(Math.round(f));
            if (this.hasLimit()) {
                p_345285_.limitSize(this.limit);
            }
        }

        return p_345285_;
    }

    public static EnchantedCountIncreaseFunction.Builder lootingMultiplier(HolderLookup.Provider p_344982_, NumberProvider p_345939_) {
        HolderLookup.RegistryLookup<Enchantment> registrylookup = p_344982_.lookupOrThrow(Registries.ENCHANTMENT);
        return new EnchantedCountIncreaseFunction.Builder(registrylookup.getOrThrow(Enchantments.LOOTING), p_345939_);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<EnchantedCountIncreaseFunction.Builder> {
        private final Holder<Enchantment> enchantment;
        private final NumberProvider count;
        private int limit = 0;

        public Builder(Holder<Enchantment> p_345337_, NumberProvider p_345112_) {
            this.enchantment = p_345337_;
            this.count = p_345112_;
        }

        protected EnchantedCountIncreaseFunction.Builder getThis() {
            return this;
        }

        public EnchantedCountIncreaseFunction.Builder setLimit(int p_344833_) {
            this.limit = p_344833_;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new EnchantedCountIncreaseFunction(this.getConditions(), this.enchantment, this.count, this.limit);
        }
    }
}
