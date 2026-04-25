package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reference implementation of {@link AbstractNeoProjectile} — a projectile
 * that gently steers toward the nearest living entity (excluding the owner)
 * while in flight, then applies magic damage to any entity it hits.
 *
 * <p>Demonstrates the "custom per-tick AI" pattern that the DSL-side
 * {@code spawn_projectile} + {@code on_hit_action} can't express. Pack
 * authors reference this by entity ID:
 * <pre>{@code
 * {
 *   "type": "neoorigins:spawn_projectile",
 *   "entity_type": "neoorigins:homing_projectile",
 *   "speed": 1.2
 * }
 * }</pre>
 *
 * <p>Tuning:
 * <ul>
 *   <li>Seek radius: {@value #SEEK_RADIUS} blocks around current position</li>
 *   <li>Steering strength: {@value #STEER_STRENGTH} — fraction of current speed
 *       blended toward target direction each tick. Higher = tighter turns.</li>
 *   <li>Impact damage: {@value #IMPACT_DAMAGE} hearts</li>
 * </ul>
 *
 * <p>Stays gentle on tuning so tracked players have counterplay (strafing,
 * blocking line-of-sight). Subclass to tighten these.
 */
public class HomingProjectile extends AbstractNeoProjectile {

    private static final double SEEK_RADIUS = 12.0;
    private static final double STEER_STRENGTH = 0.15;
    private static final float IMPACT_DAMAGE = 4.0f;

    public HomingProjectile(EntityType<? extends HomingProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getVisualItem() {
        return Items.ARROW;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved()) return;

        LivingEntity target = findNearestTarget();
        if (target == null) return;

        Vec3 current = this.getDeltaMovement();
        double speed = current.length();
        if (speed < 1e-4) return;

        Vec3 toTarget = target.getEyePosition().subtract(this.position()).normalize();
        // Blend current direction with toward-target direction at STEER_STRENGTH.
        Vec3 steered = current.normalize()
            .scale(1.0 - STEER_STRENGTH)
            .add(toTarget.scale(STEER_STRENGTH))
            .normalize()
            .scale(speed);
        this.setDeltaMovement(steered);
    }

    private LivingEntity findNearestTarget() {
        AABB box = this.getBoundingBox().inflate(SEEK_RADIUS);
        var owner = this.getOwner();
        LivingEntity best = null;
        double bestDistSq = SEEK_RADIUS * SEEK_RADIUS;
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == owner) continue;
            if (!candidate.isAlive()) continue;
            double distSq = this.position().distanceToSqr(candidate.getEyePosition());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = candidate;
            }
        }
        return best;
    }

    @Override
    protected void onImpact(ServerLevel level, HitResult result) {
        if (result instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity target) {
            target.hurt(level.damageSources().magic(), IMPACT_DAMAGE);
        }
        // on_hit_action from ProjectileActionRegistry is drained independently by
        // CombatPowerEvents.onProjectileImpact — no need to invoke it here.
    }
}
