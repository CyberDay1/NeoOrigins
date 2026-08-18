package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.FlightPower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected int fallFlyTicks;

    /**
     * When a player has either the FlightPower active OR the {@code natural_glide}
     * capability (Phantom, Elytrian, Hiveling, Draconic), skip vanilla's
     * {@code canGlide()} check (which requires an elytra) and the equipment
     * damage logic. Without this bypass, vanilla's per-tick {@code updateFallFlying}
     * would clear the fall-flying flag on the very next tick after our
     * {@code PlayerStartFallFlyingMixin} / {@code LocalPlayerNaturalGlideMixin}
     * sets it — the player would visibly start gliding for one frame and
     * immediately drop.
     */
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void neoorigins$skipGlideCheckForFlightPower(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer sp
                && (FlightPower.isActive(sp)
                    || ActiveOriginService.hasCapability(sp, "natural_glide"))) {
            self.checkSlowFallDistance();
            if (!self.level().isClientSide) {
                // Stop flight on ground, in water, or as passenger (same as vanilla canGlide)
                if (self.onGround() || self.isInWater() || self.isPassenger()) {
                    // Only act on the tick flight actually ends. Running this
                    // unconditionally re-sent the stop-sound packet to every
                    // player within 64 blocks twenty times a second, which also
                    // silenced the elytra of bystanders who were genuinely
                    // gliding past (#116).
                    if (self.isFallFlying()) {
                        sp.stopFallFlying();
                        // Broadcast stop-elytra-sound to all nearby clients.
                        // Vanilla's ElytraOnPlayerSoundInstance checks isFallFlying()
                        // each tick, but network sync delay can leave the loop running
                        // for remote players — the sound stacks and gets louder (#42).
                        var stopPacket = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                            net.minecraft.sounds.SoundEvents.ELYTRA_FLYING.getLocation(),
                            net.minecraft.sounds.SoundSource.PLAYERS);
                        for (var nearby : ((net.minecraft.server.level.ServerLevel) sp.level())
                                .players()) {
                            if (nearby.distanceToSqr(sp) < 64 * 64) {
                                nearby.connection.send(stopPacket);
                            }
                        }
                    }
                    this.fallFlyTicks = 0;
                } else {
                    // Don't check canGlide() — allow flight without elytra.
                    // Don't damage equipment — there's no elytra to damage.
                    this.fallFlyTicks++;
                }
            }
            ci.cancel();
        }
        // Client-side: bypass vanilla's canGlide() for powers that grant flight
        // or natural_glide. Without this, the client clears fall-flying on the
        // very next tick (no elytra equipped), causing rubber-banding on dedicated
        // servers where entity-state sync has latency. PowerCapabilities safely
        // bridges client/server via ClientPowerCapabilitiesBridge (no server
        // classloading risk).
        else if (self.level().isClientSide
                && self instanceof net.minecraft.world.entity.player.Player clientPlayer
                && (PowerCapabilities.hasActive(self, "flight")
                    || PowerCapabilities.hasActive(self, "natural_glide"))) {
            self.checkSlowFallDistance();
            if (self.onGround() || self.isInWater() || self.isPassenger()) {
                // Guarded for the same reason as the server branch, and this is
                // the half the player actually hears. Player.stopFallFlying()
                // sets shared-flag 7 true and then false, and LocalPlayer starts
                // a fresh ElytraOnPlayerSoundInstance on that rising edge
                // whenever its once-per-tick wasFallFlying snapshot is stale.
                // Calling it every tick on the ground therefore stacked twenty
                // overlapping elytra sounds a second (#116).
                if (self.isFallFlying()) {
                    clientPlayer.stopFallFlying();
                }
                this.fallFlyTicks = 0;
            } else {
                this.fallFlyTicks++;
            }
            ci.cancel();
        }
    }
}
