package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tornado VFX entity — pulls entities inward, lifts them, and spins them
 * tangentially, with optional damage at interval. Visual is entirely
 * server-emitted spiral particles; no mesh is used (the original
 * CybersBuildASpell tornado used a 8k-line GeckoLib-animated model that
 * doesn't port cleanly; the particle spiral reads well on its own and is
 * more customizable via {@code particle_type}).
 *
 * <p>Ported concept from CybersBuildASpell's {@code TornadoEntity},
 * rebased on NeoOrigins's {@link AbstractVfxEntity} so lifetime/caster/
 * range/effect-type plumbing comes for free.
 *
 * <p>Spawn via {@code neoorigins:spawn_tornado}.
 */
public class TornadoVfxEntity extends AbstractVfxEntity {

    private static final float DEFAULT_PULL_STRENGTH = 1.0f;
    private static final float DEFAULT_LIFT_STRENGTH = 0.5f;
    private static final float DEFAULT_SPIN_STRENGTH = 0.5f;
    private static final float DEFAULT_DAMAGE_PER_INTERVAL = 2.0f;
    private static final int DEFAULT_DAMAGE_INTERVAL = 10;

    private float pullStrength = DEFAULT_PULL_STRENGTH;
    private float liftStrength = DEFAULT_LIFT_STRENGTH;
    private float spinStrength = DEFAULT_SPIN_STRENGTH;
    private float damagePerInterval = DEFAULT_DAMAGE_PER_INTERVAL;
    private int damageIntervalTicks = DEFAULT_DAMAGE_INTERVAL;

    public TornadoVfxEntity(EntityType<? extends TornadoVfxEntity> type, Level level) {
        super(type, level);
    }

    public void setPullStrength(float value) { this.pullStrength = value; }
    public void setLiftStrength(float value) { this.liftStrength = value; }
    public void setSpinStrength(float value) { this.spinStrength = value; }
    public void setDamagePerInterval(float value) { this.damagePerInterval = value; }
    public void setDamageIntervalTicks(int ticks) { this.damageIntervalTicks = Math.max(1, ticks); }

    @Override
    protected void onVfxTick(ServerLevel level) {
        final float range = getRange();
        final boolean damageTick = damagePerInterval > 0 && getLifetime() % damageIntervalTicks == 0;
        final float damageRange = range * 0.5f;

        // Single pass — pull/lift/spin every tick; damage-in-cone on interval
        // ticks when inside the inner radius. Merging avoids a second scan.
        for (Entity target : level.getEntities(this, getBoundingBox().inflate(range, range * 2, range))) {
            if (target == this) continue;
            if (casterUuid != null && target.getUUID().equals(casterUuid)) continue;

            Vec3 toCenter = position().subtract(target.position());
            double dist = toCenter.horizontalDistance();
            if (dist > range || dist < 0.5) continue;

            double factor = 1.0 - dist / range;
            Vec3 pull = toCenter.normalize().scale(pullStrength * factor * 0.1);
            Vec3 lift = new Vec3(0, liftStrength * factor * 0.05, 0);
            Vec3 tangent = new Vec3(-toCenter.z, 0, toCenter.x).normalize();
            Vec3 spin = tangent.scale(spinStrength * factor * 0.1);

            target.setDeltaMovement(target.getDeltaMovement().add(pull).add(lift).add(spin));
            target.hurtMarked = true;

            if (damageTick && dist <= damageRange) {
                target.hurtServer(level, damageSources().magic(), damagePerInterval);
            }
        }

        if (getLifetime() % 15 == 0) {
            level.playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.5f);
        }

        // Spiral particles — cloud column with a helical twist. Particles
        // are emitted every other tick; their spread encodes the cone shape.
        if (getLifetime() % 2 == 0) {
            double angle = (getLifetime() * 0.6) % (Math.PI * 2);
            for (int h = 0; h < 4; h++) {
                double y = h * (range * 0.6);
                double r = range * (0.3 + h * 0.15);
                double px = Math.cos(angle + h * 1.2) * r;
                double pz = Math.sin(angle + h * 1.2) * r;
                level.sendParticles(ParticleTypes.CLOUD,
                    getX() + px, getY() + y, getZ() + pz,
                    2, range * 0.1, range * 0.1, range * 0.1, 0.05);
            }
        }
    }
}
