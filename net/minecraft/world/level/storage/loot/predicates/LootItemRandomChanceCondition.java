package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public record LootItemRandomChanceCondition(NumberProvider chance) implements LootItemCondition {
    public static final MapCodec<LootItemRandomChanceCondition> CODEC = RecordCodecBuilder.mapCodec(
        p_344719_ -> p_344719_.group(NumberProviders.CODEC.fieldOf("chance").forGetter(LootItemRandomChanceCondition::chance))
                .apply(p_344719_, LootItemRandomChanceCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.RANDOM_CHANCE;
    }

    public boolean test(LootContext p_81930_) {
        float f = this.chance.getFloat(p_81930_);
        return p_81930_.getRandom().nextFloat() < f;
    }

    public static LootItemCondition.Builder randomChance(float p_81928_) {
        return () -> new LootItemRandomChanceCondition(ConstantValue.exactly(p_81928_));
    }

    public static LootItemCondition.Builder randomChance(NumberProvider p_345741_) {
        return () -> new LootItemRandomChanceCondition(p_345741_);
    }
}
