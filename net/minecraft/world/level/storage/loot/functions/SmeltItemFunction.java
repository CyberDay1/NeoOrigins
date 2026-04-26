package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class SmeltItemFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SmeltItemFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_298746_ -> commonFields(p_298746_).apply(p_298746_, SmeltItemFunction::new)
    );

    private SmeltItemFunction(List<LootItemCondition> p_299162_) {
        super(p_299162_);
    }

    @Override
    public LootItemFunctionType<SmeltItemFunction> getType() {
        return LootItemFunctions.FURNACE_SMELT;
    }

    @Override
    public ItemStack run(ItemStack p_81268_, LootContext p_81269_) {
        if (p_81268_.isEmpty()) {
            return p_81268_;
        } else {
            Optional<RecipeHolder<SmeltingRecipe>> optional = p_81269_.getLevel()
                .getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(p_81268_), p_81269_.getLevel());
            if (optional.isPresent()) {
                ItemStack itemstack = optional.get().value().getResultItem(p_81269_.getLevel().registryAccess());
                if (!itemstack.isEmpty()) {
                    return itemstack.copyWithCount(p_81268_.getCount() * itemstack.getCount()); // Forge: Support smelting returning multiple
                }
            }

            LOGGER.warn("Couldn't smelt {} because there is no smelting recipe", p_81268_);
            return p_81268_;
        }
    }

    public static LootItemConditionalFunction.Builder<?> smelted() {
        return simpleBuilder(SmeltItemFunction::new);
    }
}
