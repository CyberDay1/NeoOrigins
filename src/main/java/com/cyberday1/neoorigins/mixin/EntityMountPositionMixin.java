package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.attachment.EntityAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adjusts the passenger riding offset when the passenger was mounted
 * via the mount power with the "shoulder" position.
 *
 * <p>Vanilla centres the passenger on the vehicle. When the mount
 * position attachment is {@code "shoulder"}, we shift the passenger
 * ~0.4 blocks to the side so they sit on the vehicle's shoulder.
 */
@Mixin(Entity.class)
public abstract class EntityMountPositionMixin {

    @Inject(
        method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
        at = @At("TAIL")
    )
    private void neoorigins$adjustMountPosition(Entity passenger, Entity.MoveFunction moveFunc, CallbackInfo ci) {
        if (!passenger.hasData(EntityAttachments.mountPosition())) return;
        String position = passenger.getData(EntityAttachments.mountPosition());
        if (!"shoulder".equals(position)) return;

        // Shift the passenger to the side based on the vehicle's facing direction.
        // Use the vehicle's Y rotation to determine the perpendicular offset.
        Entity self = (Entity) (Object) this;
        float yaw = self.getYRot();
        double radians = Math.toRadians(yaw + 90); // perpendicular to facing direction
        double offsetX = Math.cos(radians) * 0.4;
        double offsetZ = Math.sin(radians) * 0.4;

        Vec3 pos = passenger.position();
        passenger.setPos(pos.x + offsetX, pos.y, pos.z + offsetZ);
    }

    /**
     * Resync the vehicle player's own client after a passenger is removed.
     *
     * <p>A player never tracks itself, so the server's ClientboundSetPassengers
     * broadcast for the vehicle never reaches the vehicle player's own client.
     * Without this, the vehicle player's client keeps a stale passenger after a
     * dismount, only self-healing on the next re-track. This RETURN inject runs
     * AFTER the passenger is actually removed, so the packet reflects the
     * now-updated passenger list. Sending a player the true passenger list of
     * its own entity is always safe, so we don't gate on the mount attachment
     * (issue #111).
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
}
