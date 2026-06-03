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
 * duration of vanilla's static {@code CraftingMenu.slotChangedCraftingGrid}
 * call. That method is invoked by both {@code CraftingMenu.slotsChanged} (3x3
 * standalone table) and {@code InventoryMenu.slotsChanged} (2x2 inventory grid),
 * and it is where {@code getRecipeManager().getRecipeFor(...)} — and therefore
 * {@link com.cyberday1.neoorigins.recipe.OriginGatedRecipe#matches} — runs, so a
 * single HEAD/RETURN bracket covers all vanilla crafting flows (including the
 * recipe-book autofill path, which routes back through
 * {@code finishPlacingRecipe -> slotChangedCraftingGrid}).
 *
 * <p>HEAD push + RETURN pop is enough because the entire match-and-assemble
 * cycle runs synchronously inside this method.
 *
 * <p><b>26.1 deltas vs 1.21.1:</b>
 * <ul>
 *   <li>{@code slotChangedCraftingGrid}'s 2nd parameter is now
 *       {@link net.minecraft.server.level.ServerLevel} (recipe resolution moved
 *       server-side), so the method descriptor references {@code ServerLevel}.</li>
 *   <li>{@code CraftingMenu.recipeMatches} no longer exists, so the former
 *       recipe-book-predicate injects are dropped; {@code isSpecial()=true} on
 *       the gated recipe keeps the book from mis-highlighting it.</li>
 *   <li>The MOD_CRAFT_AMOUNT result scaling lives in its own
 *       {@code CraftingMenuCraftAmountMixin}; it is intentionally not duplicated
 *       here.</li>
 * </ul>
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuOriginContextMixin {

    @Inject(
        method = "slotChangedCraftingGrid("
            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
            + "Lnet/minecraft/server/level/ServerLevel;"
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
        net.minecraft.server.level.ServerLevel level,
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
            + "Lnet/minecraft/server/level/ServerLevel;"
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
        net.minecraft.server.level.ServerLevel level,
        Player player,
        net.minecraft.world.inventory.CraftingContainer craftSlots,
        net.minecraft.world.inventory.ResultContainer resultSlots,
        net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> last,
        CallbackInfo ci
    ) {
        OriginCraftingContext.pop();
    }
}
