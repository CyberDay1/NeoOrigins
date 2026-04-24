package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
            if (!(e instanceof PathfinderMob mob)) continue;
            if (!(e instanceof LivingEntity le)) continue;
            boolean matches = false;
            for (String id : config.entityTypes()) {
                if (com.cyberday1.neoorigins.event.CombatPowerEvents.matchesEntityIdOrTag(le, id)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;
            // Drop aggro: NearestAttackableTargetGoal will re-path toward the
            // player on the next tick if we only set a flee destination.
            // Clearing the current target + last-hurt-by lets our flee
            // navigation stick. Hostile AI may re-acquire the target on line
            // of sight, but that's OK — we re-clear on the next tick.
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
            Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, player.position());
            if (away != null) {
                mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
            }
        }
    }
}
