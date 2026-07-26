package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.event.CombatPowerEvents;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EntityExclusions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Inverse of {@link MobsIgnorePlayerPower}: listed mob types PROACTIVELY hunt a
 * player who has this power, the way wolves aggro a vanilla skeleton. Use it for
 * a skeleton-type origin that dogs treat as prey.
 *
 * <p>{@code entity_types} entries accept both raw IDs ({@code "minecraft:wolf"})
 * and tag references ({@code "#mymod:my_tag"}). An empty/omitted list matches
 * every mob — every mob within range hunts the player.
 *
 * <p>Because nothing fires when a mob is NOT targeting the player, this power can
 * not be purely reactive like {@code mobs_ignore_player}: instead a custom
 * {@link TargetOriginPlayerGoal} is injected into each mob's {@code targetSelector}
 * on entity-join (see {@link CombatPowerEvents#onEntityJoinLevel}). The goal
 * acquires the nearest matching power-holder within {@code range}.
 *
 * <p>Exclusions: boss-tier mobs (Warden, Ender Dragon, Wither), entities on the
 * shared global config list, and entities in this power's optional
 * {@code entity_blacklist} never hunt the player, even when {@code entity_types}
 * matches them (including the empty match-all case). See
 * {@link com.cyberday1.neoorigins.service.EntityExclusions}.
 */
public class MobsTargetPlayerPower extends PowerType<MobsTargetPlayerPower.Config> {

    /** Default hunt range in blocks when {@code range} is omitted. */
    public static final double DEFAULT_RANGE = 16.0;

    public record Config(List<String> entityTypes, List<String> entityBlacklist, double range, String type)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            // Per-power blocklist of entity ids ("minecraft:warden") and tag
            // refs ("#mymod:relentless") that never hunt the player even when
            // they match entity_types (notably the empty match-all list).
            // Checked on top of the shared boss-tier + global-config exclusions.
            Codec.STRING.listOf().optionalFieldOf("entity_blacklist", List.of())
                .forGetter(Config::entityBlacklist),
            Codec.DOUBLE.optionalFieldOf("range", DEFAULT_RANGE).forGetter(Config::range),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}

    /**
     * True if {@code mob} should hunt {@code player} because the player has an
     * active {@code mobs_target_player} whose {@code entity_types} matches this
     * mob's type and the mob is not excluded. Mirrors the type-match + exclusion
     * logic of {@link MobsIgnorePlayerPower#suppressesAvoidance}. Also reports the
     * widest configured range via {@code rangeOut[0]} for matched holders, so the
     * goal can size its scan to the most permissive power.
     */
    public static boolean shouldHunt(ServerPlayer player, Mob mob, double[] rangeOut) {
        return ActiveOriginService.has(player, MobsTargetPlayerPower.class, cfg -> {
            boolean typeMatch = cfg.entityTypes().isEmpty()
                || cfg.entityTypes().stream().anyMatch(id ->
                    CombatPowerEvents.matchesEntityIdOrTag(mob, id));
            if (typeMatch && EntityExclusions.isExcluded(mob, cfg.entityBlacklist())) {
                typeMatch = false;
            }
            if (typeMatch && rangeOut != null && cfg.range() > rangeOut[0]) {
                rangeOut[0] = cfg.range();
            }
            return typeMatch;
        });
    }

    /** Convenience overload without a range out-param. */
    public static boolean shouldHunt(ServerPlayer player, Mob mob) {
        return shouldHunt(player, mob, null);
    }

    /**
     * Inject the hunt goal into a freshly-joined mob. No-op if the mob already
     * carries one (rehydrate safety). Cheap: the goal itself gates its scan and
     * only fires when a matching power-holder is actually in range.
     */
    public static void injectGoal(Mob mob) {
        boolean already = mob.targetSelector.getAvailableGoals().stream()
            .anyMatch(g -> g.getGoal() instanceof TargetOriginPlayerGoal);
        if (already) return;
        mob.targetSelector.addGoal(3, new TargetOriginPlayerGoal(mob));
    }

    /**
     * Target goal that hunts the nearest player carrying a matching
     * {@code mobs_target_player}. Built on {@link NearestAttackableTargetGoal} so
     * pathing / target-invalidation is vanilla-standard; the filter does the
     * power-holder + exclusion check. The scan is throttled by the base goal's
     * {@code randomInterval} (10 ticks) and by an early-out when no matching
     * holder is near, keeping per-tick cost negligible for the common case where
     * nobody in the area has the power.
     */
    public static class TargetOriginPlayerGoal extends NearestAttackableTargetGoal<Player> {

        private final Mob self;

        public TargetOriginPlayerGoal(Mob mob) {
            // randomInterval 10 = re-scan at most every ~10 ticks (not every tick).
            // mustSee=false / mustReach=false: a skeleton-hunting wolf shouldn't
            // need line-of-sight to start closing in.
            super(mob, Player.class, 10, false, false, null);
            this.self = mob;
            // Widen the base targeting range: NearestAttackableTargetGoal defaults
            // to the mob's FOLLOW_RANGE; DEFAULT_RANGE is our floor so short-range
            // mobs still acquire. Per-power range is enforced in the predicate.
            this.targetConditions = TargetingConditions.forCombat()
                .range(DEFAULT_RANGE)
                .selector(this::matches);
        }

        /** True when {@code candidate} is a power-holder that makes {@code self} hunt it. */
        private boolean matches(net.minecraft.world.entity.LivingEntity candidate) {
            if (!(candidate instanceof ServerPlayer sp)) return false;
            double[] range = { DEFAULT_RANGE };
            boolean hunt = shouldHunt(sp, self, range);
            if (!hunt) {
                // entity_group targeted_by: a player in a pseudo entity-group whose
                // def lists this mob type is hunted too (e.g. the built-in illager
                // group draws village iron golems). Uses the default range floor.
                hunt = ActiveOriginService.has(sp,
                    com.cyberday1.neoorigins.power.builtin.EntityGroupPower.class,
                    cfg -> cfg.groupDef().targetedBy(self));
            }
            if (!hunt) return false;
            // Enforce the per-power range: the base targeting range is the floor
            // DEFAULT_RANGE, so honor a SMALLER configured range here.
            return self.distanceToSqr(sp) <= range[0] * range[0];
        }
    }
}
