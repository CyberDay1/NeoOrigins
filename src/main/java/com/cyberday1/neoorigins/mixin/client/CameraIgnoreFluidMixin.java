package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client leak plug for {@code neoorigins:ignore_fluid}.
 *
 * <p>{@code Camera.getFluidInCamera} decides the {@code FogType} — and therefore
 * underwater/lava fog and the underwater screen distortion — by sampling
 * {@code BlockGetter.getFluidState} at the camera position directly. It never
 * consults the entity, so the {@code Entity} detection mixins do <em>not</em>
 * cover it: without this, a player who ignores water still swims in blue fog.
 *
 * <p>Both sample sites in the method (the water check at the camera block, and
 * the lava check across the near plane) are wrapped by the single handler.
 */
@Mixin(Camera.class)
public abstract class CameraIgnoreFluidMixin {

    @Shadow
    private Entity entity;

    @WrapOperation(
        method = "getFluidInCamera",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState neoorigins$ignoreFluidInCamera(BlockGetter level, BlockPos pos,
                                                      Operation<FluidState> original) {
        FluidState state = original.call(level, pos);
        if (IgnoreFluidCapabilities.ignores(entity, state)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return state;
    }
}
