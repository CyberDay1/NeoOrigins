package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Lingering AoE entity — sits at a fixed position and, every {@code interval}
 * ticks, runs a stored {@link EntityAction} against the caster (with the
 * entity's position acting as the spatial origin for any area_of_effect the
 * action contains).
 *
 * <p>Typical use: "drop a poison cloud at the impact point of my projectile,"
 * "plant a healing rune that ticks every 2 seconds," "spawn a darkness aura
 * that applies Blindness to nearby mobs on an interval."
 *
 * <p>The stored action is <b>not persisted</b> across server restarts — the
 * entity saves its lifetime + range + effect-type but loses the action on
 * reload. This is acceptable because lingering areas are short-lived (seconds,
 * not hours) and pack authors expecting multi-session persistence should use
 * a different pattern.
 *
 * <p>Particles are emitted by {@link #onVfxTick(ServerLevel)} using an
 * {@link #particleType} chosen at spawn time. Default is a cloud based on
 * the entity's effect type; subclass or set the field explicitly for custom.
 */
public class LingeringAreaEntity extends AbstractVfxEntity {

    private EntityAction intervalAction;
    private int intervalTicks = 20;
    private int ticksUntilNext;
    private net.minecraft.core.particles.ParticleOptions particleType = ParticleTypes.WITCH;
    private int particlesPerEmit = 3;

    public LingeringAreaEntity(EntityType<? extends LingeringAreaEntity> type, Level level) {
        super(type, level);
    }

    /** Session-only — not persisted across save/load. */
    public void setIntervalAction(EntityAction action) {
        this.intervalAction = action;
    }

    public void setIntervalTicks(int ticks) {
        this.intervalTicks = Math.max(1, ticks);
        this.ticksUntilNext = this.intervalTicks;
    }

    public void setParticleType(net.minecraft.core.particles.ParticleOptions particle) {
        this.particleType = particle;
    }

    public void setParticlesPerEmit(int count) {
        this.particlesPerEmit = Math.max(0, count);
    }

    @Override
    protected void onVfxTick(ServerLevel level) {
        // Emit lingering particles every 2 ticks — dense enough to read as a "cloud."
        if (lifetime % 2 == 0 && particlesPerEmit > 0) {
            float r = getRange();
            emitParticles(particleType, particlesPerEmit, r * 0.5, r * 0.2, r * 0.5);
        }

        // Run the stored action every {@code intervalTicks}.
        if (intervalAction == null) return;
        if (--ticksUntilNext > 0) return;
        ticksUntilNext = intervalTicks;

        ServerPlayer caster = resolveCaster();
        if (caster == null) return; // caster offline — skip this tick's execution
        try {
            intervalAction.execute(caster);
        } catch (Exception e) {
            com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                "[vfx] lingering-area interval action failed: {}", e.getMessage());
        }
    }
}
