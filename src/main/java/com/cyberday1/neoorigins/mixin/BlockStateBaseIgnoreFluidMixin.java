package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Second half of {@code neoorigins:ignore_fluid}: suppresses whatever an ignored
 * fluid's own <em>block</em> does to the player inside it.
 *
 * <p>{@code EntityFluidInteractionIgnoreFluidMixin} covers everything vanilla
 * derives from fluid detection, but a modded fluid that damages, poisons or
 * teleports you does it from {@code Block.entityInside} on its own
 * {@code LiquidBlock}, which never consults the entity's fluid state.
 *
 * <p>This route carries more weight on 26.x than it does on the 1.21.1 lead.
 * There, NeoForge tracked every {@code FluidType} on the entity, so suppressing
 * detection already reached modded fluids. On 26.x vanilla only tracks the
 * {@code minecraft:water} and {@code minecraft:lava} fluid tags, so for a modded
 * fluid outside those tags this guard is the <em>only</em> thing "ignore every
 * effect the fluid might have" can act on.
 *
 * <p>Keyed on {@code state.getFluidState()}, so it only ever fires for blocks
 * that <em>are</em> a fluid — a waterlogged stair would also qualify, which is
 * correct: the player is ignoring that fluid either way.
 *
 * <p>Player check first, deliberately. {@code entityInside} is hot and this mixin
 * must not do fluid or capability work for the mobs and items that make up most
 * calls.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseIgnoreFluidMixin {

    @Shadow
    public abstract FluidState getFluidState();

    // require defaults to 1 (neoorigins.mixins.json defaultRequire): a silent
    // no-op here would be the only route mod-authored fluid damage escapes, so
    // a target rename must fail loudly rather than quietly ship half the power.
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void neoorigins$ignoreFluidEntityInside(Level level, BlockPos pos, Entity entity,
                                                    InsideBlockEffectApplier effectApplier,
                                                    boolean isPrecise, CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        FluidState fluid = getFluidState();
        if (fluid.isEmpty()) return;
        if (IgnoreFluidCapabilities.ignores(entity, fluid)) {
            ci.cancel();
        }
    }
}
