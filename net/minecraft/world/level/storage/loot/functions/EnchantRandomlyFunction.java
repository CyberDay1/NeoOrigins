package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class EnchantRandomlyFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<EnchantRandomlyFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_344688_ -> commonFields(p_344688_)
                .and(
                    p_344688_.group(
                        RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("options").forGetter(p_344687_ -> p_344687_.options),
                        Codec.BOOL.optionalFieldOf("only_compatible", Boolean.valueOf(true)).forGetter(p_344689_ -> p_344689_.onlyCompatible)
                    )
                )
                .apply(p_344688_, EnchantRandomlyFunction::new)
    );
    private final Optional<HolderSet<Enchantment>> options;
    private final boolean onlyCompatible;

    EnchantRandomlyFunction(List<LootItemCondition> p_299014_, Optional<HolderSet<Enchantment>> p_298965_, boolean p_344945_) {
        super(p_299014_);
        this.options = p_298965_;
        this.onlyCompatible = p_344945_;
    }

    @Override
    public LootItemFunctionType<EnchantRandomlyFunction> getType() {
        return LootItemFunctions.ENCHANT_RANDOMLY;
    }

    @Override
    public ItemStack run(ItemStack p_80429_, LootContext p_80430_) {
        RandomSource randomsource = p_80430_.getRandom();
        boolean flag = p_80429_.is(Items.BOOK);
        boolean flag1 = !flag && this.onlyCompatible;
        Stream<Holder<Enchantment>> stream = this.options
            .map(HolderSet::stream)
            .orElseGet(() -> p_80430_.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().map(Function.identity()))
            .filter(p_344686_ -> !flag1 || p_80429_.supportsEnchantment(p_344686_)); // Neo: Respect IItemExtension#supportsEnchantment
        List<Holder<Enchantment>> list = stream.toList();
        Optional<Holder<Enchantment>> optional = Util.getRandomSafe(list, randomsource);
        if (optional.isEmpty()) {
            LOGGER.warn("Couldn't find a compatible enchantment for {}", p_80429_);
            return p_80429_;
        } else {
            return enchantItem(p_80429_, optional.get(), randomsource);
        }
    }

    private static ItemStack enchantItem(ItemStack p_230980_, Holder<Enchantment> p_346257_, RandomSource p_230982_) {
        int i = Mth.nextInt(p_230982_, p_346257_.value().getMinLevel(), p_346257_.value().getMaxLevel());
        if (p_230980_.is(Items.BOOK)) {
            p_230980_ = new ItemStack(Items.ENCHANTED_BOOK);
        }

        p_230980_.enchant(p_346257_, i);
        return p_230980_;
    }

    public static EnchantRandomlyFunction.Builder randomEnchantment() {
        return new EnchantRandomlyFunction.Builder();
    }

    public static EnchantRandomlyFunction.Builder randomApplicableEnchantment(HolderLookup.Provider p_346405_) {
        return randomEnchantment().withOneOf(p_346405_.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.ON_RANDOM_LOOT));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<EnchantRandomlyFunction.Builder> {
        private Optional<HolderSet<Enchantment>> options = Optional.empty();
        private boolean onlyCompatible = true;

        protected EnchantRandomlyFunction.Builder getThis() {
            return this;
        }

        public EnchantRandomlyFunction.Builder withEnchantment(Holder<Enchantment> p_346278_) {
            this.options = Optional.of(HolderSet.direct(p_346278_));
            return this;
        }

        public EnchantRandomlyFunction.Builder withOneOf(HolderSet<Enchantment> p_345384_) {
            this.options = Optional.of(p_345384_);
            return this;
        }

        public EnchantRandomlyFunction.Builder allowingIncompatibleEnchantments() {
            this.onlyCompatible = false;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new EnchantRandomlyFunction(this.getConditions(), this.options, this.onlyCompatible);
        }
    }
}
