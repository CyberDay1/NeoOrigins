package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.EdibleItemPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Returns a vanilla-food eat duration (32 ticks) from
 * {@link ItemStack#getUseDuration(LivingEntity)} when the using entity has a
 * matching {@link EdibleItemPower} for this stack. Without this, the player's
 * {@code startUsingItem(hand)} call sets {@code useItemRemaining} to 0 (the
 * default for non-food items) and the use completes on the same tick — no
 * eating animation.
 *
 * <p>Server-side gate via {@code ServerPlayer} cast: client-side capability
 * lookup would need the full power config which we don't ship to the client.
 * The {@code USING_ITEM} entity flag is synced from the server, so the client
 * still renders the eating animation correctly even though its local
 * {@code useItemRemaining} stays at 0.
 *
 * <p>{@code require = 0} so the mixin no-ops if a future MC version refactors
 * the method shape.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEdibleDurationMixin {

    @Inject(
        method = "getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void neoorigins$edibleItemDuration(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (!(entity instanceof ServerPlayer sp)) return;
        ItemStack self = (ItemStack) (Object) this;
        boolean[] match = {false};
        ActiveOriginService.forEachOfType(sp, EdibleItemPower.class, cfg -> {
            if (match[0]) return;
            if (EdibleItemPower.matches(self, cfg)) match[0] = true;
        });
        if (match[0]) cir.setReturnValue(32);
    }
}
