package net.minecraft.data.recipes;

import javax.annotation.Nullable;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.ItemLike;

public interface RecipeBuilder {
    ResourceLocation ROOT_RECIPE_ADVANCEMENT = ResourceLocation.withDefaultNamespace("recipes/root");

    RecipeBuilder unlockedBy(String p_176496_, Criterion<?> p_301065_);

    RecipeBuilder group(@Nullable String p_176495_);

    Item getResult();

    void save(RecipeOutput p_301032_, ResourceLocation p_176504_);

    default void save(RecipeOutput p_301244_) {
        this.save(p_301244_, getDefaultRecipeId(this.getResult()));
    }

    default void save(RecipeOutput p_301186_, String p_176502_) {
        ResourceLocation resourcelocation = getDefaultRecipeId(this.getResult());
        ResourceLocation resourcelocation1 = ResourceLocation.parse(p_176502_);
        if (resourcelocation1.equals(resourcelocation)) {
            throw new IllegalStateException("Recipe " + p_176502_ + " should remove its 'save' argument as it is equal to default one");
        } else {
            this.save(p_301186_, resourcelocation1);
        }
    }

    static ResourceLocation getDefaultRecipeId(ItemLike p_176494_) {
        return BuiltInRegistries.ITEM.getKey(p_176494_.asItem());
    }

    static CraftingBookCategory determineBookCategory(RecipeCategory p_311837_) {
        return switch (p_311837_) {
            case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
            case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
    }
}
