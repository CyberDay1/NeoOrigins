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
 *         <li>Inside a solid block: full noclip with velocity-driven vertical
 *             control (jump = up, sneak = down, neither = slow settle),
 *             mirrored on the server — no creative flight.</li>
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
        } else if (neoorigins$isInsideSolid(self)
                || (net.minecraft.client.Minecraft.getInstance().options.keyShift.isDown() && neoorigins$hasSolidBelow(self))) {
            // Wall phase + inside a block (OR sneaking with ground below to
            // sink into) -- full noclip. The hasSolidBelow guard mirrors
            // WraithPhasePower.tickEffect (seerfix4): sneaking in OPEN AIR must
            // NOT enter the noclip branch, otherwise shift made the player
            // descend freely with no gravity and let them "jump off air" — the
            // Seer tester's exact report. Without ground below, fall through to
            // the surface branch where vertical collision/gravity still applies.
            //
            // Vertical motion is driven by intent, NOT creative flight and NOT
            // the gravity-derived movement.y vanilla handed us: jump = up,
            // sneak = down, neither = slow settle (phaseVerticalVelocity). The
            // CLIENT is authoritative for the phasing Y here: on 1.21.1 the
            // server cannot read an on-foot player's jump input, so it does NOT
            // recompute this velocity — it damps its own Y and snaps to the
            // position we send (noPhysics -> absMoveTo). That keeps server and
            // client agreeing on position, which is what lets survival block
            // placement raytrace correctly (no ghost blocks) and stops the
            // rubber-banding.
            var options = net.minecraft.client.Minecraft.getInstance().options;
            double vy = com.cyberday1.neoorigins.power.builtin.WraithPhasePower.phaseVerticalVelocity(
                options.keyJump.isDown(), options.keyShift.isDown());
            // Idle-settle guard (issue #109): the "neither jump nor sneak → slow
            // settle" nudge (−0.04) is meant only to keep a phasing player from
            // hovering; it must NOT keep sinking forever with no input. Under
            // noclip there is no ordinary collision to arrest it (clampAgainstBlocked
            // only stops blacklisted blocks), so an idle player would drift down
            // through the whole world into the void. When there is no vertical
            // input and solid ground sits directly below the feet, hold position
            // (zero the settle) so the player rests on the block below instead of
            // accumulating downward. Jump/sneak intent still moves normally.
            if (!options.keyJump.isDown() && !options.keyShift.isDown() && neoorigins$hasSolidBelow(self)) {
                vy = 0.0;
            }
            Vec3 intended = new Vec3(movement.x, vy, movement.z);
            // But block movement into blacklisted blocks
            Vec3 clamped = neoorigins$clampAgainstBlocked(self, intended);
            self.setPos(self.getX() + clamped.x, self.getY() + clamped.y, self.getZ() + clamped.z);
            self.horizontalCollision = false;
            self.minorHorizontalCollision = false;
            self.verticalCollision = false;
            self.verticalCollisionBelow = false;
            // Noclipping through a solid -- never on the ground here, or the
            // stuck-true flag re-enables vanilla spam-jump flight (see surface
            // branch below).
            self.setOnGround(false);
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
            // Drive onGround off the actual below-collision EVERY tick, not just
            // when landing. The old code only ever set it true and never cleared
            // it, so a player who phased while standing kept onGround=true after
            // rising into open air — the client then reported onGround=true to
            // the server, vanilla treated every jump press as a fresh ground
            // jump (spam-space flight), and the Seer air_jump reset_jumps
            // (gated on on_block) refilled forever. Clearing it while airborne
            // restores normal jump/fall semantics during surface phasing.
            self.setOnGround(self.verticalCollisionBelow);
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
        java.util.List<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>> blockedTags =
            ClientActivePowers.phaseBlockedTags();
        if (blocked.isEmpty() && blockedTags.isEmpty()) return movement;
        AABB moved = entity.getBoundingBox().move(movement);
        // Collect collision shapes of blacklisted blocks ONLY — colliding
        // against everything would also stop the player at the ordinary
        // blocks they're legitimately phasing through. A block is blacklisted
        // if its id is listed OR it carries a blacklisted block tag.
        java.util.List<VoxelShape> shapes = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(moved.minX, moved.minY, moved.minZ),
                BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (state.isAir()) continue;
            boolean isBlocked = blocked.contains(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            if (!isBlocked) {
                for (var tag : blockedTags) {
                    if (state.is(tag)) { isBlocked = true; break; }
                }
            }
            if (isBlocked) {
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

    /**
     * Client-side mirror of {@code WraithPhasePower.hasSolidBelow}: true if a
     * solid block sits directly beneath the player's feet (ground to phase down
     * into). Keeps the crouch-noclip branch from arming in open air.
     */
    @Unique
    private static boolean neoorigins$hasSolidBelow(Entity entity) {
        AABB box = entity.getBoundingBox().deflate(0.05);
        double feetY = box.minY;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, feetY - 0.5, box.minZ),
                BlockPos.containing(box.maxX, feetY - 0.01, box.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(entity.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
