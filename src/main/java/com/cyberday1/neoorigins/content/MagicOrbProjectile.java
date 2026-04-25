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
}
