package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * World-border containment for phasing powers, plus the shapes-only collision
 * both barriers (the border and {@code blocked_blocks}) are resolved with.
 *
 * <p>Vanilla enforces the border inside {@code Entity.collide} — that is where
 * {@code Entity.collectColliders} appends {@code WorldBorder.getCollisionShape()}.
 * Phasing never reaches it: the client moves with {@code setPos} and the server
 * runs under {@code noPhysics}, both of which skip {@code collide} entirely. So
 * a phasing wraith walked straight out through the border, and once outside
 * found nothing solid to phase through — the "my flight through blocks turned
 * off" half of the report, since past the border the power falls back to its
 * surface branch for want of any block to be inside of.
 *
 * <p>The split mirrors the {@link PhaseBlacklist} one: the CLIENT clamp is the
 * enforcement, because under {@code noPhysics} the server snaps to the position
 * the client reports, and {@link #pullInsideBorder} is the server's backstop —
 * it recovers anyone already outside (a tester who escaped before this fix) and
 * a client that did not clamp.
 */
// hub: neoorigins/phase-world-border.md
public final class PhaseBounds {

    private PhaseBounds() {}

    /**
     * How far inside the border face an escaped phasing player is put back.
     * Larger than the half-width of a player hitbox (0.3) so the correction
     * cannot land them straddling the face and immediately qualify again.
     */
    public static final double PULL_BACK_INSET = 0.5;

    /**
     * Adds the world border's collision shape to {@code out} when {@code movement}
     * could carry {@code entity} into it, on vanilla's own terms
     * ({@code isInsideCloseToBorder}, the same test {@code collectColliders} uses).
     * Adds nothing away from the border, so the ordinary case costs one distance
     * check.
     */
    public static void addBorderShape(Entity entity, Vec3 movement, List<VoxelShape> out) {
        WorldBorder border = entity.level().getWorldBorder();
        AABB reach = entity.getBoundingBox().expandTowards(movement);
        if (border.isInsideCloseToBorder(entity, reach)) {
            out.add(border.getCollisionShape());
        }
    }

    /**
     * Axis-wise collision against {@code shapes} and nothing else.
     *
     * <p>{@link Entity#collideBoundingBox} cannot be used for this. It funnels
     * through {@code collectColliders}, which unconditionally appends
     * {@code level.getBlockCollisions(...)} to whatever list it is handed, so
     * passing it the barrier shapes still collided the player against EVERY
     * block in range — including the ordinary ones they are legitimately phasing
     * through. The effect was that a wraith froze solid inside a wall whenever a
     * blacklisted block happened to be within reach, which is the opposite of
     * what the blacklist is for. Vanilla's own shapes-only routine
     * ({@code Entity.collideWithShapes}) is private, so its axis order is
     * mirrored here.
     */
    public static Vec3 collideAgainstShapes(Vec3 movement, AABB box, List<VoxelShape> shapes) {
        double dx = movement.x;
        double dy = movement.y;
        double dz = movement.z;
        if (dy != 0.0) {
            dy = Shapes.collide(Direction.Axis.Y, box, shapes, dy);
            if (dy != 0.0) box = box.move(0.0, dy, 0.0);
        }
        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0.0) {
            dz = Shapes.collide(Direction.Axis.Z, box, shapes, dz);
            if (dz != 0.0) box = box.move(0.0, 0.0, dz);
        }
        if (dx != 0.0) {
            dx = Shapes.collide(Direction.Axis.X, box, shapes, dx);
            if (!zFirst && dx != 0.0) box = box.move(dx, 0.0, 0.0);
        }
        if (!zFirst && dz != 0.0) {
            dz = Shapes.collide(Direction.Axis.Z, box, shapes, dz);
        }
        return new Vec3(dx, dy, dz);
    }

    /**
     * Puts a phasing player who is outside the border back inside it, quietly
     * (no teleport VFX — this is a correction, not an ability). Returns true if
     * the player was moved.
     *
     * <p>Keeps the player's Y: they are phasing, so arriving inside terrain is
     * the normal state of the power rather than a problem to solve.
     */
    public static boolean pullInsideBorder(ServerPlayer player) {
        if (player.connection == null) return false;
        Vec3 target = pullBackTarget(player.position(), player.level().getWorldBorder());
        if (target == null) return false;
        player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
        return true;
    }

    /** {@link #pullBackTarget(Vec3, double, double, double, double)} against a live border. */
    @Nullable
    public static Vec3 pullBackTarget(Vec3 pos, WorldBorder border) {
        return pullBackTarget(pos, border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ());
    }

    /**
     * The position an escaped phasing player should be moved to, or null when
     * they are inside the border and must not be touched.
     *
     * <p>Triggers on the player's own position leaving the bounds, not on the
     * hitbox crossing them: a player standing legitimately against the inside
     * of the border has part of their box flush with the face, and nudging them
     * for that would teleport people who never phased anywhere.
     */
    @Nullable
    static Vec3 pullBackTarget(Vec3 pos, double minX, double maxX, double minZ, double maxZ) {
        if (within(pos.x, minX, maxX) && within(pos.z, minZ, maxZ)) return null;
        return new Vec3(containAxis(pos.x, minX, maxX), pos.y, containAxis(pos.z, minZ, maxZ));
    }

    /** Vanilla's own bounds test for one axis ({@code WorldBorder.isWithinBounds}). */
    private static boolean within(double value, double min, double max) {
        return value >= min && value < max;
    }

    /**
     * Clamps one axis to {@link #PULL_BACK_INSET} inside the border faces. A
     * border narrower than two insets has no such band, so the centre is used.
     */
    static double containAxis(double value, double min, double max) {
        double low = min + PULL_BACK_INSET;
        double high = max - PULL_BACK_INSET;
        if (low > high) return (min + max) / 2.0;
        return Mth.clamp(value, low, high);
    }
}
