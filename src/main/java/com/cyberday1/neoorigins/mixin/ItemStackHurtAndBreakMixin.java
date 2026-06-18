package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.PreventItemDamagePower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Cancels durability loss for players holding a {@code neoorigins:prevent_item_damage}
 * power whose filter matches the stack being damaged. All other
 * {@code hurtAndBreak} overloads delegate to this canonical four-arg variant, so
 * a single HEAD-cancellable inject here covers every durability path.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackHurtAndBreakMixin {

    @Inject(
        method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void neoorigins$preventItemDamage(int amount, ServerLevel level, LivingEntity user,
                                              Consumer<Item> onBreak, CallbackInfo ci) {
        if (user instanceof ServerPlayer player
                && PreventItemDamagePower.prevents(player, (ItemStack) (Object) this)) {
            ci.cancel();
        }
    }
}
