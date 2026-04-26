package net.minecraft.client.gui.screens.recipebook;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RecipeCollection {
    private final RegistryAccess registryAccess;
    private final List<RecipeHolder<?>> recipes;
    private final boolean singleResultItem;
    private final Set<RecipeHolder<?>> craftable = Sets.newHashSet();
    private final Set<RecipeHolder<?>> fitsDimensions = Sets.newHashSet();
    private final Set<RecipeHolder<?>> known = Sets.newHashSet();

    public RecipeCollection(RegistryAccess p_266782_, List<RecipeHolder<?>> p_267051_) {
        this.registryAccess = p_266782_;
        this.recipes = ImmutableList.copyOf(p_267051_);
        if (p_267051_.size() <= 1) {
            this.singleResultItem = true;
        } else {
            this.singleResultItem = allRecipesHaveSameResult(p_266782_, p_267051_);
        }
    }

    private static boolean allRecipesHaveSameResult(RegistryAccess p_267210_, List<RecipeHolder<?>> p_100509_) {
        int i = p_100509_.size();
        ItemStack itemstack = p_100509_.get(0).value().getResultItem(p_267210_);

        for (int j = 1; j < i; j++) {
            ItemStack itemstack1 = p_100509_.get(j).value().getResultItem(p_267210_);
            if (!ItemStack.isSameItemSameComponents(itemstack, itemstack1)) {
                return false;
            }
        }

        return true;
    }

    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public boolean hasKnownRecipes() {
        return !this.known.isEmpty();
    }

    public void updateKnownRecipes(RecipeBook p_100500_) {
        for (RecipeHolder<?> recipeholder : this.recipes) {
            if (p_100500_.contains(recipeholder)) {
                this.known.add(recipeholder);
            }
        }
    }

    public void canCraft(StackedContents p_100502_, int p_100503_, int p_100504_, RecipeBook p_100505_) {
        for (RecipeHolder<?> recipeholder : this.recipes) {
            boolean flag = recipeholder.value().canCraftInDimensions(p_100503_, p_100504_) && p_100505_.contains(recipeholder);
            if (flag) {
                this.fitsDimensions.add(recipeholder);
            } else {
                this.fitsDimensions.remove(recipeholder);
            }

            if (flag && p_100502_.canCraft(recipeholder.value(), null)) {
                this.craftable.add(recipeholder);
            } else {
                this.craftable.remove(recipeholder);
            }
        }
    }

    public boolean isCraftable(RecipeHolder<?> p_301167_) {
        return this.craftable.contains(p_301167_);
    }

    public boolean hasCraftable() {
        return !this.craftable.isEmpty();
    }

    public boolean hasFitting() {
        return !this.fitsDimensions.isEmpty();
    }

    public List<RecipeHolder<?>> getRecipes() {
        return this.recipes;
    }

    public List<RecipeHolder<?>> getRecipes(boolean p_100511_) {
        List<RecipeHolder<?>> list = Lists.newArrayList();
        Set<RecipeHolder<?>> set = p_100511_ ? this.craftable : this.fitsDimensions;

        for (RecipeHolder<?> recipeholder : this.recipes) {
            if (set.contains(recipeholder)) {
                list.add(recipeholder);
            }
        }

        return list;
    }

    public List<RecipeHolder<?>> getDisplayRecipes(boolean p_100514_) {
        List<RecipeHolder<?>> list = Lists.newArrayList();

        for (RecipeHolder<?> recipeholder : this.recipes) {
            if (this.fitsDimensions.contains(recipeholder) && this.craftable.contains(recipeholder) == p_100514_) {
                list.add(recipeholder);
            }
        }

        return list;
    }

    public boolean hasSingleResultItem() {
        return this.singleResultItem;
    }
}
