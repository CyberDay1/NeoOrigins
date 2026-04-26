package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record MultiplyValue(LevelBasedValue factor) implements EnchantmentValueEffect {
    public static final MapCodec<MultiplyValue> CODEC = RecordCodecBuilder.mapCodec(
        p_344723_ -> p_344723_.group(LevelBasedValue.CODEC.fieldOf("factor").forGetter(MultiplyValue::factor)).apply(p_344723_, MultiplyValue::new)
    );

    @Override
    public float process(int p_345451_, RandomSource p_346156_, float p_345036_) {
        return p_345036_ * this.factor.calculate(p_345451_);
    }

    @Override
    public MapCodec<MultiplyValue> codec() {
        return CODEC;
    }
}
