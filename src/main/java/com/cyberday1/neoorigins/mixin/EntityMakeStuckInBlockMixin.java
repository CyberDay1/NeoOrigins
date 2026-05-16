package com.cyberday1.neoorigins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the {@code stuckSpeedMultiplier} velocity clamp (cobweb, sweet
 * berry bush, powder snow) when the player should be immune:
 * <ul>
 *   <li>{@code cobweb_affinity} capability (Arachnid) — cobweb only</li>
 *   <li>{@code neoorigins:no_slowdown} power — any stuck block, with
 *       optional {@code block_tag} filtering, resolved server-side by
 *       {@link com.cyberday1.neoorigins.service.NoSlowdownService}</li>
 * </ul>
 *
 * <p>Runs on both logical sides: server authoritative + client prediction
 * via {@code LocalPlayer}. The client check is delegated to
 * {@code ClientStuckInBlockHelper} because {@code LocalPlayer} is a
 * client-only type and naming it directly in a common mixin crashes the
 * dedicated server at mixin-transform time.
 */
@Mixin(Entity.class)
public abstract class EntityMakeStuckInBlockMixin {

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void neoorigins$skipStuck(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer sp) {
            boolean cobwebAffinity = state.is(Blocks.COBWEB)
                && com.cyberday1.neoorigins.service.ActiveOriginService
                    .hasCapability(sp, "cobweb_affinity");
            if (cobwebAffinity
                || com.cyberday1.neoorigins.service.NoSlowdownService
                    .skipsSlowdown(sp, state)) {
                ci.cancel();
            }
            return;
        }
        // Client-side prediction is delegated to ClientStuckInBlockHelper. A
        // direct `instanceof LocalPlayer` here would crash the dedicated
        // server at mixin-transform time (ClassMetadataNotFoundException for
        // LocalPlayer). The helper hides the client-only type behind a
        // lazily-verified method body. Reached only when isClientSide().
        if (self.level().isClientSide()
            && com.cyberday1.neoorigins.client.ClientStuckInBlockHelper
                .shouldSkipStuckOnClient(self, state)) {
            ci.cancel();
        }
    }
}
