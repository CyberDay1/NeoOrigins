package net.minecraft.world.item.enchantment.providers;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;

public interface EnchantmentProviderTypes {
    static MapCodec<? extends EnchantmentProvider> bootstrap(Registry<MapCodec<? extends EnchantmentProvider>> p_346162_) {
        Registry.register(p_346162_, "by_cost", EnchantmentsByCost.CODEC);
        Registry.register(p_346162_, "by_cost_with_difficulty", EnchantmentsByCostWithDifficulty.CODEC);
        return Registry.register(p_346162_, "single", SingleEnchantment.CODEC);
    }
}
