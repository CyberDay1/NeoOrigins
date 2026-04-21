package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.WallClimbingPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code LivingEntity.onClimbable()} return {@code true} when the entity is a
 * player with a granted {@link WallClimbingPower} AND is pressed against a wall
 * while airborne. Applies on both sides:
 *
 * <ul>
 *   <li><b>Server (ServerPlayer)</b>: authoritative check against
 *       {@link ActiveOriginService}.</li>
 *   <li><b>Client (LocalPlayer)</b>: mirror check via the synced
 *       {@code ClientPowerCache}, which carries the registry type ID for each
 *       power so we can ask "does my origin carry any wall_climbing power?"
 *       without shipping PowerType classes to the client.</li>
 * </ul>
 *
 * <p>Needing both sides is load-bearing: vanilla's client-side movement
 * prediction runs {@code LivingEntity.travel} locally every tick, so if the
 * client still returns {@code false} from {@code onClimbable()} it will predict
 * the player falling and rubber-band every server correction. With both sides
 * agreeing, the player climbs smoothly — no rubber-band even on dedicated
 * servers.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    private static final Identifier WALL_CLIMBING_TYPE_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "wall_climbing");

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void neoorigins$wallClimbPower(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player)) return;
        // Only hijack the return when the entity is actually pressed against a wall —
        // otherwise ordinary climbing on ladders and vines is unchanged.
        if (!self.horizontalCollision) return;
        if (self.onGround()) return;

        boolean hasPower;
        if (self instanceof ServerPlayer sp) {
            hasPower = ActiveOriginService.has(sp, WallClimbingPower.class, cfg -> true);
        } else if (self.level().isClientSide()) {
            hasPower = com.cyberday1.neoorigins.client.ClientPowerCache.localPlayerHasPowerOfType(WALL_CLIMBING_TYPE_ID);
        } else {
            hasPower = false;
        }

        if (hasPower) cir.setReturnValue(true);
    }
}
