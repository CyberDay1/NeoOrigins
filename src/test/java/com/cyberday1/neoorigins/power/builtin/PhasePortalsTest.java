package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.rig.HeadlessWorld;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wraith in phase could not use a nether portal, and could not use an end
 * portal either. One cause: {@code Entity.move} answers {@code noPhysics} with
 * {@code setPos} and returns, skipping the {@code tryCheckInsideBlocks} call in
 * its tail — and {@code BlockState.entityInside} is the only thing that ever
 * starts a {@code portalProcess}.
 *
 * <p>Driven through the real {@code NetherPortalBlock.entityInside} on a real
 * {@code Player} in the {@link HeadlessWorld} rig, and asserted on vanilla's own
 * {@code portalProcess} field, so a green means the portal actually claimed the
 * player rather than that our scan found a block.
 */
// hub: neoorigins/phase-portals.md
class PhasePortalsTest {

    /** A player standing at the centre of the block at (0, 64, 0). */
    private static HeadlessWorld.RigPlayer playerIn(HeadlessWorld world) {
        return world.spawnAt(0.5, 64.0, 0.5);
    }

    @Test
    void aNetherPortalClaimsAPhasingPlayer() {
        HeadlessWorld world = new HeadlessWorld()
            .place(0, 64, 0, Blocks.NETHER_PORTAL.defaultBlockState());
        HeadlessWorld.RigPlayer player = playerIn(world);
        assertTrue(player.isAlive(), "the rig player has to be alive to use a portal at all");
        assertNull(player.portalProcess, "nothing should have claimed the player yet");

        PhasePortals.offerPortalsInside(player);

        assertNotNull(player.portalProcess,
            "the nether portal overlapping the hitbox should have started a portal process");
    }

    @Test
    void anEndPortalClaimsAPhasingPlayer() {
        HeadlessWorld world = new HeadlessWorld()
            .place(0, 64, 0, Blocks.END_PORTAL.defaultBlockState());
        HeadlessWorld.RigPlayer player = playerIn(world);

        PhasePortals.offerPortalsInside(player);

        assertNotNull(player.portalProcess,
            "the end portal was the half of the report that was only suspected");
    }

    @Test
    void ordinaryBlocksClaimNobody() {
        HeadlessWorld world = new HeadlessWorld().fill(0, 64, 0, 0, 64, 0);
        HeadlessWorld.RigPlayer player = playerIn(world);

        PhasePortals.offerPortalsInside(player);

        assertNull(player.portalProcess, "phasing through stone is not entering a portal");
    }

    @Test
    void aPortalOutsideTheHitboxClaimsNobody() {
        HeadlessWorld world = new HeadlessWorld()
            .place(4, 64, 0, Blocks.NETHER_PORTAL.defaultBlockState());
        HeadlessWorld.RigPlayer player = playerIn(world);

        PhasePortals.offerPortalsInside(player);

        assertNull(player.portalProcess, "a portal four blocks away must not reach the player");
    }

    @Test
    void onlyPortalBlocksAreOffered() {
        assertTrue(PhasePortals.isPortal(Blocks.NETHER_PORTAL.defaultBlockState()));
        assertTrue(PhasePortals.isPortal(Blocks.END_PORTAL.defaultBlockState()));
        assertTrue(PhasePortals.isPortal(Blocks.END_GATEWAY.defaultBlockState()));
        // The inside-block check vanilla skips also drives these. Restoring it
        // wholesale would hand a phasing wraith hurt and snare effects it has
        // never had while incorporeal, so the scan stays portals-only.
        assertFalse(PhasePortals.isPortal(Blocks.CACTUS.defaultBlockState()));
        assertFalse(PhasePortals.isPortal(Blocks.SWEET_BERRY_BUSH.defaultBlockState()));
        assertFalse(PhasePortals.isPortal(Blocks.COBWEB.defaultBlockState()));
        assertFalse(PhasePortals.isPortal(Blocks.FIRE.defaultBlockState()));
        assertFalse(PhasePortals.isPortal(Blocks.POWDER_SNOW.defaultBlockState()));
        assertFalse(PhasePortals.isPortal(Blocks.STONE.defaultBlockState()));
        assertFalse(PhasePortals.isPortal(Blocks.AIR.defaultBlockState()));
    }
}
