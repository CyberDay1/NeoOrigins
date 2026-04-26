package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

public interface EnchantmentValueEffect {
    Codec<EnchantmentValueEffect> CODEC = BuiltInRegistries.ENCHANTMENT_VALUE_EFFECT_TYPE
        .byNameCodec()
        .dispatch(EnchantmentValueEffect::codec, Function.identity());

    static MapCodec<? extends EnchantmentValueEffect> bootstrap(Registry<MapCodec<? extends EnchantmentValueEffect>> p_345804_) {
        Registry.register(p_345804_, "add", AddValue.CODEC);
        Registry.register(p_345804_, "all_of", AllOf.ValueEffects.CODEC);
        Registry.register(p_345804_, "multiply", MultiplyValue.CODEC);
        Registry.register(p_345804_, "remove_binomial", RemoveBinomial.CODEC);
        return Registry.register(p_345804_, "set", SetValue.CODEC);
    }

    float process(int p_345946_, RandomSource p_345167_, float p_345777_);

    MapCodec<? extends EnchantmentValueEffect> codec();
}
