package com.cyberday1.neoorigins.mixin.recipe;

import com.cyberday1.neoorigins.recipe.OriginCraftingContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plants the crafting player into {@link OriginCraftingContext} for the
 * duration of vanilla's {@code CraftingMenu.slotChangedCraftingGrid} call.
 * That static method is invoked by both {@code CraftingMenu.slotsChanged}
 * (3x3 standalone table) and {@code InventoryMenu.slotsChanged} (2x2 inventory
 * grid via {@code CraftingMenu.slotChangedCraftingGrid(...)} dispatch), so a
 * single mixin covers all vanilla crafting flows.
 *
 * <p>The recipe-book autofill path eventually routes through here too — when
 * a recipe is picked from the book, {@code finishPlacingRecipe} replays the
 * grid which calls back into {@code slotChangedCraftingGrid}.
 *
 * <p>HEAD push + RETURN pop is enough because the entire match-and-assemble
 * cycle (including the {@code Recipe.matches} call where {@link com.cyberday1.neoorigins.recipe.OriginGatedRecipe}
 * reads the context) runs synchronously inside this method.
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuOriginContextMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private Player player;

    /**
     * Also wrap {@code recipeMatches} — that's the predicate the recipe-book
     * autofill path consults before {@code finishPlacingRecipe} runs. Without
     * this, a gated recipe whose grid contents would otherwise match would be
     * highlighted as autofill-able in the book even when the player fails the
     * gate; the autofill click would then succeed-then-clear awkwardly.
     */
    @Inject(method = "recipeMatches", at = @At("HEAD"), remap = true)
    private void neoorigins$pushOnRecipeMatchesHead(
        net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir
    ) {
        OriginCraftingContext.push(this.player);
    }

    @Inject(method = "recipeMatches", at = @At("RETURN"), remap = true)
    private void neoorigins$popOnRecipeMatchesReturn(
        net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir
    ) {
        OriginCraftingContext.pop();
    }

    @Inject(
        method = "slotChangedCraftingGrid("
            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
            + "Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/inventory/CraftingContainer;"
            + "Lnet/minecraft/world/inventory/ResultContainer;"
            + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
            + ")V",
        at = @At("HEAD"),
        remap = true
    )
    private static void neoorigins$pushPlayer(
        net.minecraft.world.inventory.AbstractContainerMenu menu,
        net.minecraft.world.level.Level level,
        Player player,
        net.minecraft.world.inventory.CraftingContainer craftSlots,
        net.minecraft.world.inventory.ResultContainer resultSlots,
        net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> last,
        CallbackInfo ci
    ) {
        OriginCraftingContext.push(player);
    }

    @Inject(
        method = "slotChangedCraftingGrid("
            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
            + "Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/inventory/CraftingContainer;"
            + "Lnet/minecraft/world/inventory/ResultContainer;"
            + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
            + ")V",
        at = @At("RETURN"),
        remap = true
    )
    private static void neoorigins$popPlayer(
        net.minecraft.world.inventory.AbstractContainerMenu menu,
        net.minecraft.world.level.Level level,
        Player player,
        net.minecraft.world.inventory.CraftingContainer craftSlots,
        net.minecraft.world.inventory.ResultContainer resultSlots,
        net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> last,
        CallbackInfo ci
    ) {
        OriginCraftingContext.pop();
    }
}
