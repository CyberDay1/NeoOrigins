package net.minecraft.world.inventory;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public interface RecipeCraftingHolder {
    void setRecipeUsed(@Nullable RecipeHolder<?> p_300902_);

    @Nullable
    RecipeHolder<?> getRecipeUsed();

    default void awardUsedRecipes(Player p_300892_, List<ItemStack> p_301308_) {
        RecipeHolder<?> recipeholder = this.getRecipeUsed();
        if (recipeholder != null) {
            p_300892_.triggerRecipeCrafted(recipeholder, p_301308_);
            if (!recipeholder.value().isSpecial()) {
                p_300892_.awardRecipes(Collections.singleton(recipeholder));
                this.setRecipeUsed(null);
            }
        }
    }

    default boolean setRecipeUsed(Level p_300930_, ServerPlayer p_301242_, RecipeHolder<?> p_301296_) {
        if (!p_301296_.value().isSpecial()
            && p_300930_.getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)
            && !p_301242_.getRecipeBook().contains(p_301296_)) {
            return false;
        } else {
            this.setRecipeUsed(p_301296_);
            return true;
        }
    }
}
