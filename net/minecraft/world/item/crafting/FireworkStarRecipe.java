package net.minecraft.world.item.crafting;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

public class FireworkStarRecipe extends CustomRecipe {
    private static final Ingredient SHAPE_INGREDIENT = Ingredient.of(
        Items.FIRE_CHARGE,
        Items.FEATHER,
        Items.GOLD_NUGGET,
        Items.SKELETON_SKULL,
        Items.WITHER_SKELETON_SKULL,
        Items.CREEPER_HEAD,
        Items.PLAYER_HEAD,
        Items.DRAGON_HEAD,
        Items.ZOMBIE_HEAD,
        Items.PIGLIN_HEAD
    );
    private static final Ingredient TRAIL_INGREDIENT = Ingredient.of(Items.DIAMOND);
    private static final Ingredient TWINKLE_INGREDIENT = Ingredient.of(Items.GLOWSTONE_DUST);
    private static final Map<Item, FireworkExplosion.Shape> SHAPE_BY_ITEM = Util.make(Maps.newHashMap(), p_261449_ -> {
        p_261449_.put(Items.FIRE_CHARGE, FireworkExplosion.Shape.LARGE_BALL);
        p_261449_.put(Items.FEATHER, FireworkExplosion.Shape.BURST);
        p_261449_.put(Items.GOLD_NUGGET, FireworkExplosion.Shape.STAR);
        p_261449_.put(Items.SKELETON_SKULL, FireworkExplosion.Shape.CREEPER);
        p_261449_.put(Items.WITHER_SKELETON_SKULL, FireworkExplosion.Shape.CREEPER);
        p_261449_.put(Items.CREEPER_HEAD, FireworkExplosion.Shape.CREEPER);
        p_261449_.put(Items.PLAYER_HEAD, FireworkExplosion.Shape.CREEPER);
        p_261449_.put(Items.DRAGON_HEAD, FireworkExplosion.Shape.CREEPER);
        p_261449_.put(Items.ZOMBIE_HEAD, FireworkExplosion.Shape.CREEPER);
        p_261449_.put(Items.PIGLIN_HEAD, FireworkExplosion.Shape.CREEPER);
    });
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);

    public FireworkStarRecipe(CraftingBookCategory p_251577_) {
        super(p_251577_);
    }

    public boolean matches(CraftingInput p_344993_, Level p_43896_) {
        boolean flag = false;
        boolean flag1 = false;
        boolean flag2 = false;
        boolean flag3 = false;
        boolean flag4 = false;

        for (int i = 0; i < p_344993_.size(); i++) {
            ItemStack itemstack = p_344993_.getItem(i);
            if (!itemstack.isEmpty()) {
                if (SHAPE_INGREDIENT.test(itemstack)) {
                    if (flag2) {
                        return false;
                    }

                    flag2 = true;
                } else if (TWINKLE_INGREDIENT.test(itemstack)) {
                    if (flag4) {
                        return false;
                    }

                    flag4 = true;
                } else if (TRAIL_INGREDIENT.test(itemstack)) {
                    if (flag3) {
                        return false;
                    }

                    flag3 = true;
                } else if (GUNPOWDER_INGREDIENT.test(itemstack)) {
                    if (flag) {
                        return false;
                    }

                    flag = true;
                } else {
                    if (!(itemstack.getItem() instanceof DyeItem)) {
                        return false;
                    }

                    flag1 = true;
                }
            }
        }

        return flag && flag1;
    }

    public ItemStack assemble(CraftingInput p_346206_, HolderLookup.Provider p_335498_) {
        FireworkExplosion.Shape fireworkexplosion$shape = FireworkExplosion.Shape.SMALL_BALL;
        boolean flag = false;
        boolean flag1 = false;
        IntList intlist = new IntArrayList();

        for (int i = 0; i < p_346206_.size(); i++) {
            ItemStack itemstack = p_346206_.getItem(i);
            if (!itemstack.isEmpty()) {
                if (SHAPE_INGREDIENT.test(itemstack)) {
                    fireworkexplosion$shape = SHAPE_BY_ITEM.get(itemstack.getItem());
                } else if (TWINKLE_INGREDIENT.test(itemstack)) {
                    flag = true;
                } else if (TRAIL_INGREDIENT.test(itemstack)) {
                    flag1 = true;
                } else if (itemstack.getItem() instanceof DyeItem) {
                    intlist.add(((DyeItem)itemstack.getItem()).getDyeColor().getFireworkColor());
                }
            }
        }

        ItemStack itemstack1 = new ItemStack(Items.FIREWORK_STAR);
        itemstack1.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(fireworkexplosion$shape, intlist, IntList.of(), flag1, flag));
        return itemstack1;
    }

    @Override
    public boolean canCraftInDimensions(int p_43885_, int p_43886_) {
        return p_43885_ * p_43886_ >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider p_335423_) {
        return new ItemStack(Items.FIREWORK_STAR);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.FIREWORK_STAR;
    }
}
