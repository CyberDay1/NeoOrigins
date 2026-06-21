package com.cyberday1.neoorigins.client.renderer;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Render state for {@link com.cyberday1.neoorigins.content.ThrownSwordProjectile}.
 * The 26.2 submit pipeline hands the renderer only a state object, so the
 * velocity (for aim), fallback rotations and tick count (for spin) the 1.21.1
 * {@code render()} read off the entity are snapshotted here in
 * {@code extractRenderState}.
 */
public class ThrownSwordRenderState extends EntityRenderState {
    /** Current velocity components — the blade's tip aims along this. */
    public double velX, velY, velZ;
    /** Fallback rotations used when velocity is ~zero. */
    public float fallbackYaw, fallbackPitch;
    /** Ticks since spawn — drives the spin about the travel axis. */
    public int age;
}
