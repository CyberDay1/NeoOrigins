package com.cyberday1.neoorigins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a player who is acting as a vehicle in sync with their own passenger
 * list.
 *
 * <p>The KubeJS {@code mountEnded} event that this mixin also fires on 1.21.1
 * has no counterpart here: this branch carries no KubeJS compat layer.
 */
@Mixin(Entity.class)
public abstract class EntityMountEndMixin {

    /**
     * Resync the vehicle player's own client after a passenger is removed.
     *
     * <p>A player never tracks itself, so the server's ClientboundSetPassengers
     * broadcast for the vehicle never reaches the vehicle player's own client.
     * Without this, the vehicle player's client keeps a stale passenger after a
     * dismount, only self-healing on the next re-track. This RETURN inject runs
     * AFTER the passenger is actually removed, so the packet reflects the
     * now-updated passenger list. Sending a player the true passenger list of
     * its own entity is always safe, so we don't gate on the mount attachment.
     */
    @Inject(
        method = "removePassenger(Lnet/minecraft/world/entity/Entity;)V",
        at = @At("RETURN")
    )
    private void neoorigins$resyncVehicleOnDismount(Entity passenger, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer vehiclePlayer) {
            vehiclePlayer.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(vehiclePlayer));
        }
    }

    /**
     * Mount-side mirror of {@link #neoorigins$resyncVehicleOnDismount}, for the
     * same self-tracking reason: the ridden player is the one client the
     * tracker's broadcast never reaches, so without this they alone cannot see
     * their own rider.
     *
     * <p>{@code MountConsentManager#doMount} already sends this explicitly, but
     * it is not the only way a player ends up ridden — the Apoli compat
     * bi-entity {@code mount} action, the builtin mount action, and
     * {@code SummonMinionPower} all call {@code startRiding} directly and had
     * no such resend. Injecting at the common sink covers every present and
     * future call site instead of each one remembering.
     */
    @Inject(
        method = "addPassenger(Lnet/minecraft/world/entity/Entity;)V",
        at = @At("RETURN")
    )
    private void neoorigins$resyncVehicleOnMount(Entity passenger, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer vehiclePlayer) {
            vehiclePlayer.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(vehiclePlayer));
        }
    }
}
