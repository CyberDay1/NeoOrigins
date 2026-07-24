package com.cyberday1.neoorigins.compat.sable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Thin, Sable-safe dispatcher for teleport-base fixup on Create Aeronautics /
 * Sable "sub-level" airships. This class carries NO Sable imports of its own, so
 * it always classloads; every Sable-typed reference lives in the nested
 * {@link Impl} holder, which is only ever touched behind a {@code ModList.isLoaded("sable")}
 * gate. That keeps {@code NoClassDefFoundError} off the table on installs without Sable.
 *
 * <h2>The bug this fixes</h2>
 * A Sable airship's blocks physically live in the host {@link ServerLevel} at
 * plotyard/staging coordinates (near the plot origin, ~10000,10000). The visible ship
 * is a pose-transformed projection of that staging grid. A rider's server-side
 * {@code position()} is ALREADY the visible WORLD position (Sable's
 * {@code entities_stick_sublevels} mixin re-derives it from a separate
 * {@code sable$plotPosition} field every tick), so relative/self-anchored teleports do
 * not need any coordinate conversion — they only need the rider un-pinned from the deck
 * so the move is not snapped back next tick (see {@link #detachFromDeck}).
 *
 * <p>The real fault is the AIMED teleport. {@code player.pick()} raycasts from the
 * player's WORLD eye, but Sable's {@code clip_overwrite} mixin resolves the hit against
 * the ship's blocks in STAGING space and returns a {@link net.minecraft.world.phys.HitResult}
 * whose location is in that staging space (~10000,10000) — never transformed back to
 * world. Teleporting to that raw hit drops the player into the empty sub-level region,
 * causing an infinite fall / crash (issue #115, #88). The fix: lift the staging-space
 * hit to the equivalent visible-ship world point via the ship's pose
 * ({@link #toWorld}) before teleporting, and detach from the deck.
 *
 * <p>The pose maps plotyard/staging space -> visible world space, so
 * {@code pose.transformPosition(hitStaging)} is the visible-ship world point. When
 * Sable is absent (or the entity is not on a sub-level) {@link #toWorld} returns its
 * argument unchanged and {@link #detachFromDeck} is a no-op, so the existing on-foot
 * behaviour is byte-identical.
 */
public final class SableTeleportCompat {

    private SableTeleportCompat() {}

    /** Cached so we don't hit the mod list on every teleport dispatch. */
    private static final boolean SABLE_LOADED = ModList.get().isLoaded("sable");

    /**
     * Lift a raycast hit {@code staging} to world space if it landed on a Sable ship.
     * Used by the aimed "teleport to where you're looking" ability: {@code player.pick()}
     * casts from the player's WORLD eye, but Sable's {@code clip_overwrite} resolves a hit
     * on the ship against the ship's blocks in STAGING space and returns that staging-space
     * location (~10000,10000). This lifts such a hit back to the visible-ship world point
     * so the player lands on the ship rather than falling into the empty sub-level region.
     * A hit that landed on real terrain (a world-space point, no owning sub-level) is
     * returned unchanged. Also returns {@code staging} unchanged when Sable is absent.
     */
    public static Vec3 toWorld(Entity entity, Vec3 staging) {
        if (!SABLE_LOADED) return staging;
        return Impl.toWorld(entity, staging);
    }

    /**
     * True when {@code entity} is currently standing on a Sable sub-level airship.
     * Always false when Sable is absent.
     */
    public static boolean isOnSubLevel(Entity entity) {
        if (!SABLE_LOADED) return false;
        return Impl.isOnSubLevel(entity);
    }

    /**
     * Un-pin {@code entity} from any Sable deck it is stuck to (so it can be freely
     * teleported into the world instead of being snapped back onto the plotyard deck
     * next tick). No-op when Sable is absent or the entity is not on a sub-level.
     */
    public static void detachFromDeck(Entity entity) {
        if (!SABLE_LOADED) return;
        Impl.detachFromDeck(entity);
    }

    /**
     * All Sable-typed access is isolated here. Never referenced unless
     * {@link #SABLE_LOADED} is true, so the JVM never has to link these Sable classes
     * on an install without the mod.
     */
    private static final class Impl {

        /** A sub-level whose bounds lie within this many blocks of the entity's feet is "the ship it's on". */
        private static final double SHIP_SEARCH_RADIUS = 6.0D;

        private Impl() {}

        static Vec3 toWorld(Entity entity, Vec3 staging) {
            if (!(entity.level() instanceof ServerLevel level)) return staging;
            // projectOutOfSubLevel keys off the point itself: if `staging` lies inside a
            // sub-level's staging region (e.g. a player.pick() hit that Sable's
            // clip_overwrite resolved against the ship's blocks) it is lifted to the
            // equivalent visible-ship world position via that sub-level's pose. If
            // `staging` is already a world-space point (a raycast that hit real terrain
            // rather than the ship) getContaining finds no sub-level and it is returned
            // unchanged, so aiming off-ship at the real world still teleports correctly.
            return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, staging);
        }

        static boolean isOnSubLevel(Entity entity) {
            if (!(entity.level() instanceof ServerLevel level)) return false;
            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return false;
            return findRiddenSubLevel(container, entity.position()) != null;
        }

        static void detachFromDeck(Entity entity) {
            if (!(entity.level() instanceof ServerLevel level)) return;
            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return;
            if (findRiddenSubLevel(container, entity.position()) == null) return;
            ((dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension)
                (Object) entity).sable$setPlotPosition(null);
        }

        /**
         * The ridden ship is the sub-level whose world bounds lie within
         * {@link #SHIP_SEARCH_RADIUS} of the entity's feet; if several qualify, the one
         * whose centre is nearest wins. Mirrors WormHoleNeo's
         * {@code findPilotedSubLevel}.
         */
        private static dev.ryanhcode.sable.sublevel.ServerSubLevel findRiddenSubLevel(
                dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container, Vec3 feet) {
            net.minecraft.world.phys.AABB probe =
                new net.minecraft.world.phys.AABB(feet.x, feet.y, feet.z, feet.x, feet.y, feet.z)
                    .inflate(SHIP_SEARCH_RADIUS);
            dev.ryanhcode.sable.sublevel.ServerSubLevel best = null;
            double bestDistSq = Double.MAX_VALUE;
            for (dev.ryanhcode.sable.sublevel.ServerSubLevel sl : container.getAllSubLevels()) {
                if (sl.isRemoved() || !sl.boundingBox().intersects(probe)) continue;
                org.joml.Vector3d c = sl.boundingBox().center();
                double dx = c.x - feet.x;
                double dy = c.y - feet.y;
                double dz = c.z - feet.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = sl;
                }
            }
            return best;
        }
    }
}
