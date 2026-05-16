package com.cyberday1.neoorigins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Negates the sub-1.0 walk slowdown of soul sand and honey blocks for
 * players with {@code neoorigins:no_slowdown}.
 *
 * <p>Vanilla {@code getBlockSpeedFactor} returns the speed factor of the
 * block the entity stands in, falling back to the block below when the
 * "here" factor is 1.0 (and the here-block isn't water / bubble column).
 * We mirror that selection so the {@code block_tag} filter is evaluated
 * against the same block vanilla actually applied the slowdown from, then
 * clamp the factor back to 1.0 when the player is immune.
 *
 * <p>Server authoritative + client prediction (unrestricted variant only)
 * via {@code ClientStuckInBlockHelper}, same trampoline rationale as
 * {@link EntityMakeStuckInBlockMixin}.
 */
@Mixin(Entity.class)
public abstract class EntityBlockSpeedFactorMixin {

    @Inject(method = "getBlockSpeedFactor", at = @At("RETURN"), cancellable = true)
    private void neoorigins$negateSpeedFactor(CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() >= 1.0F) return;
        Entity self = (Entity) (Object) this;

        if (self instanceof ServerPlayer sp) {
            BlockState relevant = neoorigins$slowdownBlock(self);
            if (com.cyberday1.neoorigins.service.NoSlowdownService
                    .skipsSlowdown(sp, relevant)) {
                cir.setReturnValue(1.0F);
            }
            return;
        }
        if (self.level().isClientSide()
            && com.cyberday1.neoorigins.client.ClientStuckInBlockHelper
                .shouldSkipSpeedFactorOnClient(self)) {
            cir.setReturnValue(1.0F);
        }
    }

    /**
     * The block vanilla {@code getBlockSpeedFactor} actually derived its
     * sub-1.0 factor from: the block at the entity position if that has a
     * speed factor (and isn't water / bubble column), otherwise the block
     * below that affects movement.
     */
    private static BlockState neoorigins$slowdownBlock(Entity self) {
        BlockState here = self.level().getBlockState(self.blockPosition());
        boolean fluidPassthrough = here.is(Blocks.WATER) || here.is(Blocks.BUBBLE_COLUMN);
        if (!fluidPassthrough && here.getBlock().getSpeedFactor() == 1.0F) {
            return self.level().getBlockState(self.getBlockPosBelowThatAffectsMyMovement());
        }
        return here;
    }
}
