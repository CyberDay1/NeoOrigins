package net.minecraft.data.recipes;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;

public class SpecialRecipeBuilder {
    private final Function<CraftingBookCategory, Recipe<?>> factory;

    public SpecialRecipeBuilder(Function<CraftingBookCategory, Recipe<?>> p_312708_) {
        this.factory = p_312708_;
    }

    public static SpecialRecipeBuilder special(Function<CraftingBookCategory, Recipe<?>> p_312084_) {
        return new SpecialRecipeBuilder(p_312084_);
    }

    public void save(RecipeOutput p_301307_, String p_126361_) {
        this.save(p_301307_, ResourceLocation.parse(p_126361_));
    }

    public void save(RecipeOutput p_301123_, ResourceLocation p_301214_) {
        p_301123_.accept(p_301214_, this.factory.apply(CraftingBookCategory.MISC), null);
    }
}
