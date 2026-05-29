package com.cyberday1.neoorigins.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MOD_CRAFT_AMOUNT — scale the count of the assembled crafting result.
 *
 * <p>Targets the static {@code CraftingMenu.slotChangedCraftingGrid}, which is
 * invoked by both {@code CraftingMenu.slotsChanged} (3x3 standalone table) and
 * {@code InventoryMenu.slotsChanged} (2x2 inventory grid), so a single inject
 * covers all vanilla crafting flows. In 26.1 the 2nd parameter is
 * {@link net.minecraft.server.level.ServerLevel} (it became server-only when
 * recipe resolution moved server-side).
 *
 * <p>Runs at RETURN, after vanilla has populated the result slot. Mutates the
 * result stack in place so the later {@code broadcastChanges} syncs the new
 * count to the client; we never call {@code setItem} to avoid a re-entrant grid
 * recompute. Because the target is now server-only, no client-side guard is
 * needed, but the {@link net.minecraft.server.level.ServerPlayer} cast keeps it
 * explicit.
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuCraftAmountMixin {

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
    private static void neoorigins$modifyCraftAmount(
        net.minecraft.world.inventory.AbstractContainerMenu menu,
        net.minecraft.server.level.ServerLevel level,
        Player player,
        net.minecraft.world.inventory.CraftingContainer craftSlots,
        net.minecraft.world.inventory.ResultContainer resultSlots,
        net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> last,
        CallbackInfo ci
    ) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        net.minecraft.world.item.ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) return;
        float scaled = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_CRAFT_AMOUNT,
            result, result.getCount());
        if (!Float.isFinite(scaled)) return;
        int newCount = Math.max(1, Math.min(result.getMaxStackSize(), Math.round(scaled)));
        if (newCount != result.getCount()) {
            result.setCount(newCount);
        }
    }
}
