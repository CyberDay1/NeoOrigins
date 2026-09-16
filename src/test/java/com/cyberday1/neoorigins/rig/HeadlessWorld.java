package com.cyberday1.neoorigins.rig;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A real {@link Player} standing in a hand-built block world, driven headlessly.
 *
 * <p>The player, its attribute map, every {@link BlockState} and all the collision
 * maths are the game's own — and the JUnit harness is FML-bootstrapped, so our
 * mixins are applied to {@code Player} exactly as they are in game. Only the chunk
 * storage is replaced: block lookups answer from {@link #blocks} instead of a chunk
 * source, which is what lets a world exist without a server.
 *
 * <p>That makes {@code move()} the real thing — {@code Entity#move} runs vanilla's
 * {@code maybeBackOffFromEdge}, {@code isAboveGround}, {@code canFallAtLeast} and
 * {@code collide} against real voxel shapes, and the resulting position is the
 * position the game would produce.
 */
// hub: neoorigins/headless-world-rig.md
public final class HeadlessWorld {

    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Level level;

    public HeadlessWorld() {
        this.level = Mockito.mock(Level.class);
        Mockito.when(level.isClientSide()).thenReturn(false);
        Mockito.when(level.getProfiler())
               .thenReturn(net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        Mockito.when(level.getWorldBorder()).thenReturn(new WorldBorder());
        Mockito.when(level.getEntityCollisions(Mockito.any(), Mockito.any())).thenReturn(List.of());
        Mockito.when(level.isLoaded(Mockito.any(BlockPos.class))).thenReturn(true);
        Mockito.when(level.hasChunkAt(Mockito.any(BlockPos.class))).thenReturn(true);
        Mockito.when(level.getBlockState(Mockito.any(BlockPos.class)))
               .thenAnswer(inv -> stateAt(inv.getArgument(0)));
        Mockito.when(level.getFluidState(Mockito.any(BlockPos.class)))
               .thenReturn(Fluids.EMPTY.defaultFluidState());
        Mockito.when(level.noCollision(Mockito.any(Entity.class), Mockito.any(AABB.class)))
               .thenAnswer(inv -> shapesOverlapping(inv.getArgument(1)).isEmpty());
        Mockito.when(level.getBlockCollisions(Mockito.any(), Mockito.any(AABB.class)))
               .thenAnswer(inv -> shapesOverlapping(inv.getArgument(1)));
    }

    public Level level() {
        return level;
    }

    private BlockState stateAt(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    /** Fills a solid stone slab of blocks, inclusive on every axis. */
    public HeadlessWorld fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
        return this;
    }

    /**
     * Every real collision shape intersecting {@code box}, in world space. Both
     * {@code noCollision} and {@code getBlockCollisions} are answered from this, so
     * the two can never disagree about what is solid.
     */
    private List<VoxelShape> shapesOverlapping(AABB box) {
        List<VoxelShape> out = new ArrayList<>();
        for (int x = (int) Math.floor(box.minX) - 1; x <= (int) Math.floor(box.maxX) + 1; x++) {
            for (int y = (int) Math.floor(box.minY) - 1; y <= (int) Math.floor(box.maxY) + 1; y++) {
                for (int z = (int) Math.floor(box.minZ) - 1; z <= (int) Math.floor(box.maxZ) + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    VoxelShape shape = stateAt(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
                    if (shape.isEmpty()) continue;
                    VoxelShape moved = shape.move(x, y, z);
                    if (Shapes.joinIsNotEmpty(moved, Shapes.create(box), BooleanOp.AND)) {
                        out.add(moved);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Whether the player still has floor beneath them at vanilla step depth — i.e.
     * whether vanilla would consider them above ground rather than in free fall.
     * This is the question "did they walk off the ledge?", asked of the world rather
     * than of a coordinate, so it does not care how far vanilla let them overhang.
     */
    public boolean standingOnSolidGround(Player player) {
        AABB box = player.getBoundingBox();
        AABB under = new AABB(box.minX, box.minY - 0.6 - 1.0E-5F, box.minZ,
                              box.maxX, box.minY, box.maxZ);
        return !shapesOverlapping(under).isEmpty();
    }

    /** A real {@code Player} in this world, standing at {@code (x, y, z)} on the ground. */
    public RigPlayer spawnAt(double x, double y, double z) {
        RigPlayer player = new RigPlayer(level);
        player.setPos(x, y, z);
        player.setOnGround(true);
        return player;
    }

    /**
     * Concrete {@link Player} — the class is abstract only for the two game-mode
     * predicates below, so nothing that matters to movement is overridden here.
     */
    public static final class RigPlayer extends Player {
        RigPlayer(Level level) {
            super(level, BlockPos.ZERO, 0f, new GameProfile(UUID.randomUUID(), "rig"));
        }

        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }

        /** Walks one tick's worth of movement, exactly as {@code Player#travel} would. */
        public void walk(double dx, double dz) {
            move(MoverType.SELF, new Vec3(dx, 0.0, dz));
        }
    }
}
