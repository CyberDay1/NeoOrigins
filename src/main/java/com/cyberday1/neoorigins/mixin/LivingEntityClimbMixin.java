package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.WallClimbingPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code LivingEntity.onClimbable()} return {@code true} when the entity is a
 * {@link ServerPlayer} with a granted {@link WallClimbingPower} AND is pressed
 * against a wall while airborne.
 *
 * <p>Enabling vanilla climbing via {@code onClimbable} causes the server to apply
 * ladder/vine physics — including jump-to-ascend and grip-to-slow-fall — in
 * lockstep with the tick handler. In singleplayer the integrated server drives
 * LocalPlayer position correction so the player visibly climbs with no
 * rubber-banding. On dedicated servers the client may briefly predict falling
 * before the next position packet corrects it; this is accepted as a 1.21.1
 * limitation (2.0 adds a synced capability-tag system that removes the gap).
 *
 * <p>Ported from 2.0-dev's {@code LivingEntityClimbMixin}, adapted to use the
 * 1.21.1 {@link ActiveOriginService} directly instead of the 2.0 capability-tag
 * subsystem.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void neoorigins$wallClimbPower(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        // Only hijack the return when the entity is actually pressed against a wall —
        // otherwise ordinary climbing on ladders and vines is unchanged.
        if (!self.horizontalCollision) return;
        if (self.onGround()) return;
        if (!ActiveOriginService.has(sp, WallClimbingPower.class, cfg -> true)) return;
        cir.setReturnValue(true);
    }
}
