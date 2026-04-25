package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Nearby matching entities flee the player.
 *
 * <p>{@code entity_types} accepts raw IDs ({@code "minecraft:creeper"}) and tag
 * references ({@code "#mymod:scary_to_florae"}).
 */
public class ScareEntitiesPower extends PowerType<ScareEntitiesPower.Config> {

    private static final double RANGE = 8.0;
    private static final int TICK_INTERVAL = 5;
    private static final double FLEE_SPEED = 1.3;

    public record Config(List<String> entityTypes, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % TICK_INTERVAL != 0) return;
        if (config.entityTypes().isEmpty()) return;

        AABB box = player.getBoundingBox().inflate(RANGE);
        for (Entity e : player.level().getEntities(player, box)) {
            // Mob covers PathfinderMob (zombies, etc.) AND WaterAnimal (cod,
            // salmon, squid, dolphin, ...). The earlier PathfinderMob-only
            // check silently filtered out every aquatic mob, leaving Abyssal
            // Scare Ocean / similar fish-fleeing powers as no-ops.
            if (!(e instanceof Mob mob)) continue;
            if (!(e instanceof LivingEntity le)) continue;
            boolean matches = false;
            for (String id : config.entityTypes()) {
                if (com.cyberday1.neoorigins.event.CombatPowerEvents.matchesEntityIdOrTag(le, id)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;
            // Drop aggro: NearestAttackableTargetGoal (or Endermite's attack goal)
            // will re-path toward the player on the next tick if we only set a
            // flee destination. Clearing the current target + last-hurt-by lets
            // our flee navigation stick. Hostile AI may re-acquire the target
            // on line of sight, but that's OK — we re-clear on the next tick.
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            if (mob.getLastHurtByMob() == player) {
                mob.setLastHurtByMob(null);
            }
            if (!mob.getNavigation().isDone()
                && mob.getNavigation().getTargetPos() != null
                && mob.getNavigation().getTargetPos().distSqr(player.blockPosition()) > RANGE * RANGE) {
                continue;
            }
            if (mob instanceof PathfinderMob pmob) {
                // Ground mobs: use vanilla's flee-pos helper + standard
                // pathfinding. This is the path that works reliably when the
                // mob has GroundPathNavigation.
                Vec3 away = DefaultRandomPos.getPosAway(pmob, 16, 7, player.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
                }
            } else {
                // Water mobs (cod / salmon / dolphin / squid / pufferfish / ...)
                // use WaterBoundPathNavigation, which silently fails to path
                // when the computed flee target is on land or otherwise
                // unreachable — leaving the mob frozen in place. Push them
                // away directly with a velocity impulse instead. Horizontal
                // direction only so we don't try to launch fish out of water.
                Vec3 dir = new Vec3(
                    mob.getX() - player.getX(),
                    0,
                    mob.getZ() - player.getZ()
                );
                if (dir.lengthSqr() < 1.0e-4) dir = new Vec3(1, 0, 0); // jitter when overlapping
                Vec3 push = dir.normalize().scale(0.4);
                Vec3 v = mob.getDeltaMovement();
                mob.setDeltaMovement(v.x + push.x, v.y, v.z + push.z);
                mob.hurtMarked = true;
            }
        }
    }
}
