package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Portal entry for phasing players.
 *
 * <p>A wraith in phase could not use a nether portal, and could not use an end
 * portal either, for the same reason it walked through the world border
 * ({@link PhaseBounds}): {@code Entity.move} answers {@code noPhysics} with
 * {@code setPos} and returns, so the whole body of the method is skipped. Portal
 * entry lives in its tail — {@code tryCheckInsideBlocks}, which offers every
 * block overlapping the hitbox to {@code BlockState.entityInside}. That call is
 * the ONLY thing that ever creates a {@code portalProcess}:
 * {@code NetherPortalBlock} and {@code EndPortalBlock} do nothing but
 * {@code setAsInsidePortal} from it. No inside-block check, no portal.
 *
 * <p>So the check is re-run here, for portal blocks and nothing else. Running
 * all of it would also hand a phasing wraith the cactus, fire, sweet berry and
 * cobweb effects they have never had while incorporeal, which is a bigger
 * change than the report asks for and arguably the wrong one. "Portal block"
 * means {@link Portal}, the interface the three vanilla portals implement, so a
 * mod's portal that implements it is covered too.
 */
// hub: neoorigins/phase-portals.md
public final class PhasePortals {

    private PhasePortals() {}

    /** True if {@code state} is a portal, the one kind of block offered below. */
    static boolean isPortal(BlockState state) {
        return state.getBlock() instanceof Portal;
    }

    /**
     * Offers every portal block overlapping {@code player}'s hitbox to its own
     * {@code entityInside}, as {@code Entity.checkInsideBlocks} would have.
     *
     * <p>The scan bounds are vanilla's, down to the 1.0E-7 inset, so a phasing
     * player needs exactly the overlap an ordinary one needs — the end portal in
     * particular tests the hitbox against its own shape before accepting.
     */
    public static void offerPortalsInside(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        BlockPos min = BlockPos.containing(box.minX + 1.0E-7, box.minY + 1.0E-7, box.minZ + 1.0E-7);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7);
        if (!level.hasChunksAt(min, max)) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (!player.isAlive()) return;
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (isPortal(state)) {
                        // 26.x asks for an effect applier and an "is this a
                        // precise overlap" flag. No vanilla portal reads either:
                        // they only call setAsInsidePortal. NOOP keeps us from
                        // queueing anything into vanilla's own collector out of
                        // turn, and the scan above already establishes the overlap.
                        state.entityInside(level, pos, player,
                            InsideBlockEffectApplier.NOOP, true);
                    }
                }
            }
        }
    }
}
