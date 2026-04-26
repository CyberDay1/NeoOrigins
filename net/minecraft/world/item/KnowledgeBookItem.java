package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class KnowledgeBookItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public KnowledgeBookItem(Item.Properties p_42822_) {
        super(p_42822_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_42824_, Player p_42825_, InteractionHand p_42826_) {
        ItemStack itemstack = p_42825_.getItemInHand(p_42826_);
        List<ResourceLocation> list = itemstack.getOrDefault(DataComponents.RECIPES, List.of());
        itemstack.consume(1, p_42825_);
        if (list.isEmpty()) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            if (!p_42824_.isClientSide) {
                RecipeManager recipemanager = p_42824_.getServer().getRecipeManager();
                List<RecipeHolder<?>> list1 = new ArrayList<>(list.size());

                for (ResourceLocation resourcelocation : list) {
                    Optional<RecipeHolder<?>> optional = recipemanager.byKey(resourcelocation);
                    if (!optional.isPresent()) {
                        LOGGER.error("Invalid recipe: {}", resourcelocation);
                        return InteractionResultHolder.fail(itemstack);
                    }

                    list1.add(optional.get());
                }

                p_42825_.awardRecipes(list1);
                p_42825_.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResultHolder.sidedSuccess(itemstack, p_42824_.isClientSide());
        }
    }
}
