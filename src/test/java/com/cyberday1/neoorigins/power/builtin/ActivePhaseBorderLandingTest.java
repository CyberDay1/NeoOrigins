package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code active_phase} landed a player on the centre of a block that passed the
 * border test only by its lower corner. {@code /worldborder center 0 0} centres
 * the border on 0.5, so its faces sit at half blocks and that block's centre is
 * on the face itself.
 */
// hub: neoorigins/phase-world-border.md
class ActivePhaseBorderLandingTest {

    /** A player's hitbox half-width. */
    private static final double HALF_WIDTH = 0.3;

    /** Centred on (0.5, 0.5), 100 across: faces at x/z = -49.5 and 50.5. */
    private static WorldBorder halfBlockBorder() {
        WorldBorder border = new WorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(100.0);
        return border;
    }

    private static boolean lands(int x, int z) {
        return ActivePhasePower.landsInsideBorder(halfBlockBorder(), new BlockPos(x, 64, z), HALF_WIDTH);
    }

    @Test
    void aBlockWhollyInsideTheBorderIsALandingSpot() {
        assertTrue(lands(49, 0), "landing at 49.5 keeps the hitbox inside the 50.5 face");
        assertTrue(lands(-49, 0), "landing at -48.5 keeps the hitbox inside the -49.5 face");
    }

    @Test
    void aBlockWhoseCentreIsOnTheFaceIsRefused() {
        WorldBorder border = halfBlockBorder();
        BlockPos edge = new BlockPos(50, 64, 0);
        assertTrue(border.isWithinBounds(edge), "the block's lower corner is inside: the old test passed it");
        assertFalse(ActivePhasePower.landsInsideBorder(border, edge, HALF_WIDTH),
            "landing at 50.5 puts the player on the face, half outside");
        assertFalse(lands(-50, 0), "landing at -49.5 is on the -X face");
        assertFalse(lands(0, 50), "the Z faces are tested too");
    }

    @Test
    void aBlockOutsideTheBorderIsRefused() {
        assertFalse(lands(51, 0));
        assertFalse(lands(0, -51));
    }
}
