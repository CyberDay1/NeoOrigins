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
    public int getMaxLifetime() { return maxLifetime; }
    public void setMaxLifetime(int ticks) { this.maxLifetime = Math.max(1, ticks); }

    public float getLifetimeProgress() {
        return maxLifetime <= 0 ? 0f : Math.min(1.0f, (float) lifetime / maxLifetime);
    }

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

    /** Per-tick server-side behaviour. Default is no-op. */
    protected void onVfxTick(ServerLevel level) {}

    /** Called once just before discard when lifetime runs out. Default is no-op. */
    protected void onExpire(ServerLevel level) {}

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
