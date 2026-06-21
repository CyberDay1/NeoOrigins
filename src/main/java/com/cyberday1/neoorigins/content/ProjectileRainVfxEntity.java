package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "Ten Thousand Swords" VFX entity — a ring of spectral blades that rain from
 * the sky and stab into the ground around the center, each landing staggered
 * over the first part of the lifetime so the storm reads as a sequence of
 * thunks rather than one instant pop.
 *
 * <p>Server side this entity owns the staggered damage: as each blade lands it
 * hits living entities near that blade's ground point (excluding the caster)
 * and launches them. Standing in the thick of the ring means more blades land
 * near you, so the damage naturally scales with how exposed the victim is.
 *
 * <p>The matching {@code ProjectileRainRenderer} reproduces the same per-blade ring
 * placement and landing timing purely from {@link #getLifetime()} /
 * {@link #getMaxLifetime()} / {@link #getSwordCount()} / {@link #getRange()},
 * so client visuals and server hits stay in lockstep with no extra sync.
 *
 * <p>26.1/26.2 variant (uses {@code hurtServer}); the 1.21.1 twin uses
 * {@code hurt}. Public method shape is identical across versions.
 */
public class ProjectileRainVfxEntity extends AbstractVfxEntity {

    private static final int DEFAULT_SWORD_COUNT = 24;
    private static final float DEFAULT_DAMAGE_PER_SWORD = 4.0f;
    private static final float DEFAULT_KNOCKUP = 0.5f;
    private static final float DEFAULT_IMPACT_RADIUS = 2.0f;
    private static final int DEFAULT_TELEGRAPH_TICKS = 14;
    /** Fraction of the lifetime (after the telegraph) over which blades land. */
    private static final float LANDING_WINDOW_FRACTION = 0.85f;

    // Synced to the client: the renderer reads swordCount + telegraphTicks (via
    // landingTick) to place and time every blade, so they must match the server.
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_SWORD_COUNT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            ProjectileRainVfxEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_TELEGRAPH_TICKS =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            ProjectileRainVfxEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    // Which baked model the renderer draws for each falling projectile. Synced so
    // the same action can rain swords, arrows, meteors, … by id (see
    // ProjectileRainRenderer's model registry). Default "sword".
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_MODEL =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            ProjectileRainVfxEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    public static final String DEFAULT_MODEL = "sword";
    // When false the client skips the fake baked-mesh blades entirely — used in
    // "real projectile" mode (a BladeLauncher spawns actual entities that fall
    // under real physics, so the choreographed blade render would double up).
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_RENDER_BLADES =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            ProjectileRainVfxEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    // When true each scatter point drops onto the actual terrain height beneath it
    // (heightmap lookup at that x/z) instead of all landing on the center's flat
    // Y plane, so the storm follows hills/valleys. Synced so the client renderer
    // and the server impacts pick the same per-blade ground Y with no extra data.
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_FOLLOW_TERRAIN =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            ProjectileRainVfxEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    // Server-only (damage application happens in onVfxTick, server side).
    private float damagePerSword = DEFAULT_DAMAGE_PER_SWORD;
    private float knockup = DEFAULT_KNOCKUP;
    private float impactRadius = DEFAULT_IMPACT_RADIUS;
    /** Flat per-blade damage bonus captured from the caster's weapon at cast time. */
    private float weaponDamageBonus = 0f;

    /** Which blades have already stabbed (landing times are random, not ordered). */
    private final java.util.BitSet firedBlades = new java.util.BitSet();

    /**
     * Composable per-blade effect run at each blade's ground point when it lands.
     * Set server-side at cast time from the action's {@code impact_action} field;
     * when {@code null} the entity falls back to its built-in damage + knockup, so
     * blades spawned without a parser (e.g. {@code /summon}) still hit.
     */
    public interface BladeImpact {
        void apply(ServerLevel level, Vec3 pos, float impactRadius, ServerPlayer caster);
    }

    private transient BladeImpact impactCallback = null;
    public void setImpactCallback(BladeImpact cb) { this.impactCallback = cb; }

    /**
     * "Real projectile" launcher: when set (from the action's {@code projectile}
     * field) each blade spawns an actual registered entity high above its ground
     * point that then falls under real physics, instead of the choreographed
     * baked-mesh blade. The rain still owns the scatter pattern + staggered
     * landing schedule + telegraph; this just swaps the fake visual for a real,
     * caster-owned entity whose on-hit runs the {@code impact_action}. When
     * {@code null} the entity keeps its built-in fake-blade behaviour.
     */
    public interface BladeLauncher {
        void launch(ServerLevel level, Vec3 groundPos, ServerPlayer caster);
    }

    private transient BladeLauncher launchCallback = null;
    public void setLaunchCallback(BladeLauncher cb) { this.launchCallback = cb; }

    public ProjectileRainVfxEntity(net.minecraft.world.entity.EntityType<? extends ProjectileRainVfxEntity> type,
                              net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SWORD_COUNT, DEFAULT_SWORD_COUNT);
        builder.define(DATA_TELEGRAPH_TICKS, DEFAULT_TELEGRAPH_TICKS);
        builder.define(DATA_MODEL, DEFAULT_MODEL);
        builder.define(DATA_RENDER_BLADES, true);
        builder.define(DATA_FOLLOW_TERRAIN, true);
    }

    public void setSwordCount(int value) { entityData.set(DATA_SWORD_COUNT, Math.max(1, value)); }
    public void setDamagePerSword(float value) { this.damagePerSword = value; }
    public void setKnockup(float value) { this.knockup = value; }
    public void setImpactRadius(float value) { this.impactRadius = Math.max(0.1f, value); }
    public void setTelegraphTicks(int value) { entityData.set(DATA_TELEGRAPH_TICKS, Math.max(0, value)); }
    public void setWeaponDamageBonus(float value) { this.weaponDamageBonus = Math.max(0f, value); }

    public void setModel(String value) { entityData.set(DATA_MODEL, value == null || value.isEmpty() ? DEFAULT_MODEL : value); }
    public void setRenderBlades(boolean value) { entityData.set(DATA_RENDER_BLADES, value); }
    public void setFollowTerrain(boolean value) { entityData.set(DATA_FOLLOW_TERRAIN, value); }

    public int getSwordCount() { return entityData.get(DATA_SWORD_COUNT); }
    public int getTelegraphTicks() { return entityData.get(DATA_TELEGRAPH_TICKS); }
    public String getModel() { return entityData.get(DATA_MODEL); }
    public boolean shouldRenderBlades() { return entityData.get(DATA_RENDER_BLADES); }
    public boolean shouldFollowTerrain() { return entityData.get(DATA_FOLLOW_TERRAIN); }

    /**
     * Tick (relative to spawn) at which blade {@code i} stabs into the ground.
     * Offset past {@link #getTelegraphTicks()} so the "sword shadow" telegraph plays
     * first, then each blade lands at a deterministic-random time scattered
     * across the landing window — so the storm reads as blades raining at random
     * moments over the duration, not a clean sweep. Deterministic per blade so
     * the renderer's visual thunk stays locked to the server's damage tick.
     */
    public int landingTick(int i) {
        int telegraph = getTelegraphTicks();
        float window = Math.max(1f, (getMaxLifetime() - telegraph) * LANDING_WINDOW_FRACTION);
        double h = frac(Math.sin(i * 33.17 + 0.6) * 17321.13);
        return telegraph + 1 + (int) Math.round(h * window);
    }

    /**
     * Horizontal impact offset of blade {@code i} from the center (y = 0).
     * Scattered across the whole disk (deterministic per-blade hash, sqrt-radius
     * for uniform area density) rather than a stamped ring, so the storm reads as
     * a meteor shower of blades raining down at random points — the matching
     * {@code ProjectileRainRenderer} streaks each blade in from its own random angle.
     */
    public Vec3 bladeLocalOffset(int i) {
        double hAngle = frac(Math.sin(i * 12.9898) * 43758.5453);
        double hRad   = frac(Math.sin(i * 78.233 + 1.7) * 12543.7531);
        double angle = hAngle * Math.PI * 2.0;
        // sqrt → uniform spread over the disk area (not bunched at the center).
        double r = getRange() * Math.sqrt(hRad);
        return new Vec3(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }

    /** Ground position of blade {@code i}: its scatter point's x/z, dropped onto
     *  the terrain height there when follow-terrain is on (else the center Y). */
    public Vec3 bladeGroundPos(int i) {
        Vec3 local = bladeLocalOffset(i);
        double x = getX() + local.x;
        double z = getZ() + local.z;
        return new Vec3(x, bladeGroundY(i), z);
    }

    /**
     * Terrain height under blade {@code i}'s scatter point. With follow-terrain on
     * this is the surface Y from the heightmap (so the storm hugs hills/valleys);
     * off, every blade shares the center's Y plane (the old flat-disk behaviour).
     * Computed from synced data + the shared heightmap, so the client renderer and
     * the server impacts agree without sending a Y per blade.
     */
    public double bladeGroundY(int i) {
        if (!shouldFollowTerrain()) return getY();
        Vec3 local = bladeLocalOffset(i);
        int bx = net.minecraft.util.Mth.floor(getX() + local.x);
        int bz = net.minecraft.util.Mth.floor(getZ() + local.z);
        // MOTION_BLOCKING is maintained on BOTH server and client (sent in chunk
        // packets), so both sides resolve the same surface Y. getHeight returns the
        // y just above the top blocking block — i.e. ground level where a blade
        // tip meets the surface.
        return level().getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    private static double frac(double v) { return v - Math.floor(v); }

    @Override
    protected void onVfxTick(ServerLevel level) {
        // Lead-in: play the "sword shadow" ground telegraph before any blade
        // falls, so the storm reads as a marked, dodgeable landing zone.
        if (getLifetime() < getTelegraphTicks()) {
            emitTelegraph(level);
            return;
        }

        // Fire each blade's impact as the lifetime crosses its (random) landing
        // tick. Landing times aren't ordered by index, so scan all unfired blades.
        for (int i = 0; i < getSwordCount(); i++) {
            if (!firedBlades.get(i) && landingTick(i) <= getLifetime()) {
                if (launchCallback != null) {
                    // Real-projectile mode: spawn an actual entity from the sky
                    // over this blade's ground point. Its own on-hit (registered
                    // by the launcher) runs the impact_action — the rain just
                    // chose where and when.
                    launchCallback.launch(level, bladeGroundPos(i), resolveCaster());
                } else {
                    impactBlade(level, i);
                }
                firedBlades.set(i);
            }
        }
    }

    /**
     * Ground "sword shadow" telegraph drawn during the lead-in: a dark outer
     * AoE ring marking the danger zone, a reticle ring that contracts toward the
     * center as the wind-up completes, and a faint sword-silhouette line across
     * the disk. A single throw/whoosh cue plays on the final lead-in tick so the
     * rain reads as "thrown" right before the blades land.
     */
    private void emitTelegraph(ServerLevel level) {
        double cx = getX(), cy = getY(), cz = getZ();
        double range = getRange();
        // Lead-in progress 0→1 across the telegraph window.
        int telegraph = getTelegraphTicks();
        float progress = telegraph <= 0 ? 1f : getLifetime() / (float) telegraph;

        // Outer danger ring: static marker of the full AoE footprint.
        int outerPoints = Math.max(16, (int) (range * 6));
        for (int p = 0; p < outerPoints; p++) {
            double a = p / (double) outerPoints * Math.PI * 2.0;
            double x = cx + Math.cos(a) * range;
            double z = cz + Math.sin(a) * range;
            level.sendParticles(ParticleTypes.SMOKE, x, cy + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Contracting reticle ring: shrinks from the rim to the center as the
        // wind-up finishes, drawing the eye to where the blades will rain.
        double reticleR = range * (1.0 - progress * 0.85);
        int reticlePoints = Math.max(10, (int) (reticleR * 6));
        for (int p = 0; p < reticlePoints; p++) {
            double a = p / (double) reticlePoints * Math.PI * 2.0;
            double x = cx + Math.cos(a) * reticleR;
            double z = cz + Math.sin(a) * reticleR;
            level.sendParticles(ParticleTypes.CRIT, x, cy + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Sword-silhouette line across the disk (the "shadow" of the blade).
        for (int s = -8; s <= 8; s++) {
            double t = s / 8.0;
            double x = cx + t * range * 0.9;
            level.sendParticles(ParticleTypes.SMOKE, x, cy + 0.05, cz, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Throw cue on the final lead-in tick.
        if (getLifetime() == telegraph - 1) {
            level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 0.6f);
        } else if (getLifetime() % 4 == 0) {
            level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(),
                SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    private void impactBlade(ServerLevel level, int i) {
        Vec3 pos = bladeGroundPos(i);

        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.1, pos.z, 12, 0.2, 0.1, 0.2, 0.25);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.3, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP,
            SoundSource.PLAYERS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.3f);

        // Composable path: a datapack-supplied impact_action replaces the built-in
        // damage. The blade's thunk visuals/sound above always play regardless.
        if (impactCallback != null) {
            impactCallback.apply(level, pos, impactRadius, resolveCaster());
            return;
        }

        float bladeDamage = damagePerSword + weaponDamageBonus;
        if (bladeDamage <= 0) return;

        ServerPlayer caster = resolveCaster();
        DamageSource source = caster != null
            ? damageSources().playerAttack(caster)
            : damageSources().magic();

        double r = impactRadius;
        var box = new net.minecraft.world.phys.AABB(
            pos.x - r, pos.y - 1.5, pos.z - r, pos.x + r, pos.y + 2.5, pos.z + r);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (casterUuid != null && target.getUUID().equals(casterUuid)) continue;
            double dx = target.getX() - pos.x;
            double dz = target.getZ() - pos.z;
            if (dx * dx + dz * dz > r * r) continue;

            target.hurtServer(level, source, bladeDamage);
            if (knockup > 0) {
                target.setDeltaMovement(target.getDeltaMovement().add(0, knockup, 0));
                target.hurtMarked = true;
            }
        }
    }
}
