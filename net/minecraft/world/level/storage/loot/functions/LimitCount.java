package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LimitCount extends LootItemConditionalFunction {
    public static final MapCodec<LimitCount> CODEC = RecordCodecBuilder.mapCodec(
        p_298095_ -> commonFields(p_298095_).and(IntRange.CODEC.fieldOf("limit").forGetter(p_298094_ -> p_298094_.limiter)).apply(p_298095_, LimitCount::new)
    );
    private final IntRange limiter;

    private LimitCount(List<LootItemCondition> p_299132_, IntRange p_165214_) {
        super(p_299132_);
        this.limiter = p_165214_;
    }

    @Override
    public LootItemFunctionType<LimitCount> getType() {
        return LootItemFunctions.LIMIT_COUNT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.limiter.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack p_80644_, LootContext p_80645_) {
        int i = this.limiter.clamp(p_80645_, p_80644_.getCount());
        p_80644_.setCount(i);
        return p_80644_;
    }

    public static LootItemConditionalFunction.Builder<?> limitCount(IntRange p_165216_) {
        return simpleBuilder(p_298093_ -> new LimitCount(p_298093_, p_165216_));
    }
}
