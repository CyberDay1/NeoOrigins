package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Client-side block phasing for origin powers.
 *
 * <ul>
 *   <li>{@code "no_physics"} -- full noclip (PhantomFormPower, always flying).</li>
 *   <li>{@code "wall_phase"} -- dual mode (WraithPhasePower):
 *       <ul>
 *         <li>On the surface: horizontal collision disabled, vertical collision
 *             kept (walk on the ground, gravity works).</li>
 *         <li>Inside a solid block: full noclip (flight is enabled server-side
 *             so jump = up, shift = down).</li>
 *       </ul>
 *   </li>
 * </ul>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerNoPhysicsMixin {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void neoorigins$phaseMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        boolean fullNoclip = ClientActivePowers.hasCapability("no_physics");
        boolean wallPhase  = ClientActivePowers.hasCapability("wall_phase");
        if (!fullNoclip && !wallPhase) return;

        Entity self = (Entity) (Object) this;

        if (fullNoclip) {
            // PhantomForm -- full noclip, always flying
            self.setPos(self.getX() + movement.x, self.getY() + movement.y, self.getZ() + movement.z);
            self.horizontalCollision = false;
            self.minorHorizontalCollision = false;
            self.verticalCollision = false;
            self.verticalCollisionBelow = false;
            self.setDeltaMovement(movement);
        } else if (neoorigins$isInsideSolid(self) || ((LocalPlayer) self).input.shiftKeyDown) {
            // Wall phase + inside a block -- full noclip (server enables flight)
            // But block movement into bedrock
            Vec3 clamped = neoorigins$clampAgainstBedrock(self, movement);
            self.setPos(self.getX() + clamped.x, self.getY() + clamped.y, self.getZ() + clamped.z);
            self.horizontalCollision = false;
            self.minorHorizontalCollision = false;
            self.verticalCollision = false;
            self.verticalCollisionBelow = false;
            self.setDeltaMovement(clamped);
        } else {
            // Wall phase + on surface -- horizontal noclip (except bedrock), vertical collision kept
            Vec3 hClamped = neoorigins$clampAgainstBedrock(self, new Vec3(movement.x, 0, movement.z));
            double newX = self.getX() + hClamped.x;
            double newZ = self.getZ() + hClamped.z;

            AABB movedBox = self.getBoundingBox().move(hClamped.x, 0, hClamped.z);
            List<VoxelShape> shapes = self.level().getEntityCollisions(self, movedBox.expandTowards(0, movement.y, 0));
            Vec3 verticalOnly = Entity.collideBoundingBox(self, new Vec3(0, movement.y, 0), movedBox, self.level(), shapes);
            double newY = self.getY() + verticalOnly.y;

            self.setPos(newX, newY, newZ);
            self.horizontalCollision = hClamped.x != movement.x || hClamped.z != movement.z;
            self.minorHorizontalCollision = false;
            self.verticalCollision = movement.y != verticalOnly.y;
            self.verticalCollisionBelow = self.verticalCollision && movement.y < 0;
            if (self.verticalCollisionBelow) {
                self.setOnGround(true);
            }
            self.setDeltaMovement(hClamped.x, verticalOnly.y, hClamped.z);
        }
        self.fallDistance = 0.0F;
        ci.cancel();
    }

    /**
     * Zeros out any movement axis that would push the player's bounding box
     * into a bedrock block. Prevents phasing through bedrock sideways/vertically.
     */
    @Unique
    private static Vec3 neoorigins$clampAgainstBedrock(Entity entity, Vec3 movement) {
        AABB moved = entity.getBoundingBox().move(movement);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(moved.minX, moved.minY, moved.minZ),
                BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                // Bedrock detected in destination — apply vanilla collision for this movement
                java.util.List<VoxelShape> shapes = new java.util.ArrayList<>();
                entity.level().getBlockCollisions(entity, moved).forEach(shapes::add);
                if (!shapes.isEmpty()) {
                    return Entity.collideBoundingBox(entity, movement, entity.getBoundingBox(), entity.level(), shapes);
                }
            }
        }
        return movement;
    }

    @Unique
    private static boolean neoorigins$isInsideSolid(Entity entity) {
        AABB box = entity.getBoundingBox().deflate(0.1);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY + 0.1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY - 0.1, box.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(entity.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
