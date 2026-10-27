package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.rig.HeadlessWorld;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A phasing wraith walked out through the world border: the client moves with
 * {@code setPos} and the server runs under {@code noPhysics}, so neither end
 * reaches {@code Entity.collide}, which is the only place vanilla applies the
 * border collider.
 *
 * <p>Covers both halves of the fix — the movement clamp that stops the crossing
 * and the pull-back that recovers a player already outside. The clamp is asked
 * of a real {@code Player} standing in front of a real {@link
 * net.minecraft.world.level.border.WorldBorder} (see {@link HeadlessWorld}), so
 * the geometry is the game's own rather than a restatement of it.
 */
// hub: neoorigins/phase-world-border.md
class PhaseBoundsTest {

    /** Border 64 blocks across, centred on the origin: faces at ±32. */
    private static final double FACE = 32.0;

    private static HeadlessWorld world() {
        return new HeadlessWorld().borderSize(FACE * 2);
    }

    /** The barrier clamp the phasing client applies, with no blocked blocks in play. */
    private static Vec3 clamp(HeadlessWorld.RigPlayer player, Vec3 movement) {
        List<VoxelShape> shapes = new ArrayList<>();
        PhaseBounds.addBorderShape(player, movement, shapes);
        if (shapes.isEmpty()) return movement;
        return PhaseBounds.collideAgainstShapes(movement, player.getBoundingBox(), shapes);
    }

    @Test
    void phasingOutThroughTheBorderIsStopped() {
        HeadlessWorld world = world();
        HeadlessWorld.RigPlayer player = world.spawnAt(FACE - 1.0, 64, 0);

        Vec3 clamped = clamp(player, new Vec3(4.0, 0, 0));

        assertTrue(clamped.x < 4.0,
            () -> "expected the border to stop +X phasing, got " + clamped.x);
        assertTrue(player.getBoundingBox().move(clamped).maxX <= FACE + 1.0E-7,
            () -> "the hitbox ended up past the border face: "
                + player.getBoundingBox().move(clamped).maxX);
    }

    @Test
    void phasingBackInsideTheBorderIsNotStopped() {
        HeadlessWorld world = world();
        HeadlessWorld.RigPlayer player = world.spawnAt(FACE - 1.0, 64, 0);

        Vec3 clamped = clamp(player, new Vec3(-4.0, 0, 0));

        assertEquals(-4.0, clamped.x, 1.0E-9, "phasing away from the border must be untouched");
    }

    @Test
    void phasingFarFromTheBorderCostsNothing() {
        HeadlessWorld world = world();
        HeadlessWorld.RigPlayer player = world.spawnAt(0, 64, 0);

        List<VoxelShape> shapes = new ArrayList<>();
        PhaseBounds.addBorderShape(player, new Vec3(0.5, 0, 0), shapes);

        assertTrue(shapes.isEmpty(), "the border should contribute no shape when nowhere near it");
    }

    @Test
    void risingBesideTheBorderStillWorks() {
        HeadlessWorld world = world();
        HeadlessWorld.RigPlayer player = world.spawnAt(FACE - 0.3, 64, 0);

        Vec3 clamped = clamp(player, new Vec3(0, WraithPhasePower.PHASE_UP_VELOCITY, 0));

        assertEquals(WraithPhasePower.PHASE_UP_VELOCITY, clamped.y, 1.0E-9,
            "the border is a vertical wall; phasing up against it must still rise");
    }

    @Test
    void aPlayerInsideTheBorderIsLeftAlone() {
        assertNull(
            PhaseBounds.pullBackTarget(new Vec3(FACE - 0.1, 70, -5), world().worldBorder()),
            "standing legitimately against the inside of the border is not an escape");
    }

    @Test
    void anEscapedPlayerIsPulledBackKeepingTheirHeight() {
        Vec3 target = PhaseBounds.pullBackTarget(new Vec3(120.0, 70.0, -400.0), world().worldBorder());

        assertNotNull(target);
        assertEquals(FACE - PhaseBounds.PULL_BACK_INSET, target.x, 1.0E-9);
        assertEquals(-FACE + PhaseBounds.PULL_BACK_INSET, target.z, 1.0E-9);
        assertEquals(70.0, target.y, 1.0E-9,
            "phasing players keep their Y — arriving inside terrain is the power working");
    }

    @Test
    void onlyTheEscapedAxisMoves() {
        Vec3 target = PhaseBounds.pullBackTarget(new Vec3(FACE + 8.0, 70.0, 10.0), world().worldBorder());

        assertNotNull(target);
        assertEquals(FACE - PhaseBounds.PULL_BACK_INSET, target.x, 1.0E-9);
        assertEquals(10.0, target.z, 1.0E-9, "the axis still inside the border must not be nudged");
    }

    @Test
    void aBorderNarrowerThanTheInsetBandUsesItsCentre() {
        // Faces at ±0.25, so there is no "half a block inside" to aim for.
        assertEquals(0.0, PhaseBounds.containAxis(50.0, -0.25, 0.25), 1.0E-9);
    }
}
