package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.data.EntityGroupDataManager;
import com.cyberday1.neoorigins.data.EntityGroupDataManager.GroupDef;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Classifies the player as a mob entity group, affecting how potion effects and
 * enchantments interact — and (data-driven) which mobs ignore them.
 *
 * <p>The {@code group} value is a group id resolved by {@link EntityGroupDataManager}.
 * A bare value (no namespace) resolves to {@code neoorigins:<name>} for backward
 * compat. Four groups are built in (reproducing the historical behaviour) and a
 * datapack file at {@code data/<ns>/neoorigins/entity_groups/<name>.json} of the
 * same id overrides a built-in — or mints an entirely new group:
 * <ul>
 *   <li>{@code neoorigins:undead}  — immune to poison/regeneration, instant heal/harm
 *       inverted, extra damage from Smite.</li>
 *   <li>{@code neoorigins:arthropod} — extra damage from Bane of Arthropods (with the
 *       vanilla slowness-on-hit).</li>
 *   <li>{@code neoorigins:water}   — extra damage from Impaling.</li>
 *   <li>{@code neoorigins:illager} — raiders won't target you; village iron
 *       golems hunt you; villagers and wandering traders flee.</li>
 *   <li>{@code neoorigins:piglin}  — piglins and brutes never aggro you.</li>
 *   <li>{@code neoorigins:skeleton} — undead kit plus wolves hunt you and you burn in daylight like a vanilla skeleton.</li>
 * </ul>
 *
 * <p>Consumed via {@link Config#groupDef()} query methods (immuneTo / invertsInstant
 * / vulnerableTo / ignoredBy) in {@code CombatPowerEvents}, {@code WorldPowerEvents},
 * {@code LivingEntityUndeadPotionMixin} and {@code ArsNouveauCompat}. Membership is
 * simulated by intercepting game hooks because a player can't join real EntityType tags.
 */
public class EntityGroupPower extends PowerType<EntityGroupPower.Config> {

    public record Config(String group, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("group", "undefined").forGetter(Config::group),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        /**
         * Resolve this config's {@code group} id to its {@link GroupDef} at query
         * time (so datapack reloads take effect without regranting the power).
         * Never null — an unknown id resolves to {@link GroupDef#EMPTY} + a one-time
         * WARN. Callers query the returned def (immuneTo / invertsInstant / …).
         */
        public GroupDef groupDef() {
            return EntityGroupDataManager.INSTANCE.resolve(group);
        }

        /** True if this config's group id equals {@code groupId} (namespace-normalised). */
        public boolean is(String groupId) {
            String self = group == null ? "" : group;
            String selfNs = self.indexOf(':') >= 0 ? self : "neoorigins:" + self;
            String otherNs = groupId.indexOf(':') >= 0 ? groupId : "neoorigins:" + groupId;
            return selfNs.equalsIgnoreCase(otherNs);
        }
    }

    /** How often (in ticks) the {@code feared_by} flee sweep runs. */
    private static final int FEAR_TICK_INTERVAL = 5;
    /** Radius (blocks) of the {@code feared_by} flee sweep. Matches ScareEntitiesPower. */
    private static final double FEAR_RANGE = 8.0;
    /** How often (in ticks) the {@code burns_in_sunlight} sun check runs (~1s, matching the exposed_to_sun cadence). */
    private static final int SUN_TICK_INTERVAL = 20;
    /** Fire ticks applied when a {@code burns_in_sunlight} player is exposed. */
    private static final int SUN_FIRE_TICKS = 160;

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}

    /**
     * Drives the group's active per-tick behaviour: the {@code feared_by} flee
     * sweep (nearby mobs listed in the resolved {@link GroupDef} flee the player,
     * reusing {@link ScareEntitiesPower}'s per-mob flee logic, throttled to every
     * {@link #FEAR_TICK_INTERVAL} ticks) and the {@code burns_in_sunlight} sweep
     * (the player catches fire in open daylight, throttled to every
     * {@link #SUN_TICK_INTERVAL} ticks and gated on the shared
     * {@link com.cyberday1.neoorigins.compat.condition.ConditionParser#isExposedToSun
     * exposed-to-sun} rules). The {@code ignored_by} / {@code targeted_by} / effect
     * / enchant sides are handled reactively elsewhere (WorldPowerEvents,
     * MobsTargetPlayerPower, CombatPowerEvents) — only fleeing and burning need an
     * active sweep.
     */
    @Override
    public void onTick(ServerPlayer player, Config config) {
        GroupDef def = config.groupDef();
        if (player.tickCount % FEAR_TICK_INTERVAL == 0) {
            java.util.List<String> feared = def.fearedBy();
            if (!feared.isEmpty()) {
                java.util.List<net.minecraft.world.entity.Mob> mobs =
                    com.cyberday1.neoorigins.service.AreaTargetSelector.mobsInRadius(
                        player, FEAR_RANGE, feared, java.util.List.of(), false, 0);
                for (net.minecraft.world.entity.Mob mob : mobs) {
                    ScareEntitiesPower.fleeMob(player, mob);
                }
            }
        }
        if (def.burnsInSunlight() && player.tickCount % SUN_TICK_INTERVAL == 0
                && com.cyberday1.neoorigins.compat.condition.ConditionParser.isExposedToSun(player)) {
            player.setRemainingFireTicks(SUN_FIRE_TICKS);
        }
    }
}
