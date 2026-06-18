package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.event.CombatPowerEvents;
import com.cyberday1.neoorigins.service.EntityExclusions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Attracts nearby mobs toward the holder, as though the player were holding the
 * mob's favourite food. Each pulled mob simply paths to the player; it does not
 * become tamed or hostile.
 *
 * <p>With no {@code entity_types} filter only {@link Animal}s are drawn (the
 * vanilla "follows food" set); supplying ids/tags widens or replaces that to
 * any matching {@link PathfinderMob}. {@code entity_blacklist} removes specific
 * ids/tags from whatever set the filter selected.
 *
 * <pre>
 * { "type": "neoorigins:attract_mobs", "radius": 12 }
 * { "type": "neoorigins:attract_mobs", "entity_types": ["minecraft:zombie", "#minecraft:skeletons"] }
 * </pre>
 */
public class AttractMobsPower extends PowerType<AttractMobsPower.Config> {

    /** Re-path interval — pathing every tick is wasteful and jitters the mob. */
    private static final int REPATH_INTERVAL = 10;

    public record Config(double radius, double speed,
                         List<String> entityTypes, List<String> entityBlacklist,
                         String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(Config::radius),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(Config::speed),
            Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            Codec.STRING.listOf().optionalFieldOf("entity_blacklist", List.of()).forGetter(Config::entityBlacklist),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % REPATH_INTERVAL != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        AABB box = player.getBoundingBox().inflate(config.radius());
        List<PathfinderMob> mobs = level.getEntitiesOfClass(PathfinderMob.class, box,
            mob -> mob.isAlive() && matches(mob, config));

        for (PathfinderMob mob : mobs) {
            mob.getNavigation().moveTo(player, config.speed());
            mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
    }

    private static boolean matches(PathfinderMob mob, Config config) {
        boolean selected;
        if (config.entityTypes().isEmpty()) {
            selected = mob instanceof Animal;
        } else {
            selected = config.entityTypes().stream()
                .anyMatch(id -> CombatPowerEvents.matchesEntityIdOrTag(mob, id));
        }
        if (!selected) return false;
        return !EntityExclusions.isExcluded(mob, config.entityBlacklist());
    }
}
