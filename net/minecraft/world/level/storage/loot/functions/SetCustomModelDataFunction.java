package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetCustomModelDataFunction extends LootItemConditionalFunction {
    static final MapCodec<SetCustomModelDataFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_341379_ -> commonFields(p_341379_)
                .and(NumberProviders.CODEC.fieldOf("value").forGetter(p_341219_ -> p_341219_.valueProvider))
                .apply(p_341379_, SetCustomModelDataFunction::new)
    );
    private final NumberProvider valueProvider;

    public SetCustomModelDataFunction(List<LootItemCondition> p_340822_, NumberProvider p_340847_) {
        super(p_340822_);
        this.valueProvider = p_340847_;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.valueProvider.getReferencedContextParams();
    }

    @Override
    public LootItemFunctionType<SetCustomModelDataFunction> getType() {
        return LootItemFunctions.SET_CUSTOM_MODEL_DATA;
    }

    @Override
    public ItemStack run(ItemStack p_341195_, LootContext p_341335_) {
        p_341195_.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(this.valueProvider.getInt(p_341335_)));
        return p_341195_;
    }
}
