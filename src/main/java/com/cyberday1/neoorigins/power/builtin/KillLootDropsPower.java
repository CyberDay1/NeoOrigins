package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.mob_origin.EntityTargetSpec;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Datapack-driven power that layers extra item drops onto the VANILLA LOOT of a
 * mob the holder kills — as real world drops from the mob (flowing through the
 * loot pipeline), not inventory inserts. The "loot-table-native" version of a
 * kill&rarr;drop feature.
 *
 * <p>While the power is granted, its compiled {@link Rule rules} are published
 * into a static per-player registry ({@link #activeRules}). The companion
 * {@link com.cyberday1.neoorigins.event.KillLootDropsLootModifier} reads that
 * registry at loot-generation time, resolves the killer via
 * {@code LAST_DAMAGE_PLAYER}, and rolls each matching rule against the mob's
 * type. {@code onRevoked} clears the entry so revoked/origin-swapped players
 * stop dropping the bonus loot.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:kill_loot_drops",
 *   "drops": [
 *     { "entity_type": "minecraft:zombie",  "item": "minecraft:zombie_head",  "chance": 0.05, "count": 1 },
 *     { "entity_type": "minecraft:creeper", "item": "minecraft:creeper_head", "chance": 0.05, "count": 1 }
 *   ],
 *   "name": "Head Hunter",
 *   "description": "..."
 * }
 * }</pre>
 *
 * <p>Each drop reuses {@link EntityTargetSpec} for mob matching, so besides the
 * single {@code entity_type} above it also accepts {@code entity_tag} (an
 * entity-type tag) or {@code entity_types} (an explicit list). {@code chance}
 * defaults to {@code 1.0}, {@code count} to {@code 1}. Activation happens via a
 * data-free global-loot-modifier carrier (see the mod's
 * {@code global_loot_modifiers.json}).
 */
public class KillLootDropsPower extends PowerType<KillLootDropsPower.Config> {

    /**
     * One compiled kill&rarr;drop rule. {@code item} is resolved to the actual
     * {@link Item} at decode time so the loot modifier stays alloc-free; a
     * missing/unknown item id yields a null {@code item} (the modifier skips it).
     */
    public record Rule(EntityTargetSpec target, Item item, double chance, int count) {}

    public record Config(List<Rule> rules, String powerId, String type)
            implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "kill_loot_drops: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "kill_loot_drops: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:kill_loot_drops";
                String pid = obj.has("_power_id")
                    ? obj.get("_power_id").getAsString() : "neoorigins:kill_loot_drops";

                List<Rule> rules = new ArrayList<>();
                if (obj.has("drops") && obj.get("drops").isJsonArray()) {
                    JsonArray arr = obj.getAsJsonArray("drops");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject d = el.getAsJsonObject();
                        Rule rule = parseRule(d, pid);
                        if (rule != null) rules.add(rule);
                    }
                }

                Config cfg = new Config(List.copyOf(rules), pid, t);
                return DataResult.success(Pair.of(cfg, ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        /** Parse one drop object into a {@link Rule}, or null if it's unusable. */
        private static Rule parseRule(JsonObject d, String pid) {
            // Reuse EntityTargetSpec's codec so entity_type / entity_tag /
            // entity_types all parse identically to the mob-origin path.
            var specResult = EntityTargetSpec.CODEC.parse(JsonOps.INSTANCE, d);
            if (specResult.result().isEmpty()) {
                NeoOrigins.LOGGER.warn(
                    "kill_loot_drops ({}): drop entry missing/invalid entity target, skipping: {}",
                    pid, specResult.error().map(Object::toString).orElse("?"));
                return null;
            }
            EntityTargetSpec spec = specResult.result().get();
            if (!spec.isValid()) {
                NeoOrigins.LOGGER.warn(
                    "kill_loot_drops ({}): drop entry must set exactly one of entity_type / entity_tag / entity_types, skipping",
                    pid);
                return null;
            }

            if (!d.has("item") || !d.get("item").isJsonPrimitive()) {
                NeoOrigins.LOGGER.warn("kill_loot_drops ({}): drop entry missing `item`, skipping", pid);
                return null;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(d.get("item").getAsString());
            Item item = (itemId == null) ? null
                : BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) {
                NeoOrigins.LOGGER.warn("kill_loot_drops ({}): unknown item '{}', skipping",
                    pid, d.get("item").getAsString());
                return null;
            }

            double chance = (d.has("chance") && d.get("chance").isJsonPrimitive())
                ? d.get("chance").getAsDouble() : 1.0;
            int count = (d.has("count") && d.get("count").isJsonPrimitive())
                ? Math.max(1, d.get("count").getAsInt()) : 1;

            return new Rule(spec, item, chance, count);
        }
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Killer-UUID &rarr; compiled rules, published while the power is granted and
     * read by {@link com.cyberday1.neoorigins.event.KillLootDropsLootModifier}.
     * A player may hold multiple {@code kill_loot_drops} powers (e.g. one per
     * mob); their rule lists are concatenated per config so revoking one power
     * doesn't drop the others' rules.
     */
    private static final java.util.Map<UUID, java.util.Map<String, List<Rule>>> perConfig =
        new ConcurrentHashMap<>();

    /** Flattened rules for a killer, or an empty list. Alloc-light: returns the
     *  single config's list directly when only one power is active. */
    public static List<Rule> activeRules(UUID uuid) {
        java.util.Map<String, List<Rule>> byConfig = perConfig.get(uuid);
        if (byConfig == null || byConfig.isEmpty()) return List.of();
        if (byConfig.size() == 1) return byConfig.values().iterator().next();
        List<Rule> all = new ArrayList<>();
        for (List<Rule> l : byConfig.values()) all.addAll(l);
        return all;
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        if (config.rules().isEmpty()) return;
        perConfig.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
            .put(config.powerId(), config.rules());
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        java.util.Map<String, List<Rule>> byConfig = perConfig.get(player.getUUID());
        if (byConfig != null) {
            byConfig.remove(config.powerId());
            if (byConfig.isEmpty()) perConfig.remove(player.getUUID());
        }
    }

    /**
     * Remove all published rules for a player — safety sweep mirroring
     * {@link ActionOnEventPower#clearTokens(UUID)}, called after individual
     * {@code onRevoked}s on a full power wipe (origin change / orb reroll).
     */
    public static void clearTokens(UUID uuid) {
        perConfig.remove(uuid);
    }
}
