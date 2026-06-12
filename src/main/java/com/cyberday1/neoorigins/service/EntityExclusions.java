package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.event.CombatPowerEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Set;

/**
 * Shared exclusion logic for the mob-control power family (taming and scare
 * abilities: {@code tame_mob}, {@code scare_entities}, {@code mobs_ignore_player}).
 *
 * <p>An entity is excluded from mob-control effects when ANY of these hold:
 * <ol>
 *   <li><b>Boss-tier</b> — Warden, Ender Dragon, Wither. Hardcoded, never
 *       overridable: bosses can't be tamed, scared, or made to ignore the
 *       player. The Ender Dragon and Wither were historically caught by
 *       {@code tame_mob}'s {@code canUsePortal} gate; the Warden slipped
 *       through (it can use portals), hence the explicit set.</li>
 *   <li><b>Global config blacklist</b> — the {@code tame_scare_entity_blacklist}
 *       list in {@code config/neoorigins/admin.toml} ({@code [entity_exclusions]}).
 *       Server/pack operators extend the exclusion to arbitrary mobs across
 *       ALL taming and scare powers at once.</li>
 *   <li><b>Per-power {@code entity_blacklist}</b> — the optional JSON field on
 *       the individual power.</li>
 * </ol>
 *
 * <p>Entries in both lists use the same syntax as {@code entity_types} filters
 * elsewhere: raw entity ids ({@code "minecraft:warden"}) and {@code #}-prefixed
 * entity-type tag refs ({@code "#mymod:untameable"}).
 */
public final class EntityExclusions {

    private EntityExclusions() {}

    /**
     * Boss-grade mobs that are never tameable / scareable regardless of config.
     */
    private static final Set<EntityType<?>> BOSS_TIER = Set.of(
        EntityType.WARDEN,
        EntityType.ENDER_DRAGON,
        EntityType.WITHER);

    /** True if the entity is in the hardcoded, non-overridable boss-tier set. */
    public static boolean isBossTier(LivingEntity entity) {
        return BOSS_TIER.contains(entity.getType());
    }

    /**
     * True if the entity matches the server-operator global blacklist
     * ({@code [entity_exclusions] tame_scare_entity_blacklist} in the COMMON
     * config). Does NOT include the boss-tier set.
     */
    public static boolean isConfigBlacklisted(LivingEntity entity) {
        for (String idOrTag : AdminConfig.tameScareEntityBlacklist()) {
            if (CombatPowerEvents.matchesEntityIdOrTag(entity, idOrTag)) return true;
        }
        return false;
    }

    /**
     * True if the entity matches any entry in a per-power {@code entity_blacklist}
     * list (entity ids and {@code #tag} refs).
     */
    public static boolean matchesAny(LivingEntity entity, List<String> idsOrTags) {
        for (String idOrTag : idsOrTags) {
            if (CombatPowerEvents.matchesEntityIdOrTag(entity, idOrTag)) return true;
        }
        return false;
    }

    /**
     * Combined exclusion check: boss-tier OR global config blacklist OR the
     * given per-power {@code entity_blacklist}. This is the one-call answer for
     * scare-type powers, which skip excluded entities silently. {@code tame_mob}
     * calls the parts separately so it can show distinct actionbar messages.
     */
    public static boolean isExcluded(LivingEntity entity, List<String> perPowerBlacklist) {
        return isBossTier(entity)
            || isConfigBlacklisted(entity)
            || matchesAny(entity, perPowerBlacklist);
    }
}
