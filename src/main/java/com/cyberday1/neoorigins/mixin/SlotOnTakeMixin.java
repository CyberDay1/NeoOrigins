package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.event.CraftingPowerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Universal result-slot interceptor for the blacksmith quality power's
 * data-driven {@code intercept_menus} hook.
 *
 * <p>Modded workstations (e.g. Overgeared's smithing anvils) finalize a forged
 * item into a real result {@link Slot} and fire the base {@code Slot#onTake}
 * when the player pulls it — but they emit no {@code PlayerEvent.ItemCraftedEvent}
 * and are not vanilla {@link net.minecraft.world.inventory.SmithingMenu}, so
 * neither the crafting event listener nor {@link SmithingMenuTakeMixin} covers
 * them. Injecting at HEAD of {@code Slot#onTake} catches every take reliably,
 * AFTER the forge is finalized (Overgeared's anonymous {@code SlotItemHandler}
 * result slot calls {@code super.onTake} last).
 *
 * <p>This is a soft dependency: there is no compile-time reference to any modded
 * class. The handler resolves the current menu's registry id at runtime and only
 * acts when it matches an id opted in by an active power's {@code intercept_menus}
 * list, so it is a no-op when the referenced mod is absent.
 *
 * <p>{@code Slot#onTake} runs on EVERY inventory take on both sides, so this
 * bails fast: it returns immediately unless the taker is a {@link ServerPlayer},
 * then defers all further (cheap&rarr;expensive) gating to
 * {@link CraftingPowerEvents#onGenericResultTake}.
 */
@Mixin(Slot.class)
public abstract class SlotOnTakeMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void neoorigins$onGenericResultTake(Player player, ItemStack taken, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        CraftingPowerEvents.onGenericResultTake(sp, (Slot) (Object) this, taken);
    }
}
