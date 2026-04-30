package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Client-side block phasing for origin powers.
 *
 * <p>Two capabilities are handled:
 * <ul>
 *   <li>{@code "no_physics"} — full noclip (PhantomFormPower). The player is
 *       flying, so disabling all collision is fine.</li>
 *   <li>{@code "wall_phase"} — horizontal-only phasing (WraithPhasePower). The
 *       player walks on the ground — horizontal block collision is disabled but
 *       vertical collision (gravity, standing on floor) is kept.</li>
 * </ul>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerNoPhysicsMixin {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void neoorigins$phaseMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        boolean fullNoclip = ClientActivePowers.hasCapability("no_physics");
        boolean wallPhase = ClientActivePowers.hasCapability("wall_phase");
        if (!fullNoclip && !wallPhase) return;

        Entity self = (Entity) (Object) this;

        if (fullNoclip) {
            // Full noclip — skip all collision (player is flying via PhantomForm)
            self.setPos(self.getX() + movement.x, self.getY() + movement.y, self.getZ() + movement.z);
            self.horizontalCollision = false;
            self.minorHorizontalCollision = false;
            self.verticalCollision = false;
            self.verticalCollisionBelow = false;
            self.setDeltaMovement(movement);
        } else {
            // Wall phase — horizontal movement ignores blocks, vertical keeps collision
            double newX = self.getX() + movement.x;
            double newZ = self.getZ() + movement.z;

            AABB box = self.getBoundingBox();
            // Move box to new XZ first so vertical collision checks happen at the destination
            AABB movedBox = box.move(movement.x, 0, movement.z);
            List<VoxelShape> shapes = self.level().getEntityCollisions(self, movedBox.expandTowards(0, movement.y, 0));
            Vec3 verticalOnly = Entity.collideBoundingBox(self, new Vec3(0, movement.y, 0), movedBox, self.level(), shapes);
            double newY = self.getY() + verticalOnly.y;

            self.setPos(newX, newY, newZ);
            self.horizontalCollision = false;
            self.minorHorizontalCollision = false;
            self.verticalCollision = movement.y != verticalOnly.y;
            self.verticalCollisionBelow = self.verticalCollision && movement.y < 0;
            if (self.verticalCollisionBelow) {
                self.setOnGround(true);
            }
            self.setDeltaMovement(movement.x, verticalOnly.y, movement.z);
        }
        self.fallDistance = 0.0F;
        ci.cancel();
    }
}
