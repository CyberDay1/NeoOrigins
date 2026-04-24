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
 * Stationary gravity-well VFX entity. Pulls entities in radius toward its
 * center and damages anything in the inner radius every 10 ticks.
 *
 * <p>Ported from CybersBuildASpell's {@code BlackHoleEntity} and adapted to
 * NeoOrigins's VFX foundation — inherits lifetime/caster/range/particle
 * helpers from {@link AbstractVfxEntity}, so the entity-specific code
 * stays minimal (~60 lines of novel logic).
 *
 * <p>Spawn via {@code neoorigins:spawn_black_hole} action. Pack authors
 * configure radius, pull strength, damage-per-tick, and duration from JSON.
 */
public class BlackHoleVfxEntity extends AbstractVfxEntity {

    private static final float DEFAULT_PULL_STRENGTH = 1.5f;
    private static final float DEFAULT_DAMAGE_PER_TICK = 2.0f;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final float DAMAGE_RADIUS_FRACTION = 0.3f;

    private float pullStrength = DEFAULT_PULL_STRENGTH;
    private float damagePerTick = DEFAULT_DAMAGE_PER_TICK;
    private boolean initialSoundPlayed;

    public BlackHoleVfxEntity(EntityType<? extends BlackHoleVfxEntity> type, Level level) {
        super(type, level);
    }

    public void setPullStrength(float value) { this.pullStrength = value; }
    public void setDamagePerTick(float value) { this.damagePerTick = value; }

    @Override
    protected void onVfxTick(ServerLevel level) {
        if (!initialSoundPlayed) {
            level.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 0.5f);
            initialSoundPlayed = true;
        }

        final float range = getRange();
        final boolean damageTick = getLifetime() % DAMAGE_INTERVAL_TICKS == 0;
        final float damageRadius = range * DAMAGE_RADIUS_FRACTION;

        // Single pass — pull every tick, damage on the interval tick when inside
        // the inner radius. Merging avoids a second getEntities() scan.
        for (Entity target : level.getEntities(this, getBoundingBox().inflate(range))) {
            if (target == this) continue;
            Vec3 toward = position().subtract(target.position());
            double dist = toward.length();
            if (dist <= 0.5 || dist >= range) continue;

            double strength = pullStrength * (1.0 - dist / range);
            Vec3 pull = toward.normalize().scale(strength * 0.1);
            target.setDeltaMovement(target.getDeltaMovement().add(pull));
            target.hurtMarked = true;

            if (damageTick && dist <= damageRadius
                    && (casterUuid == null || !target.getUUID().equals(casterUuid))) {
                target.hurtServer(level, damageSources().magic(), damagePerTick);
            }
        }

        if (getLifetime() % 20 == 0) {
            level.playSound(null, blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.5f);
        }
        if (getLifetime() % 2 == 0) {
            emitParticles(ParticleTypes.PORTAL, 5, range * 0.5, range * 0.5, range * 0.5);
        }
    }
}
