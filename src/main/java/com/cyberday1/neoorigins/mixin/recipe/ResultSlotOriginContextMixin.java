package com.cyberday1.neoorigins.mixin.recipe;

import com.cyberday1.neoorigins.recipe.OriginCraftingContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plants the crafting {@link Player} into {@link OriginCraftingContext} for the
 * duration of {@code ResultSlot.onTake} — the moment the player pulls the
 * crafted result out of the result slot (single-craft, shift-click craft-all,
 * and swap-craft all route through here).
 *
 * <p><b>Why this is needed on top of {@link CraftingMenuOriginContextMixin}.</b>
 * {@code ResultSlot.onTake} re-resolves the recipe to compute the leftover
 * ("remaining") items via {@code ServerLevel.recipeAccess().getRecipeFor(...)}:
 *
 * <pre>
 * getRecipeFor(CRAFTING, input, level)
 *     .map(r -> r.value().getRemainingItems(input))
 *     .orElseGet(() -> copyAllInputItems(input));   // fallback = the WHOLE grid
 * </pre>
 *
 * That {@code getRecipeFor} call invokes {@link com.cyberday1.neoorigins.recipe.OriginGatedRecipe#matches}.
 * Unlike {@code slotChangedCraftingGrid} (wrapped by
 * {@link CraftingMenuOriginContextMixin}), {@code onTake} runs <em>outside</em>
 * any push, so {@link OriginCraftingContext#current()} is {@code null} and the
 * gate fails closed — {@code matches} returns {@code false}, {@code getRecipeFor}
 * yields empty, and vanilla falls back to {@code copyAllInputItems(input)}.
 *
 * <p>{@code copyAllInputItems} hands back the ENTIRE input grid as the "remaining
 * items". {@code onTake} then removes one item per slot and, finding the copied
 * stack {@code isSameItemSameComponents} as the just-shrunk grid slot, does
 * {@code replacement.grow(itemStack.getCount())} and writes it back — effectively
 * refunding (and, over a shift-click craft-all loop, snowballing) the consumed
 * ingredients. For the Origins-Classes {@code common_ink} recipe (which consumes
 * {@code irons_spellbooks:arcane_essence}) this regrows the arcane_essence stack
 * every craft until its count overflows the [1;99] ItemStack save codec and the
 * dropped item entity can no longer be persisted (community bug report).
 *
 * <p>Pushing the player here makes the gate resolve for real, so {@code getRecipeFor}
 * returns the {@code OriginGatedRecipe}, whose {@code getRemainingItems} correctly
 * reports no leftovers for these consuming recipes. HEAD push + RETURN pop is safe
 * because the whole take/leftover cycle runs synchronously within {@code onTake}.
 */
@Mixin(ResultSlot.class)
public class ResultSlotOriginContextMixin {

    @Shadow @Final private Player player;

    /**
     * Previous crafting-context player, captured at push time and restored on
     * return. Per-thread so a nested/re-entrant take (should not happen in vanilla,
     * but modded menus reusing {@code ResultSlot} could) doesn't clobber an outer
     * context. Never leaks: the RETURN inject always restores it.
     */
    @Unique
    private static final ThreadLocal<Player> neoorigins$prevContext = new ThreadLocal<>();

    @Inject(method = "onTake", at = @At("HEAD"), remap = true)
    private void neoorigins$pushOnTakeHead(Player player, ItemStack carried, CallbackInfo ci) {
        // Prefer the shadowed slot-owner field; the method arg is the same player
        // in every vanilla path but the field is authoritative.
        Player prev = OriginCraftingContext.push(this.player != null ? this.player : player);
        neoorigins$prevContext.set(prev);
    }

    @Inject(method = "onTake", at = @At("RETURN"), remap = true)
    private void neoorigins$popOnTakeReturn(Player player, ItemStack carried, CallbackInfo ci) {
        OriginCraftingContext.restore(neoorigins$prevContext.get());
        neoorigins$prevContext.remove();
    }
}
