package com.cyberday1.neoorigins.api.content.vfx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
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
 * <p>See {@code docs/CUSTOM_PROJECTILES.md} for the full extension guide.
 * This file is the 1.21.1 variant (uses {@code CompoundTag} save/load);
 * the 26.1 variant uses {@code ValueInput} / {@code ValueOutput}. Public
 * method signatures are identical across both versions so pack-author
 * code compiles unchanged.
 *
 * <p>API status: stable. Added in 2.0.
 */
public abstract class AbstractVfxEntity extends Entity {

    protected static final EntityDataAccessor<Float> DATA_RANGE =
        SynchedEntityData.defineId(AbstractVfxEntity.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<String> DATA_EFFECT_TYPE =
        SynchedEntityData.defineId(AbstractVfxEntity.class, EntityDataSerializers.STRING);
    // Synced so the client renderer's timing (fall start, fade-out, self-discard)
    // matches the server's actual lifetime. Left as a plain field, the client
    // keeps the default and renderers desync from server-side effects.
    protected static final EntityDataAccessor<Integer> DATA_MAX_LIFETIME =
        SynchedEntityData.defineId(AbstractVfxEntity.class, EntityDataSerializers.INT);

    @Nullable protected UUID casterUuid;
    protected int lifetime;

    protected AbstractVfxEntity(EntityType<? extends AbstractVfxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RANGE, 3.0f);
        builder.define(DATA_EFFECT_TYPE, "");
        builder.define(DATA_MAX_LIFETIME, 100);
    }

    public float getRange() { return entityData.get(DATA_RANGE); }
    public void setRange(float range) { entityData.set(DATA_RANGE, range); }
    public String getEffectType() { return entityData.get(DATA_EFFECT_TYPE); }
    public void setEffectType(String type) { entityData.set(DATA_EFFECT_TYPE, type == null ? "" : type); }
    @Nullable public UUID getCasterUuid() { return casterUuid; }
    public void setCaster(@Nullable UUID casterUuid) { this.casterUuid = casterUuid; }

    @Nullable
    public ServerPlayer resolveCaster() {
        if (casterUuid == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(casterUuid);
    }

    public int getLifetime() { return lifetime; }
    public int getMaxLifetime() { return entityData.get(DATA_MAX_LIFETIME); }
    public void setMaxLifetime(int ticks) { entityData.set(DATA_MAX_LIFETIME, Math.max(1, ticks)); }

    public float getLifetimeProgress() {
        int max = getMaxLifetime();
        return max <= 0 ? 0f : Math.min(1.0f, (float) lifetime / max);
    }

    @Override
    public void tick() {
        super.tick();
        lifetime++;
        if (lifetime >= getMaxLifetime()) {
            if (level() instanceof ServerLevel sl) onExpire(sl);
            discard();
            return;
        }
        if (level() instanceof ServerLevel sl) onVfxTick(sl);
    }

    /** Per-tick server-side behaviour. Default is no-op. */
    protected void onVfxTick(ServerLevel level) {}

    /** Called once just before discard when lifetime runs out. Default is no-op. */
    protected void onExpire(ServerLevel level) {}

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /**
     * VFX renderers draw well outside the entity's own (tiny) hitbox — blades
     * raining 7+ blocks up, tornado columns several times {@link #getRange()}
     * tall, AoE rings out to the full range. The default culling box is just the
     * entity dimensions, so as soon as the bare origin point leaves the frustum
     * the whole effect is culled and {@code render()} never runs (the
     * "I can't see the swords" bug). Inflate the culling box to cover the full
     * visual envelope: generous horizontally around the origin and tall upward.
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        double r = getRange() * 4.0 + 8.0;
        double up = getRange() * 8.0 + 16.0;
        return new net.minecraft.world.phys.AABB(
            getX() - r, getY() - 4.0, getZ() - r,
            getX() + r, getY() + up, getZ() + r);
    }

    /** Always render VFX within a generous radius — the tiny hitbox would
     *  otherwise make the vanilla size-based distance check cull them early. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 96.0 * 96.0;
    }

    protected void emitParticles(ParticleOptions particle, int count,
                                 double xSpread, double ySpread, double zSpread) {
        if (count <= 0) return;
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(particle, getX(), getY(), getZ(), count, xSpread, ySpread, zSpread, 0.0);
    }

    // Save/load stubs — VFX entities are short-lived (seconds), persistence
    // across server restarts isn't useful. The save/load signature drifts
    // between MC versions so we keep this minimal.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
