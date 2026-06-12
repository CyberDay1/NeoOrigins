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
 *         <li>Inside a solid block or holding shift: full noclip (flight is
 *             enabled server-side so jump = up, shift = down).</li>
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
        } else if (neoorigins$isInsideSolid(self) || net.minecraft.client.Minecraft.getInstance().options.keyShift.isDown()) {
            // Wall phase + inside a block or holding shift -- full noclip (server enables flight)
            // But block movement into blacklisted blocks
            Vec3 clamped = neoorigins$clampAgainstBlocked(self, movement);
            self.setPos(self.getX() + clamped.x, self.getY() + clamped.y, self.getZ() + clamped.z);
            self.horizontalCollision = false;
            self.minorHorizontalCollision = false;
            self.verticalCollision = false;
            self.verticalCollisionBelow = false;
            self.setDeltaMovement(clamped);
        } else {
            // Wall phase + on surface -- horizontal noclip (except blacklisted blocks), vertical collision kept
            Vec3 hClamped = neoorigins$clampAgainstBlocked(self, new Vec3(movement.x, 0, movement.z));
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
     * Applies vanilla collision to any movement that would push the player's
     * bounding box into a blacklisted block ({@code blocked_blocks} on the
     * phase power, synced via {@code phase_blocked:} capability tags).
     * Previously this hardcoded bedrock only, which silently let phasing
     * players through obsidian and any other pack-blacklisted block — the
     * client predicts the movement and the server accepts it, so the client
     * clamp IS the blacklist.
     */
    @Unique
    private static Vec3 neoorigins$clampAgainstBlocked(Entity entity, Vec3 movement) {
        java.util.Set<net.minecraft.resources.Identifier> blocked =
            ClientActivePowers.phaseBlockedBlocks();
        if (blocked.isEmpty()) return movement;
        AABB moved = entity.getBoundingBox().move(movement);
        // Collect collision shapes of blacklisted blocks ONLY — colliding
        // against everything would also stop the player at the ordinary
        // blocks they're legitimately phasing through.
        java.util.List<VoxelShape> shapes = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(moved.minX, moved.minY, moved.minZ),
                BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.isAir() && blocked.contains(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                VoxelShape shape = state.getCollisionShape(entity.level(), pos);
                if (!shape.isEmpty()) {
                    shapes.add(shape.move(pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        }
        if (!shapes.isEmpty()) {
            return Entity.collideBoundingBox(entity, movement, entity.getBoundingBox(), entity.level(), shapes);
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
