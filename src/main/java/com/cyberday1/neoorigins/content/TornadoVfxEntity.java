package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * Funnel size + spin multipliers, applied on top of the base design radius
     * ({@link #getRange()}). Shared with {@code TornadoRenderer} so the visual
     * funnel and the server-side pull/damage field stay the same shape: the
     * affected area widens with {@link #WIDTH_MULT} and the column rises with
     * {@link #HEIGHT_MULT}, while {@link #SPIN_MULT} only speeds the render spin.
     */
    public static final float WIDTH_MULT = 7.5f;
    public static final float HEIGHT_MULT = 7.0f;
    public static final float SPIN_MULT = 3.0f;

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

    /** Horizontal travel: the funnel drifts in the cast direction each tick. */
    private Vec3 moveDir = Vec3.ZERO;
    private float moveSpeed = 0f;

    /** When true the funnel falls under gravity until its base rests on the
     *  ground, so a tornado cast in mid-air drops while it drifts forward. */
    private boolean applyGravity = false;
    private double fallVel = 0.0;
    /** Downward acceleration per tick, capped at a sane terminal velocity. */
    private static final double GRAVITY_PER_TICK = 0.08;
    private static final double TERMINAL_VELOCITY = -3.0;
    /** Per-tick fraction the funnel base lerps toward the surface height while
     *  riding terrain. Lower = smoother/laggier glide, higher = snappier. This
     *  is what turns stepped elevation changes into a smooth ride over hills. */
    private static final double GROUND_FOLLOW_LERP = 0.2;
    /** Height above the surface beyond which a gravity funnel is still airborne
     *  (falling); at or below it switches to riding the ground contour. */
    private static final double GROUND_CONTACT_EPS = 0.5;

    /**
     * Composable payload run against each caught entity on the damage interval,
     * in place of the built-in magic damage. Set by the {@code impact_action}
     * field on spawn_tornado; null = use the hardcoded {@code damagePerInterval}.
     */
    public interface TornadoImpact {
        void apply(ServerLevel level, Entity target, ServerPlayer caster);
    }
    private TornadoImpact impactCallback = null;
    public void setImpactCallback(TornadoImpact cb) { this.impactCallback = cb; }

    public TornadoVfxEntity(EntityType<? extends TornadoVfxEntity> type, Level level) {
        super(type, level);
    }

    public void setPullStrength(float value) { this.pullStrength = value; }
    public void setLiftStrength(float value) { this.liftStrength = value; }
    public void setSpinStrength(float value) { this.spinStrength = value; }
    public void setDamagePerInterval(float value) { this.damagePerInterval = value; }
    public void setDamageIntervalTicks(int ticks) { this.damageIntervalTicks = Math.max(1, ticks); }

    /**
     * Set the horizontal drift: {@code dir} is flattened to the XZ plane and
     * normalised, then advanced {@code speedPerTick} blocks each tick. A zero or
     * vertical direction leaves the funnel stationary.
     */
    public void setMoveDirection(Vec3 dir, float speedPerTick) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        double len = flat.length();
        this.moveDir = len < 1.0e-4 ? Vec3.ZERO : flat.scale(1.0 / len);
        this.moveSpeed = Math.max(0f, speedPerTick);
    }

    /** When enabled, the funnel falls under gravity (and keeps drifting forward)
     *  until its base reaches the surface, then rides along the ground. */
    public void setGravity(boolean value) { this.applyGravity = value; }

    @Override
    protected void onVfxTick(ServerLevel level) {
        // Drift forward in the cast direction (and fall under gravity when
        // enabled) before scanning, so the pull field and particles use the
        // funnel's new position this tick.
        double newX = getX() + (moveSpeed > 0 ? moveDir.x * moveSpeed : 0.0);
        double newZ = getZ() + (moveSpeed > 0 ? moveDir.z * moveSpeed : 0.0);
        double newY = getY();

        // A drifting (or gravity-enabled) funnel rides the terrain; a stationary
        // non-gravity funnel hovers at its spawn height as before.
        if (moveSpeed > 0 || applyGravity) {
            // Ground height under the funnel's new XZ. VFX entities are noPhysics
            // → no vanilla collision, so we scan for a floor manually. Use the
            // LOCAL floor beneath the funnel (first solid block scanning down from
            // its own height), NOT the MOTION_BLOCKING world-surface heightmap:
            // cast inside a cave, the surface heightmap points at open sky far
            // above, so the follow-lerp below would climb the funnel straight up
            // and out of the cave. A local downward scan finds the cave floor (or,
            // on the surface, the surface itself — same result out in the open).
            double groundY = localFloorY(level, newX, newY, newZ);

            if (applyGravity && newY > groundY + GROUND_CONTACT_EPS) {
                // Spawned above the ground: fall under gravity until the base lands.
                fallVel = Math.max(TERMINAL_VELOCITY, fallVel - GRAVITY_PER_TICK);
                newY += fallVel;
                if (newY <= groundY) {
                    newY = groundY;
                    fallVel = 0.0;
                }
            } else {
                // Grounded: ride the contour smoothly. Lerp the base toward the
                // surface height so elevation steps glide instead of snapping —
                // this is what keeps the forward drift smooth over hills and dips.
                fallVel = 0.0;
                newY += (groundY - newY) * GROUND_FOLLOW_LERP;
                if (Math.abs(groundY - newY) < 0.01) newY = groundY;
            }
        }

        if (newX != getX() || newY != getY() || newZ != getZ()) {
            setPos(newX, newY, newZ);
        }

        final float baseRange = getRange();
        // Affected area widened to match the wider funnel; column height raised
        // to match the taller funnel (WIDTH_MULT / HEIGHT_MULT shared with the renderer).
        final float range = baseRange * WIDTH_MULT;
        final float columnHeight = baseRange * HEIGHT_MULT;
        // Fire on the interval when there's a payload to deliver — either the
        // built-in damage or a composable impact_action. (Gating on damage alone
        // would suppress an impact_action set with damage_per_interval = 0.)
        final boolean intervalTick = (damagePerInterval > 0 || impactCallback != null)
            && getLifetime() % damageIntervalTicks == 0;
        final float damageRange = range * 0.5f;
        final ServerPlayer impactCaster = (intervalTick && impactCallback != null) ? resolveCaster() : null;

        // Single pass — pull/lift/spin every tick; damage-in-cone on interval
        // ticks when inside the inner radius. Merging avoids a second scan.
        for (Entity target : level.getEntities(this, getBoundingBox().inflate(range, columnHeight, range))) {
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

            if (intervalTick && dist <= damageRange) {
                if (impactCallback != null) {
                    impactCallback.apply(level, target, impactCaster);
                } else {
                    target.hurt(damageSources().magic(), damagePerInterval);
                }
            }
        }

        if (getLifetime() % 15 == 0) {
            level.playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.5f);
        }

        if (getLifetime() % 2 == 0) {
            // Spin 3× faster to match the renderer, and stack 6 rings up the full
            // (taller) column so the particle funnel reads as tall as the mesh.
            double angle = (getLifetime() * 0.6 * SPIN_MULT) % (Math.PI * 2);
            for (int h = 0; h < 6; h++) {
                double y = (h / 6.0) * columnHeight;
                double r = range * (0.18 + h * 0.06);
                double px = Math.cos(angle + h * 1.2) * r;
                double pz = Math.sin(angle + h * 1.2) * r;
                level.sendParticles(ParticleTypes.CLOUD,
                    getX() + px, getY() + y, getZ() + pz,
                    2, range * 0.1, range * 0.1, range * 0.1, 0.05);
            }
        }
    }

    /**
     * Y of the first solid floor at or below the funnel's current height under
     * {@code (x, z)}. Scans downward from just above the funnel so a cave-cast
     * tornado settles on the cave floor instead of the {@code MOTION_BLOCKING}
     * world surface (open sky) — the latter made it climb out of the cave.
     * On the open surface the first block down is the surface, so overworld
     * terrain-following behaviour is unchanged. Returns the world floor if the
     * column is all air (void), so the funnel simply drops.
     */
    private double localFloorY(ServerLevel level, double x, double y, double z) {
        int xi = net.minecraft.util.Mth.floor(x);
        int zi = net.minecraft.util.Mth.floor(z);
        int minY = level.getMinBuildHeight();
        int startY = net.minecraft.util.Mth.floor(y) + 1;
        net.minecraft.core.BlockPos.MutableBlockPos pos =
            new net.minecraft.core.BlockPos.MutableBlockPos(xi, startY, zi);
        for (int yy = startY; yy >= minY; yy--) {
            pos.setY(yy);
            if (level.getBlockState(pos).blocksMotion()) {
                return yy + 1.0; // top face of the solid block
            }
        }
        return minY;
    }
}
