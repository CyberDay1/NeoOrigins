package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Generic color-keyed magic-orb projectile. Physics inherited from
 * {@link AbstractNeoProjectile} (throwable with gravity + drag). Visuals
 * delegated to {@link com.cyberday1.neoorigins.api.content.vfx.ProceduralQuadRenderer}
 * subclass via the {@code effect_type} synched field — pack authors choose
 * the color theme from JSON without writing Java.
 *
 * <p>Impact behavior: no intrinsic damage — this is a visual vehicle. The
 * real effect runs via the {@code on_hit_action} registered at spawn time
 * (see {@code spawn_projectile}). The projectile is discarded after impact
 * per {@link AbstractNeoProjectile}.
 *
 * <p>Set {@link #DATA_EFFECT_TYPE} via {@link #setEffectType(String)} right
 * after construction so renderers see the right color on first sync.
 */
public class MagicOrbProjectile extends AbstractNeoProjectile {

    public static final EntityDataAccessor<String> DATA_EFFECT_TYPE =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.STRING);

    public MagicOrbProjectile(EntityType<? extends MagicOrbProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EFFECT_TYPE, "magic");
    }

    public String getEffectType() { return entityData.get(DATA_EFFECT_TYPE); }
    public void setEffectType(String type) {
        entityData.set(DATA_EFFECT_TYPE, type == null || type.isEmpty() ? "magic" : type);
    }

    @Override
    protected Item getVisualItem() {
        // Fallback if the renderer somehow isn't registered — plain snowball so
        // the entity at least shows up rather than being invisible.
        return Items.SNOWBALL;
    }

    @Override
    protected void onImpact(ServerLevel level, HitResult result) {
        // No intrinsic impact behavior — the DSL-side on_hit_action handles
        // damage/effects. ProjectileActionRegistry drains the action in
        // CombatPowerEvents.onProjectileImpact independently of this hook.
    }

    @Override
    public void tick() {
        super.tick();
        // Emit a particle trail keyed to the synched effect_type so pack
        // authors get a visible flight trail without writing Java. Server-side
        // sendParticles broadcasts to all viewers.
        if (this.level() instanceof ServerLevel sl && this.tickCount > 0) {
            net.minecraft.core.particles.ParticleOptions particle = trailParticle(getEffectType());
            if (particle != null) {
                sl.sendParticles(particle,
                    this.getX(), this.getY(), this.getZ(),
                    2,
                    0.05, 0.05, 0.05,
                    0.0);
            }
        }
    }

    /**
     * Map the synched effect_type to a vanilla particle for the flight trail.
     * Picked to read as the matching status effect at a glance: poison →
     * lingering green wisp, fire → flame, magic → witch sparkle, etc.
     * Returns null to suppress particles for unknown types.
     */
    private static net.minecraft.core.particles.ParticleOptions trailParticle(String effectType) {
        if (effectType == null) return null;
        return switch (effectType) {
            case "poison" -> net.minecraft.core.particles.ParticleTypes.EFFECT;
            case "fire", "flame" -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case "soul", "soul_fire" -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
            case "ice", "snow" -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
            case "void", "ender" -> net.minecraft.core.particles.ParticleTypes.PORTAL;
            case "magic" -> net.minecraft.core.particles.ParticleTypes.WITCH;
            default -> net.minecraft.core.particles.ParticleTypes.WITCH;
        };
    }
}
