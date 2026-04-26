package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetComponentsFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetComponentsFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_331915_ -> commonFields(p_331915_)
                .and(DataComponentPatch.CODEC.fieldOf("components").forGetter(p_331262_ -> p_331262_.components))
                .apply(p_331915_, SetComponentsFunction::new)
    );
    private final DataComponentPatch components;

    private SetComponentsFunction(List<LootItemCondition> p_330669_, DataComponentPatch p_330819_) {
        super(p_330669_);
        this.components = p_330819_;
    }

    @Override
    public LootItemFunctionType<SetComponentsFunction> getType() {
        return LootItemFunctions.SET_COMPONENTS;
    }

    @Override
    public ItemStack run(ItemStack p_330211_, LootContext p_331318_) {
        p_330211_.applyComponentsAndValidate(this.components);
        return p_330211_;
    }

    public static <T> LootItemConditionalFunction.Builder<?> setComponent(DataComponentType<T> p_331454_, T p_331398_) {
        return simpleBuilder(p_331753_ -> new SetComponentsFunction(p_331753_, DataComponentPatch.builder().set(p_331454_, p_331398_).build()));
    }
}
