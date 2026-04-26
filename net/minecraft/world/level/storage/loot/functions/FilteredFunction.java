package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FilteredFunction extends LootItemConditionalFunction {
    public static final MapCodec<FilteredFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_340904_ -> commonFields(p_340904_)
                .and(
                    p_340904_.group(
                        ItemPredicate.CODEC.fieldOf("item_filter").forGetter(p_341101_ -> p_341101_.filter),
                        LootItemFunctions.ROOT_CODEC.fieldOf("modifier").forGetter(p_340853_ -> p_340853_.modifier)
                    )
                )
                .apply(p_340904_, FilteredFunction::new)
    );
    private final ItemPredicate filter;
    private final LootItemFunction modifier;

    public FilteredFunction(List<LootItemCondition> p_341370_, ItemPredicate p_341210_, LootItemFunction p_340816_) {
        super(p_341370_);
        this.filter = p_341210_;
        this.modifier = p_340816_;
    }

    @Override
    public LootItemFunctionType<FilteredFunction> getType() {
        return LootItemFunctions.FILTERED;
    }

    @Override
    public ItemStack run(ItemStack p_340845_, LootContext p_341349_) {
        return this.filter.test(p_340845_) ? this.modifier.apply(p_340845_, p_341349_) : p_340845_;
    }

    @Override
    public void validate(ValidationContext p_341254_) {
        super.validate(p_341254_);
        this.modifier.validate(p_341254_.forChild(".modifier"));
    }
}
