package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows players with the {@code walk_on_water} or {@code walk_on_lava}
 * capabilities to stand on fluid surfaces, using the same mechanic vanilla
 * Striders use for walking on lava.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityWalkOnFluidMixin {

    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoorigins$walkOnFluid(FluidState fluidState, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) return;
        if (fluidState.isEmpty()) return;

        // The fluid surface only becomes a solid collision via this method
        // (LiquidBlock#getCollisionShape -> EntityCollisionContext#canStandOnFluid).
        // The local player predicts its own movement client-side, so this check
        // must also resolve true on the client — otherwise the client runs
        // vanilla swim physics and the player bobs *inside* the fluid while the
        // server alone thinks they're standing on top. Resolve the capability
        // against the side-appropriate source, mirroring LivingEntityAirRefillMixin.
        boolean walkWater;
        boolean walkLava;
        if (player.level().isClientSide) {
            // Only the local player predicts its own movement; other entities'
            // collision contexts must not inherit the local player's capabilities.
            if (!(player instanceof LocalPlayer)) return;
            walkWater = ClientActivePowers.hasCapability("walk_on_water");
            walkLava = ClientActivePowers.hasCapability("walk_on_lava");
        } else if (player instanceof ServerPlayer sp) {
            walkWater = ActiveOriginService.hasCapability(sp, "walk_on_water");
            walkLava = ActiveOriginService.hasCapability(sp, "walk_on_lava");
        } else {
            return;
        }

        // Only walk on the water surface — if the player is fully submerged,
        // let normal swim physics apply so they can dive by looking down.
        if (walkWater && fluidState.is(FluidTags.WATER) && !player.isUnderWater()) {
            cir.setReturnValue(true);
        }
        // Mirror the water branch: gate on eye-level submersion, not mere contact.
        // isInLava() is true the instant any lava enters the AABB (feet on the
        // surface), which flipped the power off and dropped the player in.
        if (walkLava && fluidState.is(FluidTags.LAVA) && !player.isEyeInFluid(FluidTags.LAVA)) {
            cir.setReturnValue(true);
        }
    }
}
