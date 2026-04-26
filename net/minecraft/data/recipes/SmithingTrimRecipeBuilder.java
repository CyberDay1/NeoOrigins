package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

public class SmithingTrimRecipeBuilder {
    private final RecipeCategory category;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

    public SmithingTrimRecipeBuilder(RecipeCategory p_267007_, Ingredient p_266712_, Ingredient p_267018_, Ingredient p_267264_) {
        this.category = p_267007_;
        this.template = p_266712_;
        this.base = p_267018_;
        this.addition = p_267264_;
    }

    public static SmithingTrimRecipeBuilder smithingTrim(Ingredient p_266812_, Ingredient p_266843_, Ingredient p_267309_, RecipeCategory p_267269_) {
        return new SmithingTrimRecipeBuilder(p_267269_, p_266812_, p_266843_, p_267309_);
    }

    public SmithingTrimRecipeBuilder unlocks(String p_266882_, Criterion<?> p_301261_) {
        this.criteria.put(p_266882_, p_301261_);
        return this;
    }

    public void save(RecipeOutput p_301110_, ResourceLocation p_266718_) {
        this.ensureValid(p_266718_);
        Advancement.Builder advancement$builder = p_301110_.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(p_266718_))
            .rewards(AdvancementRewards.Builder.recipe(p_266718_))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(advancement$builder::addCriterion);
        SmithingTrimRecipe smithingtrimrecipe = new SmithingTrimRecipe(this.template, this.base, this.addition);
        p_301110_.accept(p_266718_, smithingtrimrecipe, advancement$builder.build(p_266718_.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceLocation p_267040_) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + p_267040_);
        }
    }
}
