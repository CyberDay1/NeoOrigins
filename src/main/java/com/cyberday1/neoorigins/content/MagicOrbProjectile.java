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

    /** Sentinel for "field not set by JSON" — fall back to effect_type defaults. */
    public static final int COLOR_UNSET = Integer.MIN_VALUE;
    public static final float SIZE_UNSET = -1.0f;

    public static final EntityDataAccessor<String> DATA_EFFECT_TYPE =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.STRING);

    // ── Data-driven visual config (2.1). Each is COLOR_UNSET / SIZE_UNSET / -1
    // until set, meaning "use the effect_type default" — so the renderer resolves
    // explicit > effect_type > hardcoded. Packed colors are 0xRRGGBB ints. ──
    public static final EntityDataAccessor<Integer> DATA_ORB_COLOR =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> DATA_GLOW_COLOR =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> DATA_SIZE =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_GLOW_SIZE =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.FLOAT);
    /** Glow alpha 0–255, or -1 for the renderer default. */
    public static final EntityDataAccessor<Integer> DATA_GLOW_ALPHA =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.INT);
    /** Shape key: cross / cube / ring / sphere (empty = effect_type / cross default). */
    public static final EntityDataAccessor<String> DATA_SHAPE =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.STRING);
    /** Vanilla particle id for the flight trail (empty = effect_type default). */
    public static final EntityDataAccessor<String> DATA_TRAIL_PARTICLE =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> DATA_TRAIL_COUNT =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> DATA_TRAIL_SPREAD =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_TRAIL_SPEED =
        SynchedEntityData.defineId(MagicOrbProjectile.class, EntityDataSerializers.FLOAT);

    public MagicOrbProjectile(EntityType<? extends MagicOrbProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EFFECT_TYPE, "magic");
        builder.define(DATA_ORB_COLOR, COLOR_UNSET);
        builder.define(DATA_GLOW_COLOR, COLOR_UNSET);
        builder.define(DATA_SIZE, SIZE_UNSET);
        builder.define(DATA_GLOW_SIZE, SIZE_UNSET);
        builder.define(DATA_GLOW_ALPHA, -1);
        builder.define(DATA_SHAPE, "");
        builder.define(DATA_TRAIL_PARTICLE, "");
        builder.define(DATA_TRAIL_COUNT, 2);
        builder.define(DATA_TRAIL_SPREAD, 0.05f);
        builder.define(DATA_TRAIL_SPEED, 0.0f);
    }

    public String getEffectType() { return entityData.get(DATA_EFFECT_TYPE); }
    public void setEffectType(String type) {
        entityData.set(DATA_EFFECT_TYPE, type == null || type.isEmpty() ? "magic" : type);
    }

    // ── Visual-config accessors (COLOR_UNSET / SIZE_UNSET / -1 / "" = use default) ──
    public int getOrbColor() { return entityData.get(DATA_ORB_COLOR); }
    public void setOrbColor(int packed) { entityData.set(DATA_ORB_COLOR, packed); }
    public int getGlowColor() { return entityData.get(DATA_GLOW_COLOR); }
    public void setGlowColor(int packed) { entityData.set(DATA_GLOW_COLOR, packed); }
    public float getSize() { return entityData.get(DATA_SIZE); }
    public void setSize(float v) { entityData.set(DATA_SIZE, v); }
    public float getGlowSize() { return entityData.get(DATA_GLOW_SIZE); }
    public void setGlowSize(float v) { entityData.set(DATA_GLOW_SIZE, v); }
    public int getGlowAlpha() { return entityData.get(DATA_GLOW_ALPHA); }
    public void setGlowAlpha(int v) { entityData.set(DATA_GLOW_ALPHA, v); }
    public String getShape() { return entityData.get(DATA_SHAPE); }
    public void setShape(String v) { entityData.set(DATA_SHAPE, v == null ? "" : v); }
    public String getTrailParticle() { return entityData.get(DATA_TRAIL_PARTICLE); }
    public void setTrailParticle(String v) { entityData.set(DATA_TRAIL_PARTICLE, v == null ? "" : v); }
    public int getTrailCount() { return entityData.get(DATA_TRAIL_COUNT); }
    public void setTrailCount(int v) { entityData.set(DATA_TRAIL_COUNT, v); }
    public float getTrailSpread() { return entityData.get(DATA_TRAIL_SPREAD); }
    public void setTrailSpread(float v) { entityData.set(DATA_TRAIL_SPREAD, v); }
    public float getTrailSpeed() { return entityData.get(DATA_TRAIL_SPEED); }
    public void setTrailSpeed(float v) { entityData.set(DATA_TRAIL_SPEED, v); }

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
        // sendParticles broadcasts to all viewers; runs every tick on the
        // server only.
        if (this.level() instanceof ServerLevel sl && this.tickCount > 0) {
            net.minecraft.core.particles.ParticleOptions particle = resolveTrailParticle();
            if (particle != null) {
                float spread = getTrailSpread();
                sl.sendParticles(particle,
                    this.getX(), this.getY(), this.getZ(),
                    getTrailCount(),         // count
                    spread, spread, spread,  // spread
                    getTrailSpeed());        // speed
            }
        }
    }

    /**
     * Resolve the flight-trail particle: an explicit {@code trail_particle} JSON
     * id wins; otherwise fall back to the {@link #effectTypeTrailParticle} mapping
     * keyed on the synched effect_type. Returns null to suppress particles.
     */
    private net.minecraft.core.particles.ParticleOptions resolveTrailParticle() {
        String explicit = getTrailParticle();
        if (explicit != null && !explicit.isEmpty()) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(explicit);
            if (id != null) {
                var pt = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
                // Only simple (no-data) particle types are usable as ParticleOptions directly.
                if (pt instanceof net.minecraft.core.particles.ParticleOptions opt) {
                    return opt;
                }
            }
        }
        return effectTypeTrailParticle(getEffectType());
    }

    /**
     * Map the synched effect_type to a vanilla particle for the flight trail.
     * Picked to read as the matching status effect at a glance: poison →
     * lingering green wisp, fire → flame, magic → witch sparkle, etc.
     * Returns null to suppress particles for unknown types.
     */
    private static net.minecraft.core.particles.ParticleOptions effectTypeTrailParticle(String effectType) {
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
