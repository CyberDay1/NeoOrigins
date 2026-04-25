package com.cyberday1.neoorigins.api.content.vfx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base class for non-moving visual-effect entities — lingering AoEs,
 * ground markers, barriers, black holes, tornados. Handles lifetime,
 * caster tracking, range, and effect-type colour, so subclasses only
 * own their unique behaviour (on-tick action, render pattern, expiry
 * effect).
 *
 * <p><b>What subclasses get for free:</b>
 * <ul>
 *   <li>{@link #DATA_RANGE} and {@link #DATA_EFFECT_TYPE} synched fields
 *       — renderer reads via {@link #getRange()}, {@link #getEffectType()}</li>
 *   <li>Lifetime countdown — {@link #tick()} expires the entity automatically</li>
 *   <li>Caster UUID persistence via {@code CasterUUID} NBT key</li>
 *   <li>{@link #emitParticles(ParticleOptions, int, double, double, double)}
 *       helper for server-emitted particles with spread</li>
 *   <li>{@link #hurtServer(ServerLevel, DamageSource, float)} always returns
 *       false — VFX entities ignore damage</li>
 *   <li>{@code noPhysics = true} + no gravity by default — stay where placed</li>
 * </ul>
 *
 * <p><b>What subclasses override:</b>
 * <ul>
 *   <li>{@link #onVfxTick(ServerLevel)} — per-tick server behaviour (damage
 *       entities in radius, apply effect, whatever). Default is no-op.</li>
 *   <li>{@link #onExpire(ServerLevel)} — called once when lifetime runs out,
 *       before discard. Default is no-op.</li>
 *   <li>Constructor that takes the {@link EntityType} and {@link Level} per
 *       vanilla convention, plus a secondary constructor for spawn-time
 *       configuration.</li>
 * </ul>
 *
 * <p>For moving projectiles, extend
 * {@link com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile}
 * instead — it handles physics + on-impact semantics rather than lingering.
 *
 * <p>API status: stable. Added in 2.0.
 */
public abstract class AbstractVfxEntity extends Entity {

    protected static final EntityDataAccessor<Float> DATA_RANGE =
        SynchedEntityData.defineId(AbstractVfxEntity.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<String> DATA_EFFECT_TYPE =
        SynchedEntityData.defineId(AbstractVfxEntity.class, EntityDataSerializers.STRING);

    @Nullable protected UUID casterUuid;
    protected int lifetime;
    protected int maxLifetime = 100;

    protected AbstractVfxEntity(EntityType<? extends AbstractVfxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RANGE, 3.0f);
        builder.define(DATA_EFFECT_TYPE, "");
    }

    // ─── Accessors ───────────────────────────────────────────────────────

    /** Radius in blocks; read by the renderer + by subclasses for in-range checks. */
    public float getRange() { return entityData.get(DATA_RANGE); }

    /** Set the radius; syncs to clients. Call after spawn but before addFreshEntity if possible. */
    public void setRange(float range) { entityData.set(DATA_RANGE, range); }

    /** Effect type key — see {@link VfxEffectTypes#get(String)} for the color lookup. */
    public String getEffectType() { return entityData.get(DATA_EFFECT_TYPE); }

    /** Set the effect type; syncs to clients. */
    public void setEffectType(String type) { entityData.set(DATA_EFFECT_TYPE, type == null ? "" : type); }

    /** Caster (nullable — may be offline, or may not be a player). */
    @Nullable public UUID getCasterUuid() { return casterUuid; }

    public void setCaster(@Nullable UUID casterUuid) { this.casterUuid = casterUuid; }

    /** Shorthand — resolve the caster to a currently-online {@link ServerPlayer}, or null. */
    @Nullable
    public ServerPlayer resolveCaster() {
        if (casterUuid == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(casterUuid);
    }

    /** Ticks since spawn. Renderers use this + partialTick for smooth animation. */
    public int getLifetime() { return lifetime; }

    /** Max ticks before auto-despawn. Subclasses set via {@link #setMaxLifetime(int)}. */
    public int getMaxLifetime() { return maxLifetime; }

    public void setMaxLifetime(int ticks) { this.maxLifetime = Math.max(1, ticks); }

    /** 0.0–1.0, how close to expiry. Useful for fade-out visuals. */
    public float getLifetimeProgress() {
        return maxLifetime <= 0 ? 0f : Math.min(1.0f, (float) lifetime / maxLifetime);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        lifetime++;
        if (lifetime >= maxLifetime) {
            if (level() instanceof ServerLevel sl) onExpire(sl);
            discard();
            return;
        }
        if (level() instanceof ServerLevel sl) onVfxTick(sl);
    }

    /**
     * Per-tick server-side behaviour. Runs on every tick except the expiry
     * tick. Default is no-op — subclasses apply damage / effects / particles.
     */
    protected void onVfxTick(ServerLevel level) {}

    /**
     * Called once, just before discard, when the entity's lifetime runs out.
     * Default is no-op — subclasses can play an expiry sound, emit a final
     * particle burst, etc.
     */
    protected void onExpire(ServerLevel level) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    // ─── Particle helper ─────────────────────────────────────────────────

    /**
     * Emit {@code count} particles spread around the entity's position.
     * Short-circuits when not on a server level. No-op if count ≤ 0.
     *
     * @param particle  the {@link ParticleOptions} to emit
     * @param count     how many particles
     * @param xSpread   per-axis ± spread in blocks
     * @param ySpread   per-axis ± spread in blocks
     * @param zSpread   per-axis ± spread in blocks
     */
    protected void emitParticles(ParticleOptions particle, int count,
                                 double xSpread, double ySpread, double zSpread) {
        if (count <= 0) return;
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(particle, getX(), getY(), getZ(), count, xSpread, ySpread, zSpread, 0.0);
    }

    // ─── Save / load ─────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        lifetime = input.getIntOr("Lifetime", 0);
        maxLifetime = input.getIntOr("MaxLifetime", 100);
        entityData.set(DATA_RANGE, input.getFloatOr("Range", getRange()));
        input.getString("EffectType").ifPresent(s -> entityData.set(DATA_EFFECT_TYPE, s));
        input.getString("CasterUUID").ifPresent(s -> {
            try { casterUuid = UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        });
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Lifetime", lifetime);
        output.putInt("MaxLifetime", maxLifetime);
        output.putFloat("Range", getRange());
        output.putString("EffectType", getEffectType());
        if (casterUuid != null) output.putString("CasterUUID", casterUuid.toString());
    }
}
