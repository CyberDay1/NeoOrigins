package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record SetValue(LevelBasedValue value) implements EnchantmentValueEffect {
    public static final MapCodec<SetValue> CODEC = RecordCodecBuilder.mapCodec(
        p_345396_ -> p_345396_.group(LevelBasedValue.CODEC.fieldOf("value").forGetter(SetValue::value)).apply(p_345396_, SetValue::new)
    );

    @Override
    public float process(int p_344906_, RandomSource p_345151_, float p_345190_) {
        return this.value.calculate(p_344906_);
    }

    @Override
    public MapCodec<SetValue> codec() {
        return CODEC;
    }
}
