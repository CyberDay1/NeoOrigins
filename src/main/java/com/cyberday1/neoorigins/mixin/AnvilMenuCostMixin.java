package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MOD_ANVIL_COST — per-player adjustment of the anvil level cost.
 *
 * <p>The previous implementation listened to NeoForge's {@code AnvilUpdateEvent}
 * and called {@code event.setCost(...)}. That is a documented no-op for vanilla
 * recipes: the event fires <em>before</em> vanilla computes anything, and unless
 * a listener also sets a custom <em>output</em>, {@code CommonHooks.onAnvilChange}
 * discards the event's cost and lets vanilla recompute from scratch (tester
 * report 2026-06-12: {@code mod_anvil_cost} powers did nothing). The only hook
 * that sees the final vanilla-computed cost is the menu itself, hence this mixin.
 *
 * <p>Injects at RETURN of {@link AnvilMenu#createResult()} — after every code
 * path (vanilla compute, event-supplied output, early aborts) has finished
 * writing the {@code cost} DataSlot — and rescales it through the
 * MOD_ANVIL_COST modifier chain. The DataSlot is synced to the client, so the
 * discounted value is both displayed and charged on take. Runs server-side
 * only; the client's locally-computed value is overwritten by the sync.
 *
 * <p>Known limitation: vanilla's "Too Expensive!" check (cost ≥ 40) runs before
 * this inject, so it is evaluated against the <em>undiscounted</em> cost.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuCostMixin extends ItemCombinerMenu {

    @Shadow @Final private DataSlot cost;

    private AnvilMenuCostMixin(MenuType<?> type, int containerId,
                               Inventory playerInventory, ContainerLevelAccess access) {
        super(type, containerId, playerInventory, access);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void neoorigins$applyAnvilCostModifier(CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayer sp)) return;
        int current = this.cost.get();
        if (current <= 0) return;
        float scaled = EventPowerIndex.dispatchModifier(sp,
            EventPowerIndex.Event.MOD_ANVIL_COST, this, current);
        if (!Float.isFinite(scaled)) return;
        int desired = Math.max(1, Math.round(scaled));
        if (desired != current) {
            this.cost.set(desired);
        }
    }
}
