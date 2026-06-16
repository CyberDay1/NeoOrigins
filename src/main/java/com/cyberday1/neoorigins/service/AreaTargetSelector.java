package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.event.CombatPowerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared area-of-effect mob selection for the mob-control power family
 * ({@code tame_mob} area mode, {@code scare_entities}, and future AoE powers).
 *
 * <p>One-call answer for "which living {@link Mob}s near the player match this
 * power's filters" — collapsing the radius sweep, whitelist/blacklist matching,
 * boss-tier/exclusion guard, distance sort, and target cap that each AoE power
 * was open-coding.
 */
public final class AreaTargetSelector {

    private AreaTargetSelector() {}

    /**
     * Collect living {@link Mob}s within {@code radius} of the player, filtered
     * and ordered for AoE targeting.
     *
     * <p>Selection rules:
     * <ul>
     *   <li>Only living {@link Mob}s within the inflated bounding box are kept
     *       (the player themself is always excluded).</li>
     *   <li>{@code hostileOnly} keeps only {@link Enemy} mobs when true.</li>
     *   <li>{@code whitelist} (entity ids / {@code #tag} refs): when non-empty,
     *       only mobs matching some entry are kept. An <b>empty whitelist means
     *       allow all</b>.</li>
     *   <li>{@code blacklist} mobs are skipped via
     *       {@link EntityExclusions#isExcluded} — which also covers boss-tier
     *       mobs and the operator global config list, not just the per-power
     *       list passed here.</li>
     * </ul>
     *
     * <p>The result is sorted <b>nearest-first</b> by squared distance to the
     * player. When {@code limit > 0} only the first {@code limit} mobs are
     * returned; {@code limit <= 0} returns all matches (unlimited).
     *
     * @param player    the AoE origin (excluded from results)
     * @param radius    bounding-box inflation radius in blocks
     * @param whitelist entity ids / {@code #tag} refs; empty = allow all
     * @param blacklist per-power entity ids / {@code #tag} refs to skip
     * @param hostileOnly when true, keep only {@link Enemy} mobs
     * @param limit     max mobs to return; {@code <= 0} = unlimited
     * @return matching mobs, nearest-first, capped at {@code limit}
     */
    public static List<Mob> mobsInRadius(ServerPlayer player, double radius,
            List<String> whitelist, List<String> blacklist, boolean hostileOnly, int limit) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<Mob> result = new ArrayList<>();
        for (var e : player.level().getEntities(player, box)) {
            if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
            if (hostileOnly && !(mob instanceof Enemy)) continue;
            if (!whitelist.isEmpty()) {
                boolean matched = false;
                for (String id : whitelist) {
                    if (CombatPowerEvents.matchesEntityIdOrTag(mob, id)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) continue;
            }
            // Covers boss-tier + operator global blacklist + per-power list.
            if (EntityExclusions.isExcluded(mob, blacklist)) continue;
            result.add(mob);
        }
        result.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        if (limit > 0 && result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }
}
