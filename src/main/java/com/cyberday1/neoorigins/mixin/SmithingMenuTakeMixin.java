package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.event.CraftingPowerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 21.1 fires no event when a player takes the smithing-table output
 * ({@code PlayerEvent.ItemCraftedEvent} only covers crafting grids), so the
 * blacksmith quality power had no way to refresh its absolute durability /
 * attribute snapshots on upgrade — a quality diamond pickaxe upgraded to
 * netherite kept diamond-level durability forever (GitHub #103).
 *
 * <p>Injects at HEAD of {@link SmithingMenu#onTake} — before the input slots
 * shrink — so the handler can compare the output against the pre-upgrade base
 * item when re-deriving quality data.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuTakeMixin extends ItemCombinerMenu {

    private SmithingMenuTakeMixin(MenuType<?> type, int containerId,
                                  Inventory playerInventory, ContainerLevelAccess access) {
        super(type, containerId, playerInventory, access);
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void neoorigins$onSmithingTake(Player player, ItemStack taken, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        CraftingPowerEvents.onSmithingTake(sp,
            this.inputSlots.getItem(SmithingMenu.BASE_SLOT), taken);
    }
}
