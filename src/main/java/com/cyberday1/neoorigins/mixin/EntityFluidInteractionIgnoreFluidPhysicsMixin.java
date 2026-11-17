package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Current-push part of {@code neoorigins:ignore_fluid} with {@code "mode": "physics"}.
 *
 * <p>{@code EntityFluidInteraction.applyCurrentTo} asks the entity's per-type
 * {@code isPushedByFluid(FluidType)} before adding a fluid's accumulated current.
 * Answering no for a covered fluid drops the push while the scan still records
 * the fluid's height, so the fluid stays detected.
 *
 * <p>{@code applyCurrentTo*} covers every overload that asks, as
 * {@link EntityFluidInteractionIgnoreFluidMixin} does for {@code update*}: 26.3
 * asks in two (the whole-entity one and the per-fluid one), 26.1 and 26.2 in one.
 * A bare name binds to the first overload only, which on these branches is the
 * deprecated one that asks nothing.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class EntityFluidInteractionIgnoreFluidPhysicsMixin {

    @WrapOperation(
        method = "applyCurrentTo*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid(Lnet/neoforged/neoforge/fluids/FluidType;)Z"
        )
    )
    private boolean neoorigins$ignoreFluidPhysicsPush(Entity entity, FluidType type,
                                                      Operation<Boolean> original) {
        if (IgnoreFluidCapabilities.ignoresPhysics(entity, type)) return false;
        return original.call(entity, type);
    }
}
