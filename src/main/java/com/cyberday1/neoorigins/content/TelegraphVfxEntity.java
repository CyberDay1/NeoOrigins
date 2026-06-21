package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Standalone "ground telegraph" VFX entity — a particle-only danger marker that
 * draws a static outer AoE ring plus a reticle ring contracting toward the
 * center over its lifetime, marking a dodgeable landing zone. When it expires it
 * optionally runs a composable impact action against entities inside its radius.
 *
 * <p>This is the {@code spawn_sword_rain} lead-in telegraph extracted into a
 * reusable building block: pack authors can drop a wind-up marker anywhere
 * (origin/look/impact) and wire {@code on_expire} to any action — damage,
 * spawn_sword_rain, explode, apply_effect, … — so the telegraph and the payoff
 * are composed in the datapack rather than hard-wired together.
 *
 * <p>No renderer is registered: the visual is entirely server-emitted
 * particles, so this entity needs no model and no client class. Like its VFX
 * siblings it is not persisted across restarts (short-lived by design).
 *
 * <p>1.21.1 variant; the 26.1/26.2 twins share this public shape.
 */
public class TelegraphVfxEntity extends AbstractVfxEntity {

    /**
     * Composable effect run once at the marker's center when the wind-up ends.
     * Set server-side at cast time from the action's {@code on_expire} field;
     * {@code null} means the telegraph is purely cosmetic (a pure dodge marker).
     */
    public interface OnExpire {
        void apply(ServerLevel level, Vec3 pos, float radius, ServerPlayer caster);
    }

    private transient OnExpire expireCallback = null;

    public TelegraphVfxEntity(EntityType<? extends TelegraphVfxEntity> type, Level level) {
        super(type, level);
    }

    public void setOnExpire(OnExpire cb) { this.expireCallback = cb; }

    @Override
    protected void onVfxTick(ServerLevel level) {
        double cx = getX(), cy = getY(), cz = getZ();
        double range = getRange();
        int max = getMaxLifetime();
        float progress = max <= 0 ? 1f : Math.min(1f, getLifetime() / (float) max);

        // Outer danger ring: static marker of the full AoE footprint.
        int outerPoints = Math.max(16, (int) (range * 6));
        for (int p = 0; p < outerPoints; p++) {
            double a = p / (double) outerPoints * Math.PI * 2.0;
            double x = cx + Math.cos(a) * range;
            double z = cz + Math.sin(a) * range;
            level.sendParticles(ParticleTypes.SMOKE, x, cy + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Contracting reticle ring: shrinks from the rim toward the center as the
        // wind-up completes, drawing the eye to the impact point.
        double reticleR = range * (1.0 - progress * 0.85);
        int reticlePoints = Math.max(10, (int) (reticleR * 6));
        for (int p = 0; p < reticlePoints; p++) {
            double a = p / (double) reticlePoints * Math.PI * 2.0;
            double x = cx + Math.cos(a) * reticleR;
            double z = cz + Math.sin(a) * reticleR;
            level.sendParticles(ParticleTypes.CRIT, x, cy + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onExpire(ServerLevel level) {
        if (expireCallback == null) return;
        expireCallback.apply(level, position(), getRange(), resolveCaster());
    }
}
