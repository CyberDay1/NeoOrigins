package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Set;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public record ValueCheckCondition(NumberProvider provider, IntRange range) implements LootItemCondition {
    public static final MapCodec<ValueCheckCondition> CODEC = RecordCodecBuilder.mapCodec(
        p_298196_ -> p_298196_.group(
                    NumberProviders.CODEC.fieldOf("value").forGetter(ValueCheckCondition::provider),
                    IntRange.CODEC.fieldOf("range").forGetter(ValueCheckCondition::range)
                )
                .apply(p_298196_, ValueCheckCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.VALUE_CHECK;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Sets.union(this.provider.getReferencedContextParams(), this.range.getReferencedContextParams());
    }

    public boolean test(LootContext p_165527_) {
        return this.range.test(p_165527_, this.provider.getInt(p_165527_));
    }

    public static LootItemCondition.Builder hasValue(NumberProvider p_165529_, IntRange p_165530_) {
        return () -> new ValueCheckCondition(p_165529_, p_165530_);
    }
}
