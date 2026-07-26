package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
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

    public record Config(List<String> entityTypes, List<String> entityBlacklist, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            // Per-power blocklist of entity ids ("minecraft:warden") and tag
            // refs ("#mymod:fearless") that are never scared even when they
            // match entity_types. Checked on top of the shared boss-tier +
            // global-config exclusions (EntityExclusions).
            Codec.STRING.listOf().optionalFieldOf("entity_blacklist", List.of())
                .forGetter(Config::entityBlacklist),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % TICK_INTERVAL != 0) return;
        if (config.entityTypes().isEmpty()) return;

        // Selection (radius sweep + entity_types whitelist + boss-tier/global/
        // per-power exclusions) is shared with the AoE power family. Behavior is
        // identical to the old inline loop: same RANGE, same whitelist
        // (entity_types), same exclusions, not hostile-only, no target cap
        // (limit 0). Mob covers PathfinderMob (zombies, etc.) AND WaterAnimal
        // (cod, salmon, squid, dolphin, ...) — the selector keeps both.
        List<Mob> mobs = com.cyberday1.neoorigins.service.AreaTargetSelector.mobsInRadius(
            player, RANGE, config.entityTypes(), config.entityBlacklist(), false, 0);
        for (Mob mob : mobs) {
            fleeMob(player, mob);
        }
    }

    /**
     * Make a single mob flee {@code player} for one tick: drops its aggro and
     * paths (or impulses) it away. Shared with the {@code entity_group}
     * {@code feared_by} sweep in {@link EntityGroupPower} so both use the exact
     * same flee behaviour, range gate and water-mob fallback.
     */
    public static void fleeMob(ServerPlayer player, Mob mob) {
        // Drop aggro unconditionally — not just when targeting this player.
        // Mobs like Phantoms use shared targeting goals that pick ANY nearby
        // player; if we only clear when target == this player, a Phantom
        // chasing a different player nearby would ignore the scare entirely.
        // Clearing the target forces re-evaluation, and our flee navigation
        // takes over before the goal can re-acquire.
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        if (!mob.getNavigation().isDone()
            && mob.getNavigation().getTargetPos() != null
            && mob.getNavigation().getTargetPos().distSqr(player.blockPosition()) > RANGE * RANGE) {
            return;
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
