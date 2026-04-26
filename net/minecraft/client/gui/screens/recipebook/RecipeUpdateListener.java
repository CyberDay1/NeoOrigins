package net.minecraft.client.gui.screens.recipebook;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface RecipeUpdateListener {
    void recipesUpdated();

    RecipeBookComponent getRecipeBookComponent();
}
