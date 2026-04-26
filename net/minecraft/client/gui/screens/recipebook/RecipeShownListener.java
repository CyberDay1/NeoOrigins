package net.minecraft.client.gui.screens.recipebook;

import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface RecipeShownListener {
    void recipesShown(List<RecipeHolder<?>> p_100518_);
}
