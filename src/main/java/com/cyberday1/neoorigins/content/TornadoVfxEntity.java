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
 * 1.21.1 tornado VFX entity — same public shape as the 26.1 variant, with
 * {@code hurt} instead of {@code hurtServer} to match the 1.21.1 entity
 * API. See the 26.1 twin for full docs.
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
                target.hurt(damageSources().magic(), damagePerInterval);
            }
        }

        if (getLifetime() % 15 == 0) {
            level.playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.5f);
        }

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
