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
 * Skips cobweb slowdown for players with the {@code cobweb_affinity}
 * capability (Arachnid origin). Other stuck-in-block sources (sweet berry
 * bush, soul sand, etc.) still slow normally — we only intercept when the
 * block is a cobweb.
 *
 * <p>Runs on both logical sides: server authoritative + client prediction
 * via {@code LocalPlayer}. The client check uses {@code ClientActivePowers}
 * because {@code LocalPlayer} is not a {@code ServerPlayer}.
 */
@Mixin(Entity.class)
public abstract class EntityMakeStuckInBlockMixin {

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void neoorigins$skipCobwebStuck(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if (!state.is(Blocks.COBWEB)) return;
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer sp) {
            if (com.cyberday1.neoorigins.service.ActiveOriginService.hasCapability(sp, "cobweb_affinity")) {
                ci.cancel();
            }
            return;
        }
        // Client-side prediction is delegated to ClientStuckInBlockHelper. The
        // direct `instanceof LocalPlayer` here would crash the dedicated server
        // at mixin-transform time (ClassMetadataNotFoundException for LocalPlayer).
        // The helper hides the client-only type behind a lazily-verified method
        // body. Reached only when self.level().isClientSide() — server never
        // loads it.
        if (self.level().isClientSide()
            && com.cyberday1.neoorigins.client.ClientStuckInBlockHelper
                .shouldSkipCobwebStuckOnClient(self)) {
            ci.cancel();
        }
    }
}
