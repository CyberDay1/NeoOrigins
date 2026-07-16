package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.FluidPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ConditionParser {

    private ConditionParser() {}

    /**
     * Canonical {@code neoorigins:} ids this parser's {@code switch} accepts —
     * the single source the 2.1 creator's condition picker reads. Kept honest
     * by {@code SchemaFormCheck}, which re-derives the case labels from this
     * file's source and fails the build if this set drifts from the switch.
     */
    public static final java.util.Set<String> KNOWN_TYPES = java.util.Set.of(
        "neoorigins:actor_condition", "neoorigins:advancement", "neoorigins:air",
        "neoorigins:amount", "neoorigins:and", "neoorigins:armor_value",
        "neoorigins:biome", "neoorigins:block", "neoorigins:block_collision",
        "neoorigins:brightness", "neoorigins:can_see", "neoorigins:climbing",
        "neoorigins:command", "neoorigins:config_flag", "neoorigins:constant",
        "neoorigins:cooldown", "neoorigins:cover", "neoorigins:creative_flying",
        "neoorigins:creative_mode", "neoorigins:damage_name",
        "neoorigins:damage_tag", "neoorigins:damage_type", "neoorigins:daytime",
        "neoorigins:dimension", "neoorigins:distance",
        "neoorigins:distance_from_coordinates", "neoorigins:enchantment",
        "neoorigins:entity_type", "neoorigins:equal", "neoorigins:equipped_item",
        "neoorigins:exists", "neoorigins:exposed_to_sky", "neoorigins:exposed_to_sun",
        "neoorigins:fall_distance", "neoorigins:fall_flying", "neoorigins:fluid_height",
        "neoorigins:food_item_id", "neoorigins:food_item_in_config_list",
        "neoorigins:food_item_in_tag", "neoorigins:food_level",
        "neoorigins:from_explosion", "neoorigins:from_fire", "neoorigins:from_projectile",
        "neoorigins:saturation_level",
        "neoorigins:hardness",
        "neoorigins:has_effect", "neoorigins:health", "neoorigins:height",
        "neoorigins:hit_dealt_amount", "neoorigins:hit_taken_amount",
        "neoorigins:in_block", "neoorigins:in_rain",
        "neoorigins:in_set", "neoorigins:in_tag", "neoorigins:in_water",
        "neoorigins:inventory",
        "neoorigins:invisible", "neoorigins:lava", "neoorigins:light_level",
        "neoorigins:living", "neoorigins:moon_phase", "neoorigins:moving",
        "neoorigins:nbt", "neoorigins:near_block", "neoorigins:near_entity",
        "neoorigins:night", "neoorigins:no_minions_alive", "neoorigins:not",
        "neoorigins:on_block", "neoorigins:on_fire", "neoorigins:on_ground",
        "neoorigins:or", "neoorigins:origin", "neoorigins:out_of_combat", "neoorigins:passenger",
        "neoorigins:power", "neoorigins:power_active", "neoorigins:power_type",
        "neoorigins:predicate", "neoorigins:relative_health", "neoorigins:replacable",
        "neoorigins:resource", "neoorigins:scoreboard", "neoorigins:sneaking",
        "neoorigins:sprinting", "neoorigins:status_effect", "neoorigins:submerged_in",
        "neoorigins:submerged_in_water", "neoorigins:swimming", "neoorigins:target_group",
        "neoorigins:target_type", "neoorigins:temperature", "neoorigins:thundering",
        "neoorigins:ticking", "neoorigins:time_of_day", "neoorigins:using_item",
        "neoorigins:water", "neoorigins:weather", "neoorigins:xp_level",
        "neoorigins:xp_levels", "neoorigins:xp_points");

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    public static EntityCondition parse(JsonObject json, String contextId) {
        if (json == null) {
            return failClosed("root", contextId, "missing condition object");
        }
        // Apoli/Origins convention: every condition supports a top-level
        // `inverted: true` flag that flips its result. Wrap the parsed
        // condition in NOT when the flag is set. Without this, MoR-shape
        // packs (and many Mido-shape, Origins++-shape, etc.) silently lose
        // their inverted gates — e.g. `{type: "in_water", inverted: true}`
        // was always evaluating as plain `in_water`, breaking conditions
        // like "drain resource WHEN NOT in creative mode".
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        EntityCondition inner = parseInner(json, contextId);
        if (!inverted) return inner;
        return p -> !inner.test(p);
    }

    /**
     * Parse a condition field that may be absent, a single object, or an array
     * of condition objects. Absent or non-object/array → {@link
     * EntityCondition#alwaysTrue()}; an array is combined as logical AND (Apoli
     * all-of: every element must pass).
     *
     * <p>Native-power CODEC counterpart to the Route-B loader's identical
     * helper, so {@code neoorigins:*} powers accept the same array-or-object
     * condition shape compat-translated powers already do.
     */
    public static EntityCondition parseField(JsonObject parent, String field, String contextId) {
        if (parent == null || !parent.has(field)) return EntityCondition.alwaysTrue();
        JsonElement el = parent.get(field);
        if (el.isJsonObject()) {
            return parse(el.getAsJsonObject(), contextId);
        }
        if (el.isJsonArray()) {
            List<EntityCondition> list = new ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) list.add(parse(item.getAsJsonObject(), contextId));
            }
            return player -> {
                for (EntityCondition c : list) if (!c.test(player)) return false;
                return true;
            };
        }
        return EntityCondition.alwaysTrue();
    }

    private static EntityCondition parseInner(JsonObject json, String contextId) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Canonicalize: bare names default to neoorigins:; legacy origins:/apace:/apoli:
        // prefixes (the Origins/Apoli ecosystem aliases — these verbs share schemas)
        // get a one-shot [2.0-legacy] warning then are rewritten to neoorigins: for
        // dispatch. The canonical switch arms below only need to list neoorigins:*
        // forms. Without apoli: here, apoli:and / apoli:resource / apoli:sneaking
        // nested in deanos powers fell through to fail-closed despite a matching handler.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (!type.isEmpty() && !type.startsWith("neoorigins:")) {
            // Generic namespace fallback for any non-canonical prefix
            // (origins:, apace:, apoli:, apugli:, medievalorigins:,
            // origins-classes:, ...) — rewrite to neoorigins:<leaf> and
            // dispatch. Earlier versions whitelisted origins/apace/apoli only,
            // silently fail-closing conditions from other Apoli-derivative
            // namespaces (e.g. MoR's medievalorigins:creative_mode).
            String canonical = "neoorigins:" + type.substring(type.indexOf(':') + 1);
            com.cyberday1.neoorigins.compat.LegacyVerbWarning.warn(type, canonical);
            type = canonical;
        }
        try {
            // Registry-refactor migration (D1): every condition verb now lives in
            // BuiltinConditions and dispatches through the registered descriptor;
            // the switch has fully retired, mirroring the action side. Addon-
            // contributed verbs resolve through the same BuiltinConditions.get
            // path. An unresolved type is an unknown verb — fail closed.
            com.cyberday1.neoorigins.compat.registry.ConditionType descriptor =
                BuiltinConditions.get(type);
            if (descriptor != null) {
                return descriptor.factory().create(json, contextId);
            }
            return failClosed(type, contextId, "unsupported condition type");
        } catch (Exception e) {
            return failClosed(type, contextId, "parse error: " + e.getMessage());
        }
    }

    /**
     * cover / covered_by_block: true when the column above the player has a
     * non-air block within {@code distance} (default 8) blocks — the
     * inverse-of-canSeeSky check, scoped to directly overhead. Default 8
     * matches Apoli's covered_by_block; distance is clamped to at least 1 to
     * avoid a silent always-false.
     */
    static EntityCondition parseCovered(JsonObject json) {
        int distance = Math.max(1, json.has("distance") ? json.get("distance").getAsInt() : 8);
        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            BlockPos pos = p.blockPosition();
            int top = Math.min(sl.getMaxY(), pos.getY() + distance);
            for (int y = pos.getY() + 2; y <= top; y++) {
                if (!sl.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isAir()) return true;
            }
            return false;
        };
    }

    static EntityCondition parseAnd(JsonObject json, String ctx) {
        JsonArray arr = json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray();
        List<EntityCondition> list = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) list.add(parse(el.getAsJsonObject(), ctx));
        }
        return player -> {
            for (EntityCondition c : list) if (!c.test(player)) return false;
            return true;
        };
    }

    static EntityCondition parseOr(JsonObject json, String ctx) {
        JsonArray arr = json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray();
        List<EntityCondition> list = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) list.add(parse(el.getAsJsonObject(), ctx));
        }
        return player -> {
            for (EntityCondition c : list) if (c.test(player)) return true;
            return false;
        };
    }

    static EntityCondition parseNot(JsonObject json, String ctx) {
        if (!json.has("condition") || !json.get("condition").isJsonObject()) {
            return failClosed("origins:not", ctx, "missing required field 'condition'");
        }
        EntityCondition inner = parse(json.getAsJsonObject("condition"), ctx);
        return player -> !inner.test(player);
    }

    /**
     * Map of supported {@code neoorigins:config_flag} keys to their live config
     * suppliers. Pack authors reference these by string key in JSON; unknown
     * keys log a warning and default to {@code true} (assume the flag is on)
     * so a typo doesn't silently disable a power.
     */
    private static final java.util.Map<String, java.util.function.BooleanSupplier> CONFIG_FLAG_LOOKUPS;
    static {
        java.util.Map<String, java.util.function.BooleanSupplier> m = new java.util.HashMap<>();
        m.put("ocean_origins.fish_diet_required",
            com.cyberday1.neoorigins.config.GameplayConfig::isOceanOriginsFishDietRequired);
        m.put("ocean_origins.dries_out",
            com.cyberday1.neoorigins.config.GameplayConfig::isOceanOriginsDriesOutEnabled);
        // Add more keys here as new tunables are exposed to JSON.
        CONFIG_FLAG_LOOKUPS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Map of supported {@code neoorigins:food_item_in_config_list} keys to the
     * live config list supplier they read. Entries in each list may be a bare
     * item id or a {@code #}-prefixed tag ref. Unknown keys evaluate to false.
     */
    private static final java.util.Map<String, java.util.function.Supplier<List<String>>> CONFIG_LIST_LOOKUPS;
    static {
        java.util.Map<String, java.util.function.Supplier<List<String>>> m = new java.util.HashMap<>();
        m.put("ocean_origins.extra_fish_foods",
            com.cyberday1.neoorigins.config.GameplayConfig::oceanOriginsExtraFishFoods);
        // Add more keys here as new item-list tunables are exposed to JSON.
        CONFIG_LIST_LOOKUPS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Parses a {@code neoorigins:config_flag} condition. The {@code key} field
     * names a supported config flag; the condition returns the live boolean
     * value of that flag at evaluation time. Unknown keys default to true and
     * log a warning, so a typo doesn't silently turn a power off.
     */
    static EntityCondition parseConfigFlag(JsonObject json) {
        String key = json.has("key") ? json.get("key").getAsString() : "";
        var supplier = CONFIG_FLAG_LOOKUPS.get(key);
        if (supplier == null) {
            NeoOrigins.LOGGER.warn(
                "[CompatB] config_flag: unknown key '{}'. Supported keys: {}. Defaulting to true.",
                key, CONFIG_FLAG_LOOKUPS.keySet());
            return EntityCondition.alwaysTrue();
        }
        return p -> supplier.getAsBoolean();
    }

    /**
     * exposed_to_sun: true when the player is in open daylight (morning to sunset,
     * clear sky, not raining) and unprotected by an umbrella or helmet. Helmet
     * protection may chip durability per tick. Lift-and-shift of the former inline
     * switch arm — behaviour is byte-identical.
     */
    static EntityCondition parseExposedToSun(JsonObject json) {
        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            if (p.isPassenger()) return false;
            // Vanilla daytime is 0–12000 (sunrise to sunset). The prior
            // impl gated on 6000–12000, which skipped morning hours and
            // silently made sun-damage origins (Abyssal Surface Burn,
            // Enderian, Cinderborn daylight variants) fail to damage
            // until around noon.
            long time = sl.getDefaultClockTime() % 24000L;
            if (time >= 12000L
                || !sl.canSeeSky(p.blockPosition())
                || sl.isRaining()) return false;
            // Umbrella protection — if Vampires Need Umbrellas is loaded,
            // holding an umbrella in either hand blocks sun damage entirely
            // (takes priority over helmet protection).
            if (neoorigins$isHoldingUmbrella(p)) return false;
            // Helmet protection — any helmet blocks sun damage.
            // Damageable helmets take durability damage over time;
            // invulnerable/unbreakable helmets (e.g. allthemodium)
            // protect indefinitely.
            ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty()) {
                if (head.isDamageableItem()) {
                    float chance = GameplayConfig.sunHelmetDuraDamageChance();
                    if (chance > 0f && p.getRandom().nextFloat() < chance) {
                        head.hurtAndBreak(1, p, EquipmentSlot.HEAD);
                    }
                }
                return false;
            }
            return true;
        };
    }

    static EntityCondition parseCooldown(JsonObject json) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null) return EntityCondition.alwaysTrue();
        return player -> {
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            return !data.isOnCooldown(powerId, player.tickCount);
        };
    }

    static EntityCondition parseHealth(JsonObject json) {
        String comp    = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target  = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getHealth(), target);
    }

    /**
     * {@code hardness} — compares the destroy-hardness of the block currently in
     * context against {@code compare_to}. Apoli uses this almost exclusively inside
     * a raycast {@code block_action}'s gate (e.g. Mage spell_break: "only break
     * blocks with hardness ≤ 2"). The block is resolved from the raycast-published
     * {@link com.cyberday1.neoorigins.compat.action.ActionParser.RaycastBlockContext};
     * outside a raycast it falls back to the block the player is looking at, so the
     * condition is still meaningful in a plain hit context. No block in range → false.
     */
    static EntityCondition parseHardness(JsonObject json) {
        String comp   = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            net.minecraft.core.BlockPos pos = null;
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (ctx instanceof com.cyberday1.neoorigins.compat.action.ActionParser.RaycastBlockContext rbc) {
                pos = rbc.pos();
            }
            if (pos == null) {
                var hit = player.pick(20.0, 1.0F, false);
                if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr
                        && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    pos = bhr.getBlockPos();
                }
            }
            if (pos == null) return false;
            float hardness = player.level().getBlockState(pos).getDestroySpeed(player.level(), pos);
            return comparison.test(hardness, target);
        };
    }

    static EntityCondition parseResource(JsonObject json, String contextId) {
        String powerId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (powerId == null || powerId.isBlank()) {
            return failClosed("origins:resource", contextId, "missing required field 'resource'");
        }
        String comp   = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target    = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        boolean wildcard = CompatAttachments.ResourceState.isWildcard(powerId);
        return player -> {
            var state = player.getData(CompatAttachments.resourceState());
            // Wildcard semantics: condition holds if ANY matching key satisfies
            // the comparison. Used by Apoli-derivative packs that author
            // resource selectors like `*:*_flight_resource` (MoR Pixie etc.).
            if (wildcard) {
                var keys = state.matchingKeys(powerId);
                if (keys.isEmpty()) {
                    // No resource has been written yet — compare against the
                    // default (0) so an unmet-resource bar is treated the
                    // same as an empty one. Without this branch, conditions
                    // like `<= 0` would silently never match because
                    // matchingKeys returned nothing.
                    return comparison.test(0, target);
                }
                for (String k : keys) {
                    if (comparison.test(state.get(k, 0), target)) return true;
                }
                return false;
            }
            // Default to the declared variable start (0 for resources / undeclared
            // keys) so a counter declared elsewhere in the power stack reads its
            // start value even before its seed runs.
            int cur = state.get(powerId, CompatAttachments.variableStart(powerId));
            return comparison.test(cur, target);
        };
    }

    static EntityCondition parsePowerActive(JsonObject json, String contextId) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null || powerId.isBlank()) {
            return failClosed("origins:power_active", contextId, "missing required field 'power'");
        }
        // Wildcard power IDs (e.g. "*:*_toggle") cannot be parsed as ResourceLocations.
        // Instead, do a suffix match against the player's toggle state keys.
        if (powerId.contains("*")) {
            // Extract the suffix after the last '*' for substring matching
            final String suffix = powerId.substring(powerId.lastIndexOf('*') + 1);
            return player -> {
                var toggleState = player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.toggleState());
                for (var entry : toggleState.getStates().entrySet()) {
                    if (entry.getKey().endsWith(suffix) && entry.getValue()) {
                        return true;
                    }
                }
                return false;
            };
        }
        // Toggles facade resolves the registered TogglePower's `default` when the
        // toggleState map has no entry yet — so `"default": true` JSONs read as
        // on-until-flipped-off without needing an action_on_event GAINED hook.
        return player -> com.cyberday1.neoorigins.compat.Toggles.isOn(player, powerId);
    }

    static EntityCondition parseOnBlock(JsonObject json, String contextId) {
        if (!json.has("block_condition") || !json.get("block_condition").isJsonObject()) {
            // Some packs omit block_condition entirely — treat as "standing on any block"
            return p -> p != null && p.onGround();
        }
        JsonObject blockCond = json.getAsJsonObject("block_condition");
        String bcType = blockCond.has("type") ? blockCond.get("type").getAsString() : "";
        // Strip namespace for matching
        String bareType = bcType.contains(":") ? bcType.substring(bcType.indexOf(':') + 1) : bcType;

        // Simple block match: { "type": "origins:block", "block": "minecraft:water" }
        // or legacy: { "id": "minecraft:stone" }
        String blockId = blockCond.has("block") ? blockCond.get("block").getAsString()
                       : blockCond.has("id") ? blockCond.get("id").getAsString() : null;
        if (bareType.equals("block") || (blockId != null && !blockId.isBlank())) {
            if (blockId == null || blockId.isBlank()) {
                return failClosed("origins:on_block", contextId, "block_condition.block is empty");
            }
            Identifier bid = Identifier.parse(blockId);
            return player -> {
                if (!player.onGround()) return false;
                BlockPos below = player.blockPosition().below();
                return BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(below).getBlock()).equals(bid);
            };
        }
        // Tag match: { "type": "origins:in_tag", "tag": "minecraft:ice" }
        if (bareType.equals("in_tag") && blockCond.has("tag")) {
            TagKey<Block> tag = parseBlockTag(blockCond.get("tag").getAsString());
            return player -> {
                if (!player.onGround()) return false;
                return player.level().getBlockState(player.blockPosition().below()).is(tag);
            };
        }
        // Boolean combinators: { "type": "origins:and/or", "conditions": [...] }
        // all_of/any_of are the Apoli 2.9+ renames of and/or — same shapes.
        if (bareType.equals("and") || bareType.equals("or")
                || bareType.equals("all_of") || bareType.equals("any_of")) {
            // Block conditions don't map cleanly to entity conditions, but we can
            // evaluate them against the block below the player.
            boolean isAnd = bareType.equals("and") || bareType.equals("all_of");
            JsonArray conditions = blockCond.has("conditions") ? blockCond.getAsJsonArray("conditions") : new JsonArray();
            List<EntityCondition> subconds = new ArrayList<>();
            for (JsonElement el : conditions) {
                if (!el.isJsonObject()) continue;
                JsonObject wrapper = new JsonObject();
                wrapper.add("block_condition", el.getAsJsonObject());
                subconds.add(parseOnBlock(wrapper, contextId));
            }
            return player -> {
                for (EntityCondition c : subconds) {
                    boolean result = c.test(player);
                    if (isAnd && !result) return false;
                    if (!isAnd && result) return true;
                }
                return isAnd;
            };
        }
        // Fallback: unknown block_condition type — pass through as always-on-ground
        NeoOrigins.LOGGER.debug("[CompatB] on_block: unknown block_condition type '{}' in {} — falling back to onGround()", bcType, contextId);
        return p -> p != null && p.onGround();
    }

    // ---- Phase 1: New condition parsers ----

    static EntityCondition parseDimension(JsonObject json) {
        String dimension = json.has("dimension") ? json.get("dimension").getAsString() : null;
        if (dimension == null) return EntityCondition.alwaysTrue();
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
        return player -> player.level().dimension().equals(dimKey);
    }

    static EntityCondition parseBiome(JsonObject json) {
        // Can check either "biome" (exact id) or "condition" (sub-condition on biome)
        String biomeId = json.has("biome") ? json.get("biome").getAsString() : null;
        if (biomeId != null) {
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId));
            return player -> {
                var biomeHolder = player.level().getBiome(player.blockPosition());
                return biomeHolder.is(biomeKey);
            };
        }
        // Tag-based check. Accept both "tag" (the original field) and "biome_tag"
        // (the more descriptive field used by several built-in JSONs — frostborn,
        // piglin, strider). Without the alias, "biome_tag"-using powers fell
        // through to alwaysTrue() and fired regardless of biome (issue #36:
        // Frostborn burned everywhere; piglin/strider buffs were always-on).
        String tag = json.has("tag") ? json.get("tag").getAsString()
                   : json.has("biome_tag") ? json.get("biome_tag").getAsString()
                   : null;
        if (tag != null) {
            TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, Identifier.parse(tag));
            return player -> player.level().getBiome(player.blockPosition()).is(biomeTag);
        }
        // Handle nested sub-conditions (origins:temperature, origins:precipitation, etc.)
        // These cannot be precisely mapped but must NOT return alwaysTrue() (fail open)
        // as that would cause the power to fire in every biome.
        if (json.has("condition") && json.get("condition").isJsonObject()) {
            JsonObject subCond = json.getAsJsonObject("condition");
            String subType = subCond.has("type") ? subCond.get("type").getAsString() : "";
            String bareSubType = subType.contains(":") ? subType.substring(subType.indexOf(':') + 1) : subType;
            if ("temperature".equals(bareSubType)) {
                // Use the biome's base temperature for comparison
                String comp = subCond.has("comparison") ? subCond.get("comparison").getAsString() : ">=";
                double target = subCond.has("compare_to") ? subCond.get("compare_to").getAsDouble() : 0.0;
                ComparisonType comparison = ComparisonType.fromString(comp);
                return player -> {
                    float temp = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
                    return comparison.test(temp, target);
                };
            }
            // Unsupported biome sub-conditions — fail closed and warn
            NeoOrigins.LOGGER.warn("[CompatB] biome condition has unsupported sub-condition type '{}' — " +
                "failing closed (power will not activate). Pack authors should use biome tags instead.", subType);
            return EntityCondition.alwaysFalse();
        }
        return EntityCondition.alwaysTrue();
    }

    static EntityCondition parseInTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null) return EntityCondition.alwaysTrue();
        // in_tag is typically a biome tag check
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, Identifier.parse(tag));
        return player -> player.level().getBiome(player.blockPosition()).is(biomeTag);
    }

    static EntityCondition parseFoodLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getFoodData().getFoodLevel(), target);
    }

    /**
     * Apoli's {@code origins:saturation_level} — tests against the float
     * saturation value (vanilla range 0..20, soft-capped to the hunger level).
     */
    static EntityCondition parseSaturationLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getFoodData().getSaturationLevel(), target);
    }

    static EntityCondition parseSubmergedIn(JsonObject json) {
        String fluid = json.has("fluid") ? json.get("fluid").getAsString() : "";
        return switch (fluid) {
            case "minecraft:water" -> p -> p.isUnderWater();
            // "Submerged in lava" means eyes-in-lava, not just feet touching it.
            // isInLava() returns true on any overlap; isEyeInFluid(LAVA) is the
            // actual "submerged" predicate and matches the water branch above.
            case "minecraft:lava"  -> p -> p.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA);
            default -> p -> p.isUnderWater() || p.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA);
        };
    }

    static EntityCondition parseEquippedItem(JsonObject json, String contextId) {
        String slot = json.has("equipment_slot") ? json.get("equipment_slot").getAsString() : "mainhand";
        EquipmentSlot eqSlot = mapEquipmentSlot(slot);

        // Check for item_condition sub-object
        JsonObject itemCond = json.has("item_condition") ? json.getAsJsonObject("item_condition") : null;
        if (itemCond == null) return EntityCondition.alwaysTrue();

        // Simplified item condition: check item id or tag
        String itemId = itemCond.has("id") ? itemCond.get("id").getAsString() : null;
        String itemTag = itemCond.has("tag") ? itemCond.get("tag").getAsString() : null;
        String itemType = itemCond.has("type") ? itemCond.get("type").getAsString() : "";

        // Handle ingredient-style item condition
        if (itemCond.has("ingredient") && itemCond.get("ingredient").isJsonObject()) {
            JsonObject ing = itemCond.getAsJsonObject("ingredient");
            if (ing.has("item")) itemId = ing.get("item").getAsString();
            else if (ing.has("tag")) itemTag = ing.get("tag").getAsString();
        }

        final String fItemId = itemId;
        final String fItemTag = itemTag;

        if (fItemId != null) {
            Identifier targetItem = Identifier.parse(fItemId);
            return player -> {
                ItemStack stack = player.getItemBySlot(eqSlot);
                return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetItem);
            };
        }
        if (fItemTag != null) {
            var itemTagKey = TagKey.create(Registries.ITEM, Identifier.parse(fItemTag));
            return player -> {
                ItemStack stack = player.getItemBySlot(eqSlot);
                return stack.is(itemTagKey);
            };
        }

        // Nested condition type check (e.g., origins:empty for checking empty slot)
        if ("origins:empty".equals(itemType) || "apace:empty".equals(itemType)) {
            return player -> player.getItemBySlot(eqSlot).isEmpty();
        }

        return EntityCondition.alwaysTrue();
    }

    /**
     * {@code origins:inventory} — counts inventory contents matching the nested
     * {@code item_condition} and compares. Apoli fields:
     * <ul>
     *   <li>{@code inventory_types} — list of {@code inventory} /
     *       {@code ender_chest} (default: inventory only). The player main
     *       inventory includes hotbar, storage, armor and offhand slots.</li>
     *   <li>{@code process_mode} — {@code stacks} counts matching stacks,
     *       {@code items} sums their stack counts (default: stacks).</li>
     *   <li>{@code item_condition} — per-stack predicate via the shared
     *       {@link ItemConditionParser}; absent → any non-empty stack.</li>
     *   <li>{@code comparison} / {@code compare_to} — default {@code >} 0
     *       ("has at least one match").</li>
     * </ul>
     * Apoli's {@code power} field (counting slots inside an apoli:inventory
     * POWER's virtual container) is not supported — fails closed with a warn.
     */
    static EntityCondition parseInventory(JsonObject json, String contextId) {
        if (json.has("power")) {
            return failClosed("neoorigins:inventory", contextId,
                "'power' (virtual inventory-power containers) not supported");
        }
        boolean checkInventory = true, checkEnderChest = false;
        if (json.has("inventory_types") && json.get("inventory_types").isJsonArray()) {
            checkInventory = false;
            for (JsonElement el : json.getAsJsonArray("inventory_types")) {
                String t = el.getAsString();
                String leaf = t.indexOf(':') >= 0 ? t.substring(t.indexOf(':') + 1) : t;
                if (leaf.equalsIgnoreCase("inventory")) checkInventory = true;
                else if (leaf.equalsIgnoreCase("ender_chest")) checkEnderChest = true;
            }
        }
        com.cyberday1.neoorigins.compat.condition.ItemCondition itemCond =
            json.has("item_condition") && json.get("item_condition").isJsonObject()
                ? com.cyberday1.neoorigins.compat.condition.ItemConditionParser
                    .parse(json.getAsJsonObject("item_condition"))
                : null;
        boolean countItems = json.has("process_mode")
            && "items".equalsIgnoreCase(json.get("process_mode").getAsString());
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType cmp = ComparisonType.fromString(comp);
        final boolean fInv = checkInventory, fEnder = checkEnderChest;
        return player -> {
            int count = 0;
            if (fInv)   count += countMatching(player.getInventory(), itemCond, countItems);
            if (fEnder) count += countMatching(player.getEnderChestInventory(), itemCond, countItems);
            return cmp.test(count, target);
        };
    }

    /** Counts stacks (or summed item counts) in a container matching the item condition (null → any non-empty). */
    private static int countMatching(net.minecraft.world.Container container,
                                     com.cyberday1.neoorigins.compat.condition.ItemCondition cond,
                                     boolean countItems) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (cond != null && !cond.test(stack)) continue;
            count += countItems ? stack.getCount() : 1;
        }
        return count;
    }

    private static EquipmentSlot mapEquipmentSlot(String slot) {
        return switch (slot.toLowerCase()) {
            case "head"     -> EquipmentSlot.HEAD;
            case "chest"    -> EquipmentSlot.CHEST;
            case "legs"     -> EquipmentSlot.LEGS;
            case "feet"     -> EquipmentSlot.FEET;
            case "offhand"  -> EquipmentSlot.OFFHAND;
            default         -> EquipmentSlot.MAINHAND;
        };
    }

    static EntityCondition parseRelativeHealth(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            double ratio = player.getMaxHealth() > 0
                ? player.getHealth() / player.getMaxHealth() : 0.0;
            return comparison.test(ratio, target);
        };
    }

    static EntityCondition parseFallDistance(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.fallDistance, target);
    }

    static EntityCondition parseEnchantment(JsonObject json) {
        String enchantId = json.has("enchantment") ? json.get("enchantment").getAsString() : null;
        if (enchantId == null) return EntityCondition.alwaysTrue();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType comparison = ComparisonType.fromString(comp);
        Identifier eid = Identifier.parse(enchantId);
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            var enchReg = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var enchHolder = enchReg.get(eid).orElse(null);
            if (enchHolder == null) return false;
            int maxLevel = 0;
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                int lvl = stack.getEnchantmentLevel(enchHolder);
                if (lvl > maxLevel) maxLevel = lvl;
            }
            return comparison.test(maxLevel, target);
        };
    }

    static EntityCondition parseBlockCondition(JsonObject json, String contextId) {
        // Block condition at player position
        JsonObject blockCond = json.has("block_condition") ? json.getAsJsonObject("block_condition") : json;
        String blockId = blockCond.has("block") ? blockCond.get("block").getAsString() : null;
        if (blockId == null) blockId = blockCond.has("id") ? blockCond.get("id").getAsString() : null;
        if (blockId != null) {
            Identifier bid = Identifier.parse(blockId);
            return player -> {
                Block block = player.level().getBlockState(player.blockPosition()).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }
        String blockTag = blockCond.has("tag") ? blockCond.get("tag").getAsString() : null;
        if (blockTag != null) {
            var tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(blockTag));
            return player -> player.level().getBlockState(player.blockPosition()).is(tagKey);
        }
        return EntityCondition.alwaysTrue();
    }

    static EntityCondition parseLightLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        String lightType = json.has("light_type") ? json.get("light_type").getAsString() : "";
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            BlockPos pos = player.blockPosition();
            int light = switch (lightType) {
                case "sky"   -> player.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
                case "block" -> player.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
                default      -> player.level().getMaxLocalRawBrightness(pos);
            };
            return comparison.test(light, target);
        };
    }

    /**
     * {@code origins:nbt} — partial-NBT match against the entity's full NBT, the
     * way Apoli's {@code apoli:nbt} entity condition works. The {@code nbt} field
     * is an SNBT string (e.g. {@code "{Tags:[\"seer_astral\"]}"}); the condition
     * is true when the entity's serialized NBT <em>contains</em> that subtree.
     *
     * <p>Crucially this matches the vanilla {@code Tags} string-list — the
     * scoreboard tags added by {@code /tag @s add ...} — which tag-state-machine
     * packs (the Seer origin) gate nearly every power on. We use
     * {@link net.minecraft.nbt.NbtUtils#compareNbt} with {@code compareListTag=true}
     * so a single {@code Tags:["x"]} expectation is satisfied when the entity's
     * full Tags list <em>includes</em> "x" (not only when the lists are equal).
     *
     * <p>The entity NBT is read via {@code saveWithoutId}; an unparseable SNBT
     * string fails closed (never matches) with a one-shot warning.
     */
    /**
     * {@code origins:nbt} — partial-NBT match against the entity's full NBT, the
     * way Apoli's {@code apoli:nbt} entity condition works. The {@code nbt} field
     * is an SNBT string (e.g. {@code "{Tags:[\"seer_astral\"]}"}); the condition
     * is true when the entity's serialized NBT <em>contains</em> that subtree.
     *
     * <p>Crucially this matches the vanilla {@code Tags} string-list — the
     * scoreboard tags added by {@code /tag @s add ...} — which tag-state-machine
     * packs (the Seer origin) gate nearly every power on. We use
     * {@link net.minecraft.nbt.NbtUtils#compareNbt} with {@code compareListTag=true}
     * so a single {@code Tags:["x"]} expectation is satisfied when the entity's
     * full Tags list <em>includes</em> "x" (not only when the lists are equal).
     *
     * <p>26.1 note: entity serialization flows through {@code ValueOutput}/
     * {@code ValueInput} rather than raw {@code CompoundTag}; we bridge via
     * {@code TagValueOutput} (mirroring {@code BuiltinActions.applyEntityTag}).
     * {@code TagParser.parseTag} is gone on 26.1 — use {@code parseCompoundFully}.
     */
    static EntityCondition parseNbt(JsonObject json) {
        String snbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (snbt == null || snbt.isBlank()) return EntityCondition.alwaysTrue();
        final CompoundTag expected;
        try {
            expected = net.minecraft.nbt.TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] origins:nbt: could not parse SNBT '{}' ({}); condition will never match",
                snbt, e.getMessage());
            return EntityCondition.alwaysFalse();
        }
        // An empty expectation ({}) is trivially contained — match everything,
        // mirroring Apoli (an empty nbt block is a no-op gate).
        if (expected.isEmpty()) return EntityCondition.alwaysTrue();
        return player -> {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, player.level().registryAccess());
            player.saveWithoutId(out);
            CompoundTag actual = out.buildResult();
            return net.minecraft.nbt.NbtUtils.compareNbt(expected, actual, true);
        };
    }

    static EntityCondition parseScoreboard(JsonObject json) {
        String objective = json.has("objective") ? json.get("objective").getAsString() : null;
        if (objective == null) return EntityCondition.alwaysFalse();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            if (player.level().getServer() == null) return false;
            var scoreboard = player.level().getServer().getScoreboard();
            var obj = scoreboard.getObjective(objective);
            if (obj == null) return false;
            var scores = scoreboard.listPlayerScores(obj);
            for (var score : scores) {
                if (score.owner().equals(player.getScoreboardName())) {
                    return comparison.test(score.value(), target);
                }
            }
            return false;
        };
    }

    static EntityCondition parseCommand(JsonObject json) {
        String command = json.has("command") ? json.get("command").getAsString() : "";
        if (command.isBlank()) return EntityCondition.alwaysFalse();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            if (player.level().getServer() == null) return false;
            // Refuse blacklisted command roots — the command condition is an
            // execution vector too (a pack could probe with `/op @s`). A blocked
            // command evaluates as if it returned 0 (ran nothing).
            if (com.cyberday1.neoorigins.command.CommandPowerGuard.isBlocked(command)) {
                com.cyberday1.neoorigins.command.CommandPowerGuard.warnBlocked(command, "command condition");
                return comparison.test(0, target);
            }
            try {
                var src = player.createCommandSourceStack().withSuppressedOutput().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
                String cmd = command.startsWith("/") ? command.substring(1) : command;
                var dispatcher = player.level().getServer().getCommands().getDispatcher();
                int result = dispatcher.execute(cmd, src);
                return comparison.test(result, target);
            } catch (Exception e) {
                return comparison.test(0, target);
            }
        };
    }

    static EntityCondition parseFluidHeight(JsonObject json) {
        String fluid = json.has("fluid") ? json.get("fluid").getAsString() : "";
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            double height = switch (fluid) {
                case "minecraft:water" -> player.getFluidHeight(net.minecraft.tags.FluidTags.WATER);
                case "minecraft:lava"  -> player.getFluidHeight(net.minecraft.tags.FluidTags.LAVA);
                default -> 0.0;
            };
            return comparison.test(height, target);
        };
    }

    static EntityCondition parseInBlock(JsonObject json, String contextId) {
        // origins:in_block — the block occupying the player's own position must
        // match the nested block_condition. The block_condition mirrors the
        // block_condition.schema (block/id, in_tag, and/or/all_of/any_of) and
        // each node honours its own `inverted` flag. Previously only a bare
        // `block`/`id` was handled and `in_tag`/`inverted`/combinators silently
        // fell through to always-true, which made tag-gated energy-drains
        // (Seer's seer:intangible inverted check) fire unconditionally.
        JsonObject blockCond = json.has("block_condition") && json.get("block_condition").isJsonObject()
            ? json.getAsJsonObject("block_condition") : null;
        if (blockCond == null) return EntityCondition.alwaysTrue();
        java.util.function.Predicate<BlockState> pred = compileInBlockPredicate(blockCond, contextId);
        if (pred == null) return EntityCondition.alwaysTrue();
        return player -> pred.test(player.level().getBlockState(player.blockPosition()));
    }

    /**
     * Recursively compiles an {@code origins:in_block} block_condition node into
     * a {@link BlockState} predicate. Self-contained on purpose — kept separate
     * from the {@code action_on_event} block-predicate compiler. Returns
     * {@code null} for an unrecognised leaf so callers can fall back to
     * always-true. Honours a per-node {@code inverted} flag.
     */
    private static java.util.function.Predicate<BlockState> compileInBlockPredicate(JsonObject bc, String contextId) {
        boolean inverted = bc.has("inverted") && bc.get("inverted").getAsBoolean();
        java.util.function.Predicate<BlockState> base = compileInBlockLeaf(bc, contextId);
        if (base == null) return null;
        return inverted ? base.negate() : base;
    }

    private static java.util.function.Predicate<BlockState> compileInBlockLeaf(JsonObject bc, String contextId) {
        String type = bc.has("type") ? bc.get("type").getAsString() : "";
        String bare = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;

        String blockId = bc.has("block") ? bc.get("block").getAsString()
                       : bc.has("id") ? bc.get("id").getAsString() : null;
        if ((bare.equals("block") || blockId != null) && blockId != null && !blockId.isBlank()) {
            Identifier bid = Identifier.parse(blockId);
            return state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(bid);
        }
        if (bare.equals("in_tag") && bc.has("tag")) {
            TagKey<Block> tag = parseBlockTag(bc.get("tag").getAsString());
            return state -> state.is(tag);
        }
        if (bare.equals("and") || bare.equals("all_of") || bare.equals("or") || bare.equals("any_of")) {
            boolean isAnd = bare.equals("and") || bare.equals("all_of");
            JsonArray conditions = bc.has("conditions") ? bc.getAsJsonArray("conditions") : new JsonArray();
            List<java.util.function.Predicate<BlockState>> subs = new ArrayList<>();
            for (JsonElement el : conditions) {
                if (!el.isJsonObject()) continue;
                var sub = compileInBlockPredicate(el.getAsJsonObject(), contextId);
                if (sub != null) subs.add(sub);
            }
            return state -> {
                for (var s : subs) {
                    boolean r = s.test(state);
                    if (isAnd && !r) return false;
                    if (!isAnd && r) return true;
                }
                return isAnd;
            };
        }
        NeoOrigins.LOGGER.debug("[CompatB] in_block: unknown block_condition type '{}' in {} — treating as match-none", type, contextId);
        return null;
    }

    static EntityCondition parseHeight(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getY(), target);
    }

    static EntityCondition parseTemperature(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            float temp = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
            return comparison.test(temp, target);
        };
    }

    static EntityCondition parseArmorValue(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getArmorValue(), target);
    }

    static EntityCondition parseAmount(JsonObject json) {
        // Generic numeric comparison wrapper — just delegates to comparison fields
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        // amount condition is context-dependent; when standalone, check health as default
        return player -> comparison.test(player.getHealth(), target);
    }

    static EntityCondition parseEntityType(JsonObject json) {
        // For a player context, player entity type is always minecraft:player.
        // A JSON author using this against the player really only has one meaningful match.
        String expected = json.has("entity_type") ? json.get("entity_type").getAsString()
                        : json.has("type_id") ? json.get("type_id").getAsString() : "";
        if (expected.isEmpty()) return EntityCondition.alwaysTrue();
        final String target = expected;
        return p -> {
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(p.getType());
            return typeId != null && typeId.toString().equals(target);
        };
    }

    static EntityCondition parsePowerType(JsonObject json, String contextId) {
        // Predicate: "does the player have any granted power whose type matches this id?"
        // Delegates to ActiveOriginService for the lookup.
        String expected = json.has("power_type") ? json.get("power_type").getAsString()
                        : json.has("id") ? json.get("id").getAsString() : null;
        if (expected == null || expected.isBlank()) {
            return failClosed("origins:power_type", contextId, "missing 'power_type' field");
        }
        final String target = expected.indexOf(':') < 0 ? "origins:" + expected : expected;
        return p -> {
            var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            for (var originEntry : data.getOrigins().entrySet()) {
                var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(originEntry.getValue());
                if (origin == null) continue;
                for (Identifier powerId : origin.powers()) {
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder == null) continue;
                    Identifier typeId = com.cyberday1.neoorigins.power.registry.PowerTypes.getId(holder.type());
                    if (typeId != null && typeId.toString().equals(target)) return true;
                }
            }
            return false;
        };
    }

    // ---- origins:predicate (Apoli meta-wrapper around vanilla MC predicates) ----

    static EntityCondition parsePredicate(JsonObject json, String contextId) {
        String predicateType = json.has("predicate_type") ? json.get("predicate_type").getAsString() : null;
        JsonElement predicateJson = json.has("predicate") ? json.get("predicate") : null;
        if (predicateType == null || predicateJson == null) {
            return failClosed("origins:predicate", contextId,
                "missing required field 'predicate_type' or 'predicate'");
        }
        return switch (predicateType) {
            case "biome"             -> parseBiomePredicate(predicateJson, contextId);
            case "block_state"       -> parseBlockStatePredicate(predicateJson, contextId);
            case "entity_properties" -> parseEntityPropertiesPredicate(predicateJson, contextId);
            case "fluid_state"       -> parseFluidStatePredicate(predicateJson, contextId);
            case "item"              -> parseItemPredicate(predicateJson, contextId);
            case "location"          -> parseLocationPredicate(predicateJson, contextId);
            case "damage"            -> failClosed("origins:predicate", contextId,
                "predicate_type 'damage' requires damage-source context (use action-on-hit hooks)");
            default                  -> failClosed("origins:predicate", contextId,
                "unknown predicate_type '" + predicateType + "'");
        };
    }

    private static EntityCondition parseBiomePredicate(JsonElement predicateJson, String contextId) {
        if (!predicateJson.isJsonObject()) {
            return failClosed("origins:predicate/biome", contextId, "predicate must be a JSON object");
        }
        JsonObject obj = predicateJson.getAsJsonObject();
        List<ResourceKey<Biome>> biomeKeys = new ArrayList<>();
        if (obj.has("biomes") && obj.get("biomes").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("biomes")) {
                biomeKeys.add(ResourceKey.create(Registries.BIOME, Identifier.parse(el.getAsString())));
            }
        }
        TagKey<Biome> tagKey = null;
        if (obj.has("tag")) {
            tagKey = TagKey.create(Registries.BIOME, Identifier.parse(obj.get("tag").getAsString()));
        }
        if (biomeKeys.isEmpty() && tagKey == null) {
            return failClosed("origins:predicate/biome", contextId, "expected 'biomes' list or 'tag'");
        }
        final TagKey<Biome> fTagKey = tagKey;
        return player -> {
            Holder<Biome> holder = player.level().getBiome(player.blockPosition());
            if (fTagKey != null && holder.is(fTagKey)) return true;
            for (ResourceKey<Biome> key : biomeKeys) {
                if (holder.is(key)) return true;
            }
            return false;
        };
    }

    private static EntityCondition parseBlockStatePredicate(JsonElement predicateJson, String contextId) {
        DataResult<BlockPredicate> result = BlockPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/block_state", contextId,
                result.error().get().message());
        }
        BlockPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.blockPosition());
        };
    }

    private static EntityCondition parseEntityPropertiesPredicate(JsonElement predicateJson, String contextId) {
        DataResult<EntityPredicate> result = EntityPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/entity_properties", contextId,
                result.error().get().message());
        }
        EntityPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.position(), player);
        };
    }

    private static EntityCondition parseFluidStatePredicate(JsonElement predicateJson, String contextId) {
        DataResult<FluidPredicate> result = FluidPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/fluid_state", contextId,
                result.error().get().message());
        }
        FluidPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.blockPosition());
        };
    }

    private static EntityCondition parseItemPredicate(JsonElement predicateJson, String contextId) {
        DataResult<ItemPredicate> result = ItemPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/item", contextId,
                result.error().get().message());
        }
        ItemPredicate pred = result.result().orElseThrow();
        return player -> pred.test(player.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    private static EntityCondition parseLocationPredicate(JsonElement predicateJson, String contextId) {
        DataResult<LocationPredicate> result = LocationPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/location", contextId,
                result.error().get().message());
        }
        LocationPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.getX(), player.getY(), player.getZ());
        };
    }

    static EntityCondition parseTimeOfDay(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        long target = json.has("compare_to") ? json.get("compare_to").getAsLong() : 0L;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.level().getDefaultClockTime() % 24000L, target);
    }

    static EntityCondition parseWeather(JsonObject json) {
        String state = json.has("state") ? json.get("state").getAsString().toLowerCase()
                     : json.has("value") ? json.get("value").getAsString().toLowerCase() : "clear";
        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            return switch (state) {
                case "clear" -> !sl.isRaining() && !sl.isThundering();
                case "rain", "raining" -> sl.isRaining() && !sl.isThundering();
                case "thunder", "thundering" -> sl.isThundering();
                default -> false;
            };
        };
    }

    static EntityCondition parseXpLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.experienceLevel, target);
    }

    static EntityCondition parseXpPoints(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.totalExperience, target);
    }

    static EntityCondition parseMoonPhase(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : "==";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            // MC's moon phase is an 8-phase cycle derived from the world clock.
            int phase = (int) ((sl.getDefaultClockTime() / 24000L) % 8L);
            if (phase < 0) phase += 8;
            return comparison.test(phase, target);
        };
    }

    /**
     * Context-aware condition that checks whether the current FOOD_EATEN
     * event's held {@link ItemStack} is in the named item tag. Requires an
     * active {@link com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext}
     * in the {@link com.cyberday1.neoorigins.service.ActionContextHolder};
     * evaluates to false outside that context. Used by the
     * {@code food_restriction} alias to re-express its item-tag filter.
     */
    static EntityCondition parseFoodItemInTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null) return CompatPolicy.FALSE_CONDITION;
        // If the entry starts with '#', treat as a tag; otherwise match a specific item id.
        // The leading '#' MUST be stripped before Identifier.parse — leaving it in
        // yields a TagKey that matches nothing, which (via the food_restriction if_else)
        // cancels every eat. Mirrors the 1.21.1 branch.
        if (tag.startsWith("#")) {
            TagKey<net.minecraft.world.item.Item> itemTag =
                TagKey.create(Registries.ITEM, Identifier.parse(tag.substring(1)));
            return p -> {
                Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc)) {
                    return false;
                }
                return fc.stack().is(itemTag);
            };
        } else {
            Identifier itemId = Identifier.parse(tag);
            var itemHolderOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
            if (itemHolderOpt.isEmpty()) return CompatPolicy.FALSE_CONDITION;
            net.minecraft.world.item.Item targetItem = itemHolderOpt.get().value();
            return p -> {
                Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc)) {
                    return false;
                }
                return fc.stack().getItem() == targetItem;
            };
        }
    }

    /**
     * Context-aware condition that checks whether the current FOOD_EATEN event's
     * held {@link ItemStack} matches any id or {@code #tag} entry in a server
     * config list, named by {@code key}. Additive companion to
     * {@link #parseFoodItemInTag}: lets server owners whitelist modded fish for
     * the ocean-origin diet via {@code ocean_origins.extra_fish_foods} without a
     * datapack. Requires an active FoodContext; false outside it, and false for
     * an unknown key (logged).
     */
    static EntityCondition parseFoodItemInConfigList(JsonObject json) {
        String key = json.has("key") ? json.get("key").getAsString() : null;
        if (key == null) return CompatPolicy.FALSE_CONDITION;
        var supplier = CONFIG_LIST_LOOKUPS.get(key);
        if (supplier == null) {
            NeoOrigins.LOGGER.warn(
                "[CompatB] food_item_in_config_list: unknown key '{}'. Supported keys: {}. Evaluating false.",
                key, CONFIG_LIST_LOOKUPS.keySet());
            return CompatPolicy.FALSE_CONDITION;
        }
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc)) {
                return false;
            }
            ItemStack stack = fc.stack();
            Identifier stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (String entry : supplier.get()) {
                if (entry == null || entry.isBlank()) continue;
                if (entry.startsWith("#")) {
                    // Tag ref: resolve the item tag and test membership.
                    TagKey<net.minecraft.world.item.Item> itemTag =
                        TagKey.create(Registries.ITEM, Identifier.parse(entry.substring(1)));
                    if (stack.is(itemTag)) return true;
                } else {
                    // Bare item id: exact match.
                    if (stackId.equals(Identifier.parse(entry))) return true;
                }
            }
            return false;
        };
    }

    /**
     * Sibling of {@link #parseFoodItemInTag} that matches a single item ID
     * exactly instead of a tag. Used by the aquatic-origin "fish diet" power
     * to give per-item food bonuses (raw cod → cooked cod values, raw salmon
     * → cooked salmon values) without needing one tag per fish item.
     */
    static EntityCondition parseFoodItemId(JsonObject json) {
        String idStr = json.has("id") ? json.get("id").getAsString() : null;
        if (idStr == null) return CompatPolicy.FALSE_CONDITION;
        Identifier itemId = Identifier.parse(idStr);
        // 26.1: BuiltInRegistries.ITEM.get returns Optional<Holder<Item>> —
        // unwrap with .value() to compare against ItemStack.getItem().
        var itemHolderOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        if (itemHolderOpt.isEmpty()) return CompatPolicy.FALSE_CONDITION;
        net.minecraft.world.item.Item targetItem = itemHolderOpt.get().value();
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc)) {
                return false;
            }
            return fc.stack().getItem() == targetItem;
        };
    }

    /**
     * Context-aware condition that compares the current HIT_TAKEN event's
     * {@code amount} field against a threshold. Requires an active
     * {@link com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext}
     * in the {@link com.cyberday1.neoorigins.service.ActionContextHolder} —
     * evaluates to false outside that context. Used by the
     * {@code action_on_hit_taken} alias to re-express {@code min_damage}
     * gating.
     */
    static EntityCondition parseHitTakenAmount(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) {
                return false;
            }
            return comparison.test(htc.amount(), target);
        };
    }

    /**
     * Context-aware condition that compares the current HIT_DEALT event's
     * {@code amount} (the most recent damage the player dealt to a target)
     * against a threshold. Requires an active
     * {@link com.cyberday1.neoorigins.service.EventPowerIndex.HitDealtContext}
     * in the {@link com.cyberday1.neoorigins.service.ActionContextHolder} —
     * evaluates to false outside that context. The attacker-side mirror of
     * {@link #parseHitTakenAmount}.
     */
    static EntityCondition parseHitDealtAmount(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitDealtContext hdc)) {
                return false;
            }
            return comparison.test(hdc.amount(), target);
        };
    }

    /**
     * no_minions_alive: true when the player has no tracked minions of the given
     * {@code key} (default "tamer:tamed"). Used by Monster Tamer's Lone Weakness
     * to only penalise the player when they're fighting without their pack.
     */
    static EntityCondition parseNoMinionsAlive(JsonObject json) {
        final String minionKey = json.has("key") ? json.get("key").getAsString() : "tamer:tamed";
        return p -> com.cyberday1.neoorigins.service.MinionTracker.countAlive(p.getUUID(), minionKey) == 0;
    }

    // ---- Bientity helpers ----

    /**
     * Extract the "target" LivingEntity from the current dispatch context.
     * Returns null outside any bientity-relevant context, causing bientity
     * conditions to fail closed.
     */
    private static net.minecraft.world.entity.LivingEntity extractTarget(Object ctx) {
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc) {
            var e = htc.source().getEntity();
            return e instanceof net.minecraft.world.entity.LivingEntity le ? le : null;
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitDealtContext hdc) {
            return hdc.target();
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.KillContext kc) {
            return kc.killed();
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext eic) {
            return eic.target();
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
            if (phc.result() instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof net.minecraft.world.entity.LivingEntity le) {
                return le;
            }
        }
        return null;
    }

    /** Extract the DamageSource from HitTakenContext; null outside a hit-taken dispatch. */
    private static net.minecraft.world.damagesource.DamageSource extractDamageSource(Object ctx) {
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc) {
            return htc.source();
        }
        return null;
    }

    static EntityCondition parseDistance(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : "<=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            return comparison.test(p.distanceTo(le), target);
        };
    }

    /**
     * distance_from_coordinates: compares the player's distance from a reference
     * coordinate set against {@code compare_to}. Mirrors Apoli's
     * {@code DistanceFromCoordinatesCondition}.
     *
     * <p>The reference point is {@code world_origin} (0,0,0) or {@code world_spawn}
     * (the level's shared spawn), shifted by an optional per-axis {@code offset}.
     * Each axis can be excluded via {@code ignore_x/y/z}. The metric is selected
     * by {@code shape}: {@code cube} (Chebyshev / max-axis, the Apoli default),
     * {@code star} (Manhattan / sum-of-axes), or {@code sphere} (Euclidean).
     * Result is compared with the vanilla operator vocabulary.
     *
     * <p>{@code result_on_the_wrong_dimension}: when the player is not in the
     * reference's dimension (the overworld for world_origin/world_spawn), the
     * configured value is substituted for the computed distance before comparison
     * — packs use a large sentinel (e.g. 999999) to force a {@code <}-style gate
     * to fail off-dimension. Absent → the real cross-dimension distance is used.
     *
     * <pre>{ "type": "neoorigins:distance_from_coordinates",
     *        "offset": { "x": 0, "y": 0, "z": 3 },
     *        "comparison": "<", "compare_to": 2,
     *        "result_on_the_wrong_dimension": 999999 }</pre>
     */
    static EntityCondition parseDistanceFromCoordinates(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : "==";
        ComparisonType comparison = ComparisonType.fromString(comp);
        double compareTo = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;

        String reference = json.has("reference") ? json.get("reference").getAsString() : "world_origin";

        double offX = 0, offY = 0, offZ = 0;
        if (json.has("offset") && json.get("offset").isJsonObject()) {
            JsonObject off = json.getAsJsonObject("offset");
            offX = off.has("x") ? off.get("x").getAsDouble() : 0;
            offY = off.has("y") ? off.get("y").getAsDouble() : 0;
            offZ = off.has("z") ? off.get("z").getAsDouble() : 0;
        }
        final double fOffX = offX, fOffY = offY, fOffZ = offZ;

        final boolean ignoreX = json.has("ignore_x") && json.get("ignore_x").getAsBoolean();
        final boolean ignoreY = json.has("ignore_y") && json.get("ignore_y").getAsBoolean();
        final boolean ignoreZ = json.has("ignore_z") && json.get("ignore_z").getAsBoolean();

        final String shape = json.has("shape") ? json.get("shape").getAsString() : "cube";

        final Double wrongDim = json.has("result_on_the_wrong_dimension")
            ? json.get("result_on_the_wrong_dimension").getAsDouble() : null;

        final boolean fromSpawn = "world_spawn".equals(reference);

        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;

            // Reference (and its dimension) is the OVERWORLD for both
            // world_origin and world_spawn. Off-dimension → substitute the
            // configured sentinel distance, else fall through to the real value.
            boolean wrongDimension =
                !sl.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);

            double refX, refY, refZ;
            if (fromSpawn) {
                // 26.1.2 removed Level#getSharedSpawnPos()/LevelData#getSpawnPos();
                // the world spawn now lives in the level's RespawnData (matches the
                // 1.21.1 getSharedSpawnPos() intent).
                ServerLevel ow = sl.getServer() != null ? sl.getServer().overworld() : sl;
                BlockPos spawn = ow.getRespawnData().pos();
                refX = spawn.getX(); refY = spawn.getY(); refZ = spawn.getZ();
            } else {
                refX = 0; refY = 0; refZ = 0;
            }
            refX += fOffX; refY += fOffY; refZ += fOffZ;

            double dist;
            if (wrongDimension && wrongDim != null) {
                dist = wrongDim;
            } else {
                double dx = ignoreX ? 0 : Math.abs(p.getX() - refX);
                double dy = ignoreY ? 0 : Math.abs(p.getY() - refY);
                double dz = ignoreZ ? 0 : Math.abs(p.getZ() - refZ);
                dist = switch (shape) {
                    case "star"   -> dx + dy + dz;                       // Manhattan
                    case "sphere" -> Math.sqrt(dx * dx + dy * dy + dz * dz); // Euclidean
                    default       -> Math.max(dx, Math.max(dy, dz));     // cube / Chebyshev
                };
            }
            return comparison.test(dist, compareTo);
        };
    }

    static EntityCondition parseCanSee() {
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            return p.hasLineOfSight(le);
        };
    }

    static EntityCondition parseEqual() {
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return le != null && le.getUUID().equals(p.getUUID());
        };
    }

    static EntityCondition parseTargetType(JsonObject json) {
        String et = json.has("entity_type") ? json.get("entity_type").getAsString() : null;
        if (et == null || et.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final String target = et;
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            if (target.startsWith("#")) {
                TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(
                    Registries.ENTITY_TYPE, Identifier.parse(target.substring(1)));
                return le.getType().getTags().anyMatch(t -> t.equals(tag));
            }
            Identifier expected = Identifier.parse(target);
            Identifier actual = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
            return expected.equals(actual);
        };
    }

    static EntityCondition parseTargetGroup(JsonObject json) {
        String group = json.has("group") ? json.get("group").getAsString() : null;
        if (group == null || group.isBlank()) return CompatPolicy.FALSE_CONDITION;
        TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("minecraft", group));
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            return le.getType().getTags().anyMatch(t -> t.equals(tag));
        };
    }

    /**
     * Bientity condition: true iff the current target's UUID is in the actor's named
     * entity-set. The {@code set} field is used verbatim as the key — pack authors
     * are expected to namespace it (e.g. {@code "mypack:kill_streak"}) to avoid collision.
     * Fails closed outside a bientity context.
     */
    static EntityCondition parseInSet(JsonObject json, String contextId) {
        String setName = json.has("set") ? json.get("set").getAsString() : null;
        if (setName == null || setName.isBlank()) {
            return failClosed("origins:in_set", contextId, "missing required field 'set'");
        }
        final String key = setName;
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            return data.getEntitySet(key).contains(le.getUUID());
        };
    }

    // ---- Damage helpers ----

    static EntityCondition parseFromFire() {
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
        };
    }

    static EntityCondition parseFromProjectile() {
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
        };
    }

    static EntityCondition parseFromExplosion() {
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        };
    }

    static EntityCondition parseDamageType(JsonObject json) {
        String id = json.has("damage_type") ? json.get("damage_type").getAsString() : null;
        if (id == null || id.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final ResourceKey<net.minecraft.world.damagesource.DamageType> key =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(id));
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(key);
        };
    }

    static EntityCondition parseDamageTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null || tag.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final TagKey<net.minecraft.world.damagesource.DamageType> key =
            TagKey.create(Registries.DAMAGE_TYPE, Identifier.parse(tag.startsWith("#") ? tag.substring(1) : tag));
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(key);
        };
    }

    static EntityCondition parseDamageName(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : null;
        if (name == null || name.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final String expected = name;
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && expected.equalsIgnoreCase(src.getMsgId());
        };
    }

    /**
     * has_effect: true when the player has the specified MobEffect active.
     * <pre>{ "type": "neoorigins:has_effect", "effect": "minecraft:luck" }</pre>
     * Useful for gating passives on consumable-applied buffs (mirrors the
     * FortuneWhenEffectPower gate pattern for DSL authors).
     */
    static EntityCondition parseHasEffect(JsonObject json) {
        if (!json.has("effect")) return CompatPolicy.FALSE_CONDITION;
        Identifier id = Identifier.parse(json.get("effect").getAsString());
        return p -> {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(id);
            if (effect.isEmpty()) return false;
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get());
            return p.hasEffect(holder);
        };
    }

    /**
     * near_block: true when any matching block is within {@code radius} blocks
     * (default 4, capped at 8) of the player. Accepts any combination of:
     * <ul>
     *   <li>{@code block} — single block ID</li>
     *   <li>{@code blocks} — list of block IDs</li>
     *   <li>{@code tag} — single block tag (with or without leading {@code #})</li>
     *   <li>{@code tags} — list of block tags</li>
     * </ul>
     * A block matches if it's in ANY of the provided blocks/tags (logical OR).
     *
     * <pre>{ "type": "neoorigins:near_block", "block": "minecraft:lava", "radius": 3 }</pre>
     * <pre>{ "type": "neoorigins:near_block", "tags": ["minecraft:campfires", "#c:fire"], "radius": 5 }</pre>
     *
     * Scans a cubic AABB; intended for ambient proximity buffs (campfire
     * warmth, lava-side speed, etc.). Capped at radius 8 to avoid overly
     * expensive per-tick scans.
     */
    static EntityCondition parseNearBlock(JsonObject json, String contextId) {
        int radius = Math.min(8, Math.max(1,
            json.has("radius") ? json.get("radius").getAsInt() : 4));
        List<Identifier> blockIds = new ArrayList<>();
        List<TagKey<Block>> tags = new ArrayList<>();

        if (json.has("block")) {
            blockIds.add(Identifier.parse(json.get("block").getAsString()));
        }
        if (json.has("blocks") && json.get("blocks").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("blocks")) {
                if (el.isJsonPrimitive()) blockIds.add(Identifier.parse(el.getAsString()));
            }
        }
        if (json.has("tag")) {
            tags.add(parseBlockTag(json.get("tag").getAsString()));
        }
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("tags")) {
                if (el.isJsonPrimitive()) tags.add(parseBlockTag(el.getAsString()));
            }
        }

        // Origins block_in_radius format: nested block_condition object
        if (json.has("block_condition") && json.get("block_condition").isJsonObject()) {
            JsonObject bc = json.getAsJsonObject("block_condition");
            String bcType = bc.has("type") ? bc.get("type").getAsString() : "";
            if ((bcType.equals("origins:in_tag") || bcType.equals("apace:in_tag")
                    || bcType.equals("neoorigins:in_tag")) && bc.has("tag")) {
                tags.add(parseBlockTag(bc.get("tag").getAsString()));
            } else if ((bcType.equals("origins:block") || bcType.equals("apace:block")
                    || bcType.equals("neoorigins:block")) && bc.has("block")) {
                blockIds.add(Identifier.parse(bc.get("block").getAsString()));
            }
        }

        if (blockIds.isEmpty() && tags.isEmpty()) return failClosed("neoorigins:near_block",
            contextId, "requires 'block'/'blocks' or 'tag'/'tags' or 'block_condition'");

        final int r = radius;
        final List<Identifier> finalBlockIds = List.copyOf(blockIds);
        final List<TagKey<Block>> finalTags = List.copyOf(tags);
        return p -> {
            BlockPos origin = p.blockPosition();
            Level level = p.level();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        for (Identifier id : finalBlockIds) {
                            var block = BuiltInRegistries.BLOCK.getOptional(id);
                            if (block.isPresent() && state.is(block.get())) return true;
                        }
                        for (TagKey<Block> tag : finalTags) {
                            if (state.is(tag)) return true;
                        }
                    }
                }
            }
            return false;
        };
    }

    private static TagKey<Block> parseBlockTag(String raw) {
        if (raw.startsWith("#")) raw = raw.substring(1);
        return TagKey.create(Registries.BLOCK, Identifier.parse(raw));
    }

    // ── Origins++ compat conditions ──────────────────────────────────────

    /** origins:status_effect — true when player has a specific effect at a given amplifier. */
    static EntityCondition parseStatusEffect(JsonObject json) {
        // Apoli format: { "effect": "minecraft:speed", "min_amplifier": 0, "max_amplifier": 2 }
        // or just { "effect": "...", "amplifier": 0 }
        String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
        if (effectId == null) return CompatPolicy.FALSE_CONDITION;
        Identifier id = Identifier.parse(effectId);
        int minAmp = json.has("min_amplifier") ? json.get("min_amplifier").getAsInt() : -1;
        int maxAmp = json.has("max_amplifier") ? json.get("max_amplifier").getAsInt() : Integer.MAX_VALUE;
        if (json.has("amplifier")) { minAmp = json.get("amplifier").getAsInt(); maxAmp = minAmp; }
        final int fMin = minAmp, fMax = maxAmp;
        return p -> {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(id);
            if (effect.isEmpty()) return false;
            var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get());
            var inst = p.getEffect(holder);
            if (inst == null) return false;
            int amp = inst.getAmplifier();
            return amp >= fMin && amp <= fMax;
        };
    }

    /** origins:air — compare player's air supply. */
    static EntityCondition parseAir(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.getAirSupply(), target);
    }

    /** origins:power — true when the player has a specific power granted. */
    static EntityCondition parsePower(JsonObject json, String contextId) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null) return failClosed("neoorigins:power", contextId, "missing 'power' field");
        Identifier id = Identifier.parse(powerId);
        return p -> {
            var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            for (var entry : data.getOrigins().entrySet()) {
                var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                if (origin != null && origin.powers().contains(id)) return true;
            }
            // Also check dynamic grants
            return data.hasDynamicGrant(id);
        };
    }

    /**
     * origins:origin — true when the entity currently has the given origin. The
     * {@code origin} field is the origin id (required); the optional {@code layer}
     * field restricts the match to a single origin layer (absent → any layer).
     * Mirrors Apoli's {@code origins:origin} entity condition so other-mod packs
     * that gate powers on "does this player have origin X" translate correctly.
     */
    static EntityCondition parseOrigin(JsonObject json, String contextId) {
        String originStr = json.has("origin") ? json.get("origin").getAsString() : null;
        if (originStr == null) return failClosed("neoorigins:origin", contextId, "missing 'origin' field");
        Identifier wantOrigin = Identifier.parse(originStr);
        Identifier wantLayer = json.has("layer") ? Identifier.parse(json.get("layer").getAsString()) : null;
        return p -> {
            var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            for (var entry : data.getOrigins().entrySet()) {
                if (wantLayer != null && !entry.getKey().equals(wantLayer)) continue;
                if (wantOrigin.equals(entry.getValue())) return true;
            }
            return false;
        };
    }

    /** origins:replacable / replaceable — true when the block at the player's position is replaceable (air, grass, etc.). */
    static EntityCondition parseReplaceable(JsonObject json) {
        return p -> p.level().getBlockState(p.blockPosition()).canBeReplaced();
    }

    /** origins:actor_condition — unwrap and delegate to the inner condition. In Apoli this
     *  filters the "actor" in a bientity context; here we just evaluate on the player. */
    static EntityCondition parseActorCondition(JsonObject json, String contextId) {
        if (json.has("condition") && json.get("condition").isJsonObject()) {
            return ConditionParser.parse(json.getAsJsonObject("condition"), contextId);
        }
        return failClosed("neoorigins:actor_condition", contextId, "missing 'condition' field");
    }

    /** origins:advancement — true when the player has completed a specific advancement. */
    static EntityCondition parseAdvancement(JsonObject json, String contextId) {
        String advId = json.has("advancement") ? json.get("advancement").getAsString() : null;
        if (advId == null) return failClosed("neoorigins:advancement", contextId, "missing 'advancement' field");
        Identifier id = Identifier.parse(advId);
        return p -> {
            if (!(p.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
            var adv = sl.getServer().getAdvancements().get(id);
            if (adv == null) return false;
            return p.getAdvancements().getOrStartProgress(adv).isDone();
        };
    }

    /**
     * near_entity: true when at least one entity of the given type (or tag) is
     * within {@code distance} blocks of the player. Uses an AABB scan capped at
     * 64 blocks to avoid expensive per-tick searches.
     *
     * <pre>{ "type": "neoorigins:near_entity", "entity_type": "minecraft:creeper", "distance": 8 }</pre>
     * <pre>{ "type": "neoorigins:near_entity", "entity_type": "#minecraft:undead", "distance": 16 }</pre>
     */
    static EntityCondition parseNearEntity(JsonObject json, String contextId) {
        String rawType = json.has("entity_type") ? json.get("entity_type").getAsString() : null;
        if (rawType == null || rawType.isBlank()) {
            return failClosed("neoorigins:near_entity", contextId, "missing 'entity_type' field");
        }
        double distance = Math.min(64.0, Math.max(1.0,
            json.has("distance") ? json.get("distance").getAsDouble() : 8.0));
        final double distSq = distance * distance;
        final double dist = distance;

        if (rawType.startsWith("#")) {
            TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(
                Registries.ENTITY_TYPE, Identifier.parse(rawType.substring(1)));
            return p -> {
                var aabb = p.getBoundingBox().inflate(dist);
                for (var entity : p.level().getEntities(p, aabb)) {
                    if (entity.getType().getTags().anyMatch(t -> t.equals(tag)) && entity.distanceToSqr(p) <= distSq) return true;
                }
                return false;
            };
        } else {
            Identifier typeId = Identifier.parse(rawType);
            var typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
            if (typeOpt.isEmpty()) {
                return failClosed("neoorigins:near_entity", contextId,
                    "unknown entity type '" + rawType + "'");
            }
            net.minecraft.world.entity.EntityType<?> targetType = typeOpt.get();
            return p -> {
                var aabb = p.getBoundingBox().inflate(dist);
                for (var entity : p.level().getEntities(p, aabb)) {
                    if (entity.getType() == targetType && entity.distanceToSqr(p) <= distSq) return true;
                }
                return false;
            };
        }
    }

    /**
     * out_of_combat: true when {@code ticks} or more have elapsed since the
     * player last took damage. Default threshold 100 ticks (5 s).
     * <pre>{ "type": "neoorigins:out_of_combat" }</pre>
     * <pre>{ "type": "neoorigins:out_of_combat", "ticks": 200 }</pre>
     * Backed by {@link com.cyberday1.neoorigins.service.CombatTracker} which
     * timestamps damage hits via {@code CombatPowerEvents.onLivingDamage}
     * and is forgotten on logout.
     */
    static EntityCondition parseOutOfCombat(JsonObject json) {
        int threshold = json.has("ticks") ? Math.max(0, json.get("ticks").getAsInt()) : 100;
        return p -> {
            if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return true;
            return com.cyberday1.neoorigins.service.CombatTracker.ticksSinceLastDamage(sp) >= threshold;
        };
    }

    private static EntityCondition failClosed(String type, String contextId, String detail) {
        com.cyberday1.neoorigins.compat.CompatWarningCollector
            .recordUnsupportedCondition(type, contextId, detail);
        CompatPolicy.recordFailClosed();
        return CompatPolicy.FALSE_CONDITION;
    }

    // ── Vampires Need Umbrellas compat ──────────────────────────────────

    /** Cached result of mod-loaded check so we don't query ModList every tick. */
    private static final boolean VNU_LOADED = neoorigins$modLoaded("vampiresneedumbrellas");
    private static final boolean CURIOS_LOADED = neoorigins$modLoaded("curios");

    /**
     * Null-safe mod-loaded check. {@code ModList.get()} returns null outside a
     * loaded FML environment (e.g. the headless compat golden-master harness,
     * which references {@link #KNOWN_TYPES} and so triggers this class's static
     * init). In-game {@code ModList.get()} is never null, so this is behaviorally
     * identical to the eager call at runtime.
     */
    private static boolean neoorigins$modLoaded(String modId) {
        net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
        return list != null && list.isLoaded(modId);
    }

    /**
     * Returns true if the player has an umbrella from Vampires Need Umbrellas
     * equipped — either hand or any Curios/Accessories slot.
     */
    static boolean neoorigins$isHoldingUmbrella(net.minecraft.world.entity.LivingEntity entity) {
        if (!VNU_LOADED) return false;
        if (neoorigins$isUmbrella(entity.getMainHandItem())) return true;
        if (neoorigins$isUmbrella(entity.getOffhandItem())) return true;
        if (CURIOS_LOADED) {
            return neoorigins$checkCuriosForUmbrella(entity);
        }
        return false;
    }

    private static boolean neoorigins$isUmbrella(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.resources.Identifier id =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "vampiresneedumbrellas".equals(id.getNamespace());
    }

    /**
     * Scan all equipped Curios slots for an umbrella via reflection.
     * Curios is not a compile-time dependency so we resolve the API at
     * runtime. The reflected method handle is cached after the first call.
     */
    private static java.lang.reflect.Method CURIOS_GET_INVENTORY;
    private static java.lang.reflect.Method CURIOS_GET_EQUIPPED;
    private static boolean CURIOS_REFLECT_FAILED = false;

    private static boolean neoorigins$checkCuriosForUmbrella(net.minecraft.world.entity.LivingEntity entity) {
        if (CURIOS_REFLECT_FAILED) return false;
        try {
            if (CURIOS_GET_INVENTORY == null) {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                CURIOS_GET_INVENTORY = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                // ICuriosItemHandler.getEquippedCurios() returns IItemHandlerModifiable
                Class<?> handlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                CURIOS_GET_EQUIPPED = handlerClass.getMethod("getEquippedCurios");
            }
            // CuriosApi.getCuriosInventory(entity) -> Optional<ICuriosItemHandler>
            @SuppressWarnings("unchecked")
            java.util.Optional<?> opt = (java.util.Optional<?>) CURIOS_GET_INVENTORY.invoke(null, entity);
            if (opt.isPresent()) {
                Object handler = opt.get();
                // handler.getEquippedCurios() -> IItemHandlerModifiable
                var equipped = (net.neoforged.neoforge.items.IItemHandlerModifiable) CURIOS_GET_EQUIPPED.invoke(handler);
                for (int i = 0; i < equipped.getSlots(); i++) {
                    if (neoorigins$isUmbrella(equipped.getStackInSlot(i))) return true;
                }
            }
        } catch (Exception e) {
            // Curios API not available or changed — disable further attempts
            CURIOS_REFLECT_FAILED = true;
        }
        return false;
    }
}
