package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.event.CombatPowerEvents;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EntityExclusions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Listed mob types do not aggro or target this player. They ignore them as if they were invisible.
 * Different from scare_entities — they don't flee, they simply never target this player.
 *
 * <p>{@code entity_types} entries accept both raw IDs ({@code "minecraft:zombie"})
 * and tag references ({@code "#mymod:my_tag"}). An empty/omitted list matches
 * every mob.
 *
 * <p>By default a retaliation window applies — if the player has recently
 * hit the mob, the mob is allowed to target them back so combat feedback
 * loops still work. Set {@code "passive": true} to disable that and make
 * the ignore unconditional (the mob never targets this player, even if
 * the player attacks first).
 *
 * <p>Exclusions: boss-tier mobs (Warden, Ender Dragon, Wither), entities on
 * the {@code tame_scare_entity_blacklist} global config list, and entities in
 * this power's optional {@code entity_blacklist} are never affected — they
 * target the player normally even when {@code entity_types} matches them
 * (including the empty match-all case). See
 * {@link com.cyberday1.neoorigins.service.EntityExclusions}.
 *
 * <p>{@code include_boss_tier: true} lifts only the boss-tier exclusion, for
 * whatever {@code entity_types} matches. The Warden's brain never fires
 * {@code LivingChangeTargetEvent}, so {@code WardenCanTargetMixin} applies
 * {@link #hidesFrom} to it directly.
 */
public class MobsIgnorePlayerPower extends PowerType<MobsIgnorePlayerPower.Config> {

    public record Config(List<String> entityTypes, List<String> entityBlacklist, boolean passive,
                         boolean includeBossTier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            // Per-power blocklist of entity ids ("minecraft:warden") and tag
            // refs ("#mymod:relentless") that always keep targeting the player
            // even when they match entity_types (notably the empty match-all
            // list). Checked on top of the shared boss-tier + global-config
            // exclusions (EntityExclusions).
            Codec.STRING.listOf().optionalFieldOf("entity_blacklist", List.of())
                .forGetter(Config::entityBlacklist),
            Codec.BOOL.optionalFieldOf("passive", false).forGetter(Config::passive),
            Codec.BOOL.optionalFieldOf("include_boss_tier", false).forGetter(Config::includeBossTier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}

    /**
     * True if {@code mob} should stop fleeing {@code player} because the player
     * has an active {@code mobs_ignore_player} that matches the mob's type. Used
     * by {@code AvoidEntityGoalMixin} so cats / ocelots / foxes that would
     * normally run from the player simply ignore them instead — making the
     * "they don't flee" contract in this class's javadoc actually hold.
     *
     * <p>Mirrors the type-match + exclusion logic of the targeting path in
     * {@link com.cyberday1.neoorigins.event.WorldPowerEvents}, but ignores the
     * {@code passive}/retaliation window: fleeing isn't retaliation, so a player
     * who has hit the mob still shouldn't make it bolt.
     */
    public static boolean suppressesAvoidance(ServerPlayer player, Mob mob) {
        return ActiveOriginService.has(player, MobsIgnorePlayerPower.class, cfg -> matches(cfg, mob));
    }

    /** {@code entity_types} matches the mob and no exclusion applies (boss-tier only without include_boss_tier). */
    public static boolean matches(Config cfg, LivingEntity mob) {
        boolean typeMatch = cfg.entityTypes().isEmpty()
            || cfg.entityTypes().stream().anyMatch(id -> CombatPowerEvents.matchesEntityIdOrTag(mob, id));
        return typeMatch && !EntityExclusions.isExcluded(mob, cfg.entityBlacklist(), cfg.includeBossTier());
    }

    /**
     * The target-event rule for AI that bypasses the event: a matching power hides
     * the player unless they hit the mob recently, which {@code passive} overrides.
     */
    public static boolean hidesFrom(ServerPlayer player, LivingEntity mob) {
        if (ActiveOriginService.has(player, MobsIgnorePlayerPower.class, cfg -> cfg.passive() && matches(cfg, mob))) {
            return true;
        }
        return mob.getLastHurtByMob() != player
            && ActiveOriginService.has(player, MobsIgnorePlayerPower.class, cfg -> matches(cfg, mob));
    }
}
