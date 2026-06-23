package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.ItemUsageGatePower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code item_usage_gate}'s {@code prevent_use} genuinely stop a totem of
 * undying from saving the holder.
 *
 * <p>Vanilla consumes the totem inside {@code LivingEntity.checkTotemDeathProtection},
 * which runs BEFORE {@code LivingDeathEvent} — so a death-event hook can't block
 * it. This injects at HEAD of that method and, when the dying player holds a gate
 * power that forbids USING the totem in the hand holding it, returns {@code false}
 * (no protection) before the totem is consumed: the player dies and keeps the
 * totem.
 *
 * <p>The decision is routed through the SAME {@link ItemUsageGatePower#blocksUse}
 * helper the equip/use handlers use, so the totem path can't drift from the rest
 * of the gate. Only {@link ServerPlayer} holders are checked (the power's
 * condition DSL targets players); other entities fall through to vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemGateMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void neoorigins$itemUsageGateBlocksTotem(
            net.minecraft.world.damagesource.DamageSource damageSource,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer sp)) return;
        // Scan each hand vanilla would pull a totem from; if a gate forbids using
        // the stack in that hand, deny protection and stop scanning.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = sp.getItemInHand(hand);
            if (held.isEmpty()) continue;
            final boolean[] blocked = {false};
            ActiveOriginService.forEachOfType(sp, ItemUsageGatePower.class, cfg -> {
                if (ItemUsageGatePower.blocksUse(sp, held, hand, cfg)) blocked[0] = true;
            });
            if (blocked[0]) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
