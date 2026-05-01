package com.cyberday1.neoorigins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla {@code Player.tick()} resets {@code noPhysics = this.isSpectator()}
 * every tick, overriding the value set by {@code WraithPhasePower} and
 * {@code PhantomFormPower} in {@code PlayerTickEvent.Pre}.
 *
 * <p>This mixin re-applies the flag immediately after the spectator reset
 * when the player has a phasing capability ({@code wall_phase} or
 * {@code no_physics}), so that both the server movement validator
 * ({@code ServerGamePacketListenerImpl}) and the client suffocation overlay
 * ({@code ScreenEffectRenderer}) see the correct value.
 */
@Mixin(Player.class)
public abstract class PlayerPhaseOverrideMixin {

    @Inject(
        method = "tick",
        at = @At(
            value  = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD,
            shift  = At.Shift.AFTER
        )
    )
    private void neoorigins$restorePhaseNoPhysics(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.noPhysics) return;              // spectator — already true

        if (self instanceof ServerPlayer sp) {
            if (com.cyberday1.neoorigins.service.ActiveOriginService.hasCapability(sp, "wall_phase")
                || com.cyberday1.neoorigins.service.ActiveOriginService.hasCapability(sp, "no_physics")) {
                self.noPhysics = true;
            }
        } else if (self.level().isClientSide()) {
            // Client side — trampoline through a method body so that
            // ClientActivePowers is never resolved on a dedicated server.
            if (neoorigins$checkClientPhase()) {
                self.noPhysics = true;
            }
        }
    }

    /**
     * Isolated in its own method so the JVM only resolves
     * {@code ClientActivePowers} when this method is actually invoked
     * (which only happens on the logical client).
     */
    private static boolean neoorigins$checkClientPhase() {
        return com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("wall_phase")
            || com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("no_physics");
    }
}
