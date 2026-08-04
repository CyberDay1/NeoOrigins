package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Core of {@code neoorigins:ignore_fluid}: makes an ignored fluid invisible to
 * fluid <em>detection</em>, so every downstream effect falls away at once.
 *
 * <p>Two scan sites are wrapped, both reading {@code Level.getFluidState}:
 *
 * <ul>
 *   <li>{@code Entity.updateFluidHeightAndDoFluidPushing()V} — the AABB scan that
 *       fills {@code forgeFluidTypeHeight}. Suppressing the fluid here removes
 *       buoyancy, drag, current push, {@code isInWater()}/{@code isInLava()},
 *       {@code lavaHurt()}, the swim pose, the jump/fall-distance modifiers and
 *       the fire-extinguish, because all of them read that map.
 *       <br><b>It must be the scan, not the map write.</b> The flow impulse is
 *       applied by {@code setDeltaMovement(...add(flowVector))} <em>before</em>
 *       {@code setFluidTypeHeight} stores the height, so cancelling the write
 *       would leave current push fully intact.</li>
 *   <li>{@code Entity.updateFluidOnEyes()V} — fills {@code forgeFluidTypeOnEyes},
 *       which backs {@code isEyeInFluid} and therefore drowning, the air bar and
 *       the screen fluid overlay.</li>
 * </ul>
 *
 * <p>Wrapped rather than {@code @Redirect}ed so other mods redirecting the same
 * calls still compose.
 *
 * <p>This is a common mixin and runs on both logical sides; the capability lookup
 * is routed through {@code PowerCapabilities}, which resolves the client branch
 * against the synced capability set for the local player only. Client-side
 * agreement is required, not optional: the player predicts its own movement, and
 * a server-only suppression would rubber-band.
 */
@Mixin(Entity.class)
public abstract class EntityIgnoreFluidMixin {

    @Unique
    private FluidState neoorigins$maskIgnoredFluid(FluidState state) {
        if ((Object) this instanceof Player player
                && IgnoreFluidCapabilities.ignores(player, state)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return state;
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState neoorigins$ignoreFluidInBodyScan(Level level, BlockPos pos,
                                                        Operation<FluidState> original) {
        return neoorigins$maskIgnoredFluid(original.call(level, pos));
    }

    @WrapOperation(
        method = "updateFluidOnEyes()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState neoorigins$ignoreFluidOnEyes(Level level, BlockPos pos,
                                                    Operation<FluidState> original) {
        return neoorigins$maskIgnoredFluid(original.call(level, pos));
    }
}
