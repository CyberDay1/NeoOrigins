package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Core of {@code neoorigins:ignore_fluid}: makes an ignored fluid invisible to
 * fluid <em>detection</em>, so every downstream effect falls away at once.
 *
 * <p><b>This is where 26.x differs from the 1.21.1 lead branch.</b> On 1.21.1 the
 * entity carried a per-{@code FluidType} height map that NeoForge filled from
 * {@code Entity.updateFluidHeightAndDoFluidPushing} and
 * {@code Entity.updateFluidOnEyes}, and the lead wraps the {@code getFluidState}
 * scan inside both. Neither method nor the map exists on 26.x: NeoForge dropped
 * entity-side fluid-type tracking and vanilla now owns the whole thing in
 * {@link EntityFluidInteraction}, a per-entity object built over the
 * {@code minecraft:water} and {@code minecraft:lava} fluid tags.
 *
 * <p>That collapses two wrap sites into one. {@code EntityFluidInteraction.update}
 * runs a single AABB scan that fills height, eyes-inside and the accumulated
 * current for every tracked fluid, so handing back an empty {@link FluidState}
 * here removes buoyancy, drag, current push, {@code isInWater()}/{@code isInLava()},
 * {@code lavaHurt()}, the swim pose, the jump/fall-distance modifiers, the fire
 * extinguish, {@code isEyeInFluid} and therefore drowning, the air bar and the
 * screen fluid overlay — all of it, from one place.
 *
 * <p><b>It must be the scan, not the tracker write.</b> The flow vector is
 * accumulated inside the same loop and applied later by {@code applyCurrentTo},
 * so suppressing anything downstream of the scan would leave current push intact.
 *
 * <p><b>Not {@code Entity.getFluidInteractionBox()}.</b> Returning null from that
 * skips the scan entirely and kills <em>every</em> fluid for the player, not the
 * selected ones. {@code EntityFluidInteraction.getTrackerFor} is private and has
 * no entity reference, so it cannot be used either.
 *
 * <p><b>Known 26.x limitation.</b> The tracker set is fixed at
 * {@code Set.of(FluidTags.WATER, FluidTags.LAVA)}, so a modded fluid outside
 * those tags is never tracked and gets no vanilla buoyancy, drag or push in the
 * first place. There is nothing to suppress for it and the power quietly no-ops.
 * The JSON schema still accepts any fluid id on purpose, so datapacks stay
 * portable across all three branches.
 *
 * <p>Wrapped rather than {@code @Redirect}ed so other mods redirecting the same
 * call still compose. The enclosing {@code entity} is a method parameter, so the
 * handler captures it off the end of its own signature.
 *
 * <p>This is a common mixin and runs on both logical sides; the capability lookup
 * is routed through {@code PowerCapabilities}, which resolves the client branch
 * against the synced capability set for the local player only. Client-side
 * agreement is required, not optional: the player predicts its own movement, and
 * a server-only suppression would rubber-band.
 *
 * <p>The player check comes first, deliberately: {@code update} runs for every
 * entity every tick and must not do capability work for mobs and items.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class EntityFluidInteractionIgnoreFluidMixin {

    @WrapOperation(
        method = "update(Lnet/minecraft/world/entity/Entity;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState neoorigins$ignoreFluidInBodyScan(BlockGetter level, BlockPos pos,
                                                        Operation<FluidState> original,
                                                        Entity entity, boolean ignoreCurrent) {
        FluidState state = original.call(level, pos);
        if (entity instanceof Player player && IgnoreFluidCapabilities.ignores(player, state)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return state;
    }
}
