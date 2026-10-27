package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.event.CraftingPowerEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cook food bonuses on shift-click out of a furnace, smoker or blast furnace (#137).
 *
 * <p>Vanilla merges the output into the inventory before {@code ItemSmeltedEvent}
 * fires, so the event's mutation never reached the moved food. This moves a boosted
 * copy instead and writes the leftover count back, leaving the stack in the output
 * slot untouched: a boosted output would no longer match the recipe result and the
 * furnace would stop stacking new smelts onto it.
 */
@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceMenuQuickMoveMixin {

    @WrapOperation(
        method = "quickMoveStack",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractFurnaceMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z")
    )
    private boolean neoorigins$moveBoostedFood(AbstractFurnaceMenu self, ItemStack stack, int start, int end,
                                               boolean reverse, Operation<Boolean> original,
                                               @Local(argsOnly = true) Player player) {
        // Only the result slot (index 2 in vanilla's own quickMoveStack).
        if (!(player instanceof ServerPlayer sp) || self.getSlot(2).getItem() != stack) {
            return original.call(self, stack, start, end, reverse);
        }
        ItemStack boosted = stack.copy();
        if (!CraftingPowerEvents.boostSmeltedFood(sp, boosted)) {
            return original.call(self, stack, start, end, reverse);
        }
        boolean moved = original.call(self, boosted, start, end, reverse);
        stack.setCount(boosted.getCount());
        return moved;
    }
}
