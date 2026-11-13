package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.MobsIgnorePlayerPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Warden's brain never fires {@code LivingChangeTargetEvent}, so the
 * {@code mobs_ignore_player} event hook cannot reach it. Every Warden decision
 * about a player (anger, roar and attack target, sniffing, vibrations) goes
 * through {@code canTargetEntity}, so a matching power with
 * {@code include_boss_tier} answers no here instead.
 */
@Mixin(Warden.class)
public abstract class WardenCanTargetMixin {

    @Inject(method = "canTargetEntity", at = @At("RETURN"), cancellable = true)
    private void neoorigins$ignoreHiddenPlayer(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof ServerPlayer sp
                && MobsIgnorePlayerPower.hidesFrom(sp, (Warden) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
