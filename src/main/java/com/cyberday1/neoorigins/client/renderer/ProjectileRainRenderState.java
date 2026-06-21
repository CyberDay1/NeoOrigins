package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Render state for {@link com.cyberday1.neoorigins.content.ProjectileRainVfxEntity}.
 * The 26.2 submit pipeline only hands the renderer a state object, so all the
 * per-blade choreography inputs the 1.21.1 {@code render()} loop read straight
 * off the entity are snapshotted here in {@code extractRenderState}: the blade
 * count, the model id, the entity Y, and per-blade landing ticks / local offsets
 * / ground Ys. {@code submit} then reproduces the fall/fade/orientation math from
 * these arrays.
 */
public class ProjectileRainRenderState extends AbstractVfxRenderState {
    /** Whether to draw the choreographed baked-mesh blades (false in real-projectile mode). */
    public boolean renderBlades;
    /** Number of blades in the storm. */
    public int count;
    /** Entity max lifetime (ticks) — drives the fade-out window. */
    public float maxLifetime;
    /** Baked-mesh model id the blades use. */
    public String model = "sword";
    /** Storm-center Y, used to convert per-blade ground Y into a render delta. */
    public double entityY;
    /** Per-blade landing tick (relative to spawn). */
    public float[] landingTicks = new float[0];
    /** Per-blade horizontal offset from the center (y = 0). */
    public Vec3[] localOffsets = new Vec3[0];
    /** Per-blade ground Y (terrain-follow surface, or the center Y). */
    public double[] groundYs = new double[0];
}
