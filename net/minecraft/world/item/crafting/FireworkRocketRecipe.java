package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;

public class FireworkRocketRecipe extends CustomRecipe {
    private static final Ingredient PAPER_INGREDIENT = Ingredient.of(Items.PAPER);
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);
    private static final Ingredient STAR_INGREDIENT = Ingredient.of(Items.FIREWORK_STAR);

    public FireworkRocketRecipe(CraftingBookCategory p_250134_) {
        super(p_250134_);
    }

    public boolean matches(CraftingInput p_345559_, Level p_43855_) {
        boolean flag = false;
        int i = 0;

        for (int j = 0; j < p_345559_.size(); j++) {
            ItemStack itemstack = p_345559_.getItem(j);
            if (!itemstack.isEmpty()) {
                if (PAPER_INGREDIENT.test(itemstack)) {
                    if (flag) {
                        return false;
                    }

                    flag = true;
                } else if (GUNPOWDER_INGREDIENT.test(itemstack)) {
                    if (++i > 3) {
                        return false;
                    }
                } else if (!STAR_INGREDIENT.test(itemstack)) {
                    return false;
                }
            }
        }

        return flag && i >= 1;
    }

    public ItemStack assemble(CraftingInput p_345921_, HolderLookup.Provider p_335560_) {
        List<FireworkExplosion> list = new ArrayList<>();
        int i = 0;

        for (int j = 0; j < p_345921_.size(); j++) {
            ItemStack itemstack = p_345921_.getItem(j);
            if (!itemstack.isEmpty()) {
                if (GUNPOWDER_INGREDIENT.test(itemstack)) {
                    i++;
                } else if (STAR_INGREDIENT.test(itemstack)) {
                    FireworkExplosion fireworkexplosion = itemstack.get(DataComponents.FIREWORK_EXPLOSION);
                    if (fireworkexplosion != null) {
                        list.add(fireworkexplosion);
                    }
                }
            }
        }

        ItemStack itemstack1 = new ItemStack(Items.FIREWORK_ROCKET, 3);
        itemstack1.set(DataComponents.FIREWORKS, new Fireworks(i, list));
        return itemstack1;
    }

    @Override
    public boolean canCraftInDimensions(int p_43844_, int p_43845_) {
        return p_43844_ * p_43845_ >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider p_335481_) {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.FIREWORK_ROCKET;
    }
}
