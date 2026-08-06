package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.BreathOutOfFluidPower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * If player has {@code breath_out_of_fluid} capability, applying
 * turtle helmet effect is done in {@link BreathOutOfFluidPower.Handler#onPlayerTickPost},
 * so it is skipped here.
 */
@Mixin(Player.class)
public abstract class PlayerTurtleHelmetMixin {

    @Inject(method = "turtleHelmetTick", at = @At("HEAD"), cancellable = true)
    private void neoorigins$turtleHelmetTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET)
                && PowerCapabilities.hasActive(player, "dries_out_of_water")) {
            ci.cancel();
        }
    }
}
