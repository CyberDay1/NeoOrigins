package net.minecraft.world.level.storage.loot.providers.number;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record EnchantmentLevelProvider(LevelBasedValue amount) implements NumberProvider {
    public static final MapCodec<EnchantmentLevelProvider> CODEC = RecordCodecBuilder.mapCodec(
        p_345879_ -> p_345879_.group(LevelBasedValue.CODEC.fieldOf("amount").forGetter(EnchantmentLevelProvider::amount))
                .apply(p_345879_, EnchantmentLevelProvider::new)
    );

    @Override
    public float getFloat(LootContext p_344791_) {
        int i = p_344791_.getParam(LootContextParams.ENCHANTMENT_LEVEL);
        return this.amount.calculate(i);
    }

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.ENCHANTMENT_LEVEL;
    }

    public static EnchantmentLevelProvider forEnchantmentLevel(LevelBasedValue p_345320_) {
        return new EnchantmentLevelProvider(p_345320_);
    }
}
