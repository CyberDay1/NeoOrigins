package net.minecraft.client;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientRecipeBook extends RecipeBook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Map<RecipeBookCategories, List<RecipeCollection>> collectionsByTab = ImmutableMap.of();
    private List<RecipeCollection> allCollections = ImmutableList.of();

    public void setupCollections(Iterable<RecipeHolder<?>> p_266814_, RegistryAccess p_266878_) {
        Map<RecipeBookCategories, List<List<RecipeHolder<?>>>> map = categorizeAndGroupRecipes(p_266814_);
        Map<RecipeBookCategories, List<RecipeCollection>> map1 = Maps.newHashMap();
        Builder<RecipeCollection> builder = ImmutableList.builder();
        map.forEach(
            (p_266602_, p_266603_) -> map1.put(
                    p_266602_,
                    p_266603_.stream()
                        .map(p_266605_ -> new RecipeCollection(p_266878_, (List<RecipeHolder<?>>)p_266605_))
                        .peek(builder::add)
                        .collect(ImmutableList.toImmutableList())
                )
        );
        RecipeBookCategories.AGGREGATE_CATEGORIES
            .forEach(
                (p_90637_, p_90638_) -> map1.put(
                        p_90637_,
                        p_90638_.stream()
                            .flatMap(p_167706_ -> map1.getOrDefault(p_167706_, ImmutableList.of()).stream())
                            .collect(ImmutableList.toImmutableList())
                    )
            );
        this.collectionsByTab = ImmutableMap.copyOf(map1);
        this.allCollections = builder.build();
    }

    private static Map<RecipeBookCategories, List<List<RecipeHolder<?>>>> categorizeAndGroupRecipes(Iterable<RecipeHolder<?>> p_90643_) {
        Map<RecipeBookCategories, List<List<RecipeHolder<?>>>> map = Maps.newHashMap();
        Table<RecipeBookCategories, String, List<RecipeHolder<?>>> table = HashBasedTable.create();

        for (RecipeHolder<?> recipeholder : p_90643_) {
            Recipe<?> recipe = recipeholder.value();
            if (!recipe.isSpecial() && !recipe.isIncomplete()) {
                RecipeBookCategories recipebookcategories = getCategory(recipeholder);
                String s = recipe.getGroup().isEmpty() ? recipeholder.id().toString() : recipe.getGroup(); // FORGE: Group value defaults to the recipe's ID if the recipe's explicit group is empty.
                if (s.isEmpty()) {
                    map.computeIfAbsent(recipebookcategories, p_90645_ -> Lists.newArrayList()).add(ImmutableList.of(recipeholder));
                } else {
                    List<RecipeHolder<?>> list = table.get(recipebookcategories, s);
                    if (list == null) {
                        list = Lists.newArrayList();
                        table.put(recipebookcategories, s, list);
                        map.computeIfAbsent(recipebookcategories, p_90641_ -> Lists.newArrayList()).add(list);
                    }

                    list.add(recipeholder);
                }
            }
        }

        return map;
    }

    private static RecipeBookCategories getCategory(RecipeHolder<?> p_301136_) {
        Recipe<?> recipe = p_301136_.value();
        if (recipe instanceof CraftingRecipe craftingrecipe) {
            return switch (craftingrecipe.category()) {
                case BUILDING -> RecipeBookCategories.CRAFTING_BUILDING_BLOCKS;
                case EQUIPMENT -> RecipeBookCategories.CRAFTING_EQUIPMENT;
                case REDSTONE -> RecipeBookCategories.CRAFTING_REDSTONE;
                case MISC -> RecipeBookCategories.CRAFTING_MISC;
            };
        } else {
            RecipeType<?> recipetype = recipe.getType();
            if (recipe instanceof AbstractCookingRecipe abstractcookingrecipe) {
                CookingBookCategory cookingbookcategory = abstractcookingrecipe.category();
                if (recipetype == RecipeType.SMELTING) {
                    return switch (cookingbookcategory) {
                        case BLOCKS -> RecipeBookCategories.FURNACE_BLOCKS;
                        case FOOD -> RecipeBookCategories.FURNACE_FOOD;
                        case MISC -> RecipeBookCategories.FURNACE_MISC;
                    };
                }

                if (recipetype == RecipeType.BLASTING) {
                    return cookingbookcategory == CookingBookCategory.BLOCKS
                        ? RecipeBookCategories.BLAST_FURNACE_BLOCKS
                        : RecipeBookCategories.BLAST_FURNACE_MISC;
                }

                if (recipetype == RecipeType.SMOKING) {
                    return RecipeBookCategories.SMOKER_FOOD;
                }

                if (recipetype == RecipeType.CAMPFIRE_COOKING) {
                    return RecipeBookCategories.CAMPFIRE;
                }
            }

            if (recipetype == RecipeType.STONECUTTING) {
                return RecipeBookCategories.STONECUTTER;
            } else if (recipetype == RecipeType.SMITHING) {
                return RecipeBookCategories.SMITHING;
            } else {
                RecipeBookCategories categories = net.neoforged.neoforge.client.RecipeBookManager.findCategories((RecipeType) recipetype, p_301136_);
                if (categories != null) return categories;
                LOGGER.warn(
                    "Unknown recipe category: {}/{}",
                    LogUtils.defer(() -> BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())),
                    LogUtils.defer(p_301136_::id)
                );
                return RecipeBookCategories.UNKNOWN;
            }
        }
    }

    public List<RecipeCollection> getCollections() {
        return this.allCollections;
    }

    public List<RecipeCollection> getCollection(RecipeBookCategories p_90624_) {
        return this.collectionsByTab.getOrDefault(p_90624_, Collections.emptyList());
    }
}
