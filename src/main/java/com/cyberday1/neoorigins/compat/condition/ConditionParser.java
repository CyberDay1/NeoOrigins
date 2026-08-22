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
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.FluidPredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        "neoorigins:always_active",
        "neoorigins:amount", "neoorigins:and", "neoorigins:armor_value",
        "neoorigins:biome", "neoorigins:block", "neoorigins:block_collision",
        "neoorigins:body_temperature",
        "neoorigins:brightness", "neoorigins:can_see", "neoorigins:climbing",
        "neoorigins:climbing_gate", "neoorigins:collided_horizontally",
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
        "neoorigins:in_block", "neoorigins:in_block_anywhere", "neoorigins:in_rain",
        "neoorigins:in_set", "neoorigins:in_tag", "neoorigins:in_water",
        "neoorigins:inventory",
        "neoorigins:invisible", "neoorigins:lava", "neoorigins:light_level",
        "neoorigins:living", "neoorigins:moon_phase", "neoorigins:moving",
        "neoorigins:nbt", "neoorigins:near_block", "neoorigins:near_entity",
        "neoorigins:near_villager", "neoorigins:nearby_entities",
        "neoorigins:night", "neoorigins:no_minions_alive", "neoorigins:not",
        "neoorigins:on_block", "neoorigins:on_fire", "neoorigins:on_ground",
        "neoorigins:or", "neoorigins:origin", "neoorigins:out_of_combat", "neoorigins:passenger",
        "neoorigins:power", "neoorigins:power_active", "neoorigins:power_type",
        "neoorigins:predicate", "neoorigins:relative_health", "neoorigins:replacable",
        "neoorigins:resource", "neoorigins:scoreboard", "neoorigins:sneaking",
        "neoorigins:sprinting", "neoorigins:statistic",
        "neoorigins:status_effect", "neoorigins:submerged_in",
        "neoorigins:submerged_in_water", "neoorigins:swimming", "neoorigins:target_group",
        "neoorigins:target_type", "neoorigins:temperature", "neoorigins:thundering",
        "neoorigins:ticking", "neoorigins:time_of_day", "neoorigins:using_item",
        "neoorigins:water", "neoorigins:weather", "neoorigins:xp_level",
        "neoorigins:xp_levels", "neoorigins:xp_points");

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    /**
     * Helmets in this tag are open/mesh (e.g. chainmail) and do NOT block sun
     * damage — a worn helmet only shades the player if it is absent from this
     * tag. Datapack-extensible: add modded see-through helmets by appending to
     * {@code data/<ns>/tags/item/sun_permeable.json}.
     */
    private static final TagKey<net.minecraft.world.item.Item> SUN_PERMEABLE_HELMETS =
        TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "sun_permeable"));

    /**
     * Items in this tag act as umbrellas: held in either hand or worn in a
     * Curios/Accessories slot they block both {@code exposed_to_sun} and
     * {@code in_rain}. Datapack-extensible: add any modded umbrella by appending
     * to {@code data/<ns>/tags/item/umbrellas.json}. The tag is consulted
     * regardless of which mods are installed, so it is the supported way to
     * register an umbrella that NeoOrigins does not know about.
     */
    private static final TagKey<net.minecraft.world.item.Item> UMBRELLAS =
        TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "umbrellas"));

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
        return com.cyberday1.neoorigins.compat.util.JsonHelpers.parseArrayOrSingle(
            parent, field, contextId,
            EntityCondition.alwaysTrue(),
            ConditionParser::parse,
            list -> player -> {
                for (EntityCondition c : list) if (!c.test(player)) return false;
                return true;
            });
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
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "conditions");
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
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "conditions");
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
        return ConditionParser::isExposedToSun;
    }

    /**
     * True if {@code p} is currently taking sun damage: daytime, sky-exposed,
     * not raining, not shielded by an umbrella (any item in the
     * {@code neoorigins:umbrellas} tag, or any Vampires Need Umbrellas item when
     * that mod is installed) or — when {@code helmet_protection} is on — by a
     * non-{@code sun_permeable} helmet. Shared by the
     * {@code exposed_to_sun} condition and the {@code entity_group}
     * {@code burns_in_sunlight} behaviour so both honour the identical rules
     * (including the helmet-durability wear side effect). NB: evaluating this
     * may damage a worn helmet, so call it on the same ~1s cadence as the
     * passive condition, not every tick.
     */
    public static boolean isExposedToSun(ServerPlayer p) {
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
            // Umbrella protection — an umbrella held in either hand or worn in
            // a Curios/Accessories slot blocks sun damage entirely, and takes
            // priority over helmet protection (so it costs no helmet durability).
            if (neoorigins$isHoldingUmbrella(p)) return false;
            // Helmet protection — any helmet blocks sun damage, but only
            // when the helmet_protection flag is enabled. When the flag is
            // false the whole block is skipped: a worn helmet neither
            // protects nor takes durability damage, and we fall through to
            // return true (player burns).
            // Damageable helmets take durability damage over time;
            // invulnerable/unbreakable helmets (e.g. allthemodium)
            // protect indefinitely. Helmets in the neoorigins:sun_permeable
            // tag (open/mesh, e.g. chainmail) are treated as no helmet at
            // all — they neither shade the player nor take durability damage.
            if (GameplayConfig.sunHelmetProtection()) {
                ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
                if (!head.isEmpty() && !head.is(SUN_PERMEABLE_HELMETS)) {
                    if (head.isDamageableItem()) {
                        float chance = GameplayConfig.sunHelmetDuraDamageChance();
                        if (chance > 0f && p.getRandom().nextFloat() < chance) {
                            head.hurtAndBreak(1, p, EquipmentSlot.HEAD);
                        }
                    }
                    return false;
                }
            }
            return true;
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
            // Mana-backed resources read live from the Iron's pool (authoritative)
            // rather than the internal store — checked at test-time because the
            // backing registration happens at grant, after parse.
            if (com.cyberday1.neoorigins.compat.CompatAttachments.isManaBacked(powerId)) {
                var meta = com.cyberday1.neoorigins.compat.CompatAttachments.getResourceMeta(powerId);
                int manaCur = com.cyberday1.neoorigins.compat.ResourceBackingRouter.read(
                    player, powerId, meta != null ? meta.min() : 0);
                return comparison.test(manaCur, target);
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
        // Apoli's power_active is NOT toggle-only: for any other granted power it
        // returns power.isActive() = granted && condition satisfied, so a
        // non-toggle power id must fall through to the granted+condition check
        // (previously always false, which broke e.g. Origins++ hold_conditions
        // referencing the climbing sub-power itself).
        return player -> {
            String resolved = com.cyberday1.neoorigins.compat.CompatAttachments.resolveLegacySyntheticId(powerId);
            Identifier rid;
            try {
                rid = Identifier.parse(resolved);
            } catch (Exception e) {
                return false;
            }
            var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(rid);
            boolean toggleLike = holder != null
                && (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower
                    || holder.type() instanceof com.cyberday1.neoorigins.power.builtin.TogglePower);
            if (toggleLike
                    || player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.toggleState())
                        .getStates().containsKey(resolved)) {
                return com.cyberday1.neoorigins.compat.Toggles.isOn(player, resolved);
            }
            if (holder == null || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return false;
            // Guard against condition cycles (power A's condition referencing
            // power_active(A)): a re-entered id fails closed for this evaluation.
            java.util.Set<String> inFlight = POWER_ACTIVE_EVAL.get();
            if (!inFlight.add(resolved)) return false;
            try {
                for (var granted : com.cyberday1.neoorigins.service.ActiveOriginService.allPowers(sp)) {
                    if (granted.id().equals(rid)) {
                        return granted.isConditionSatisfied(sp);
                    }
                }
                return false;
            } finally {
                inFlight.remove(resolved);
            }
        };
    }

    /** Re-entrancy guard for {@link #parsePowerActive}'s granted+condition path. */
    private static final ThreadLocal<java.util.Set<String>> POWER_ACTIVE_EVAL =
        ThreadLocal.withInitial(java.util.HashSet::new);

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
            JsonArray conditions =
                com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(blockCond, "conditions");
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
        // Anything the arms above do not recognise — block_state, height,
        // adjacent, offset, fluid — is handed to the shared block-condition
        // compiler, evaluated against the block below. The arms above are left
        // alone on purpose: they are what every authoring in the pack corpus
        // actually uses, and rerouting a working path buys nothing.
        BlockPosCondition pred = compileInBlockPredicate(blockCond, contextId);
        // Dropping the filter and passing through as bare onGround() was the old
        // behaviour, and it is fail-OPEN: the power fires while standing on
        // anything at all, which is not what "on this block" was asked for. The
        // compiler already reports the unreadable verb, so fail closed here.
        if (pred == null) return EntityCondition.alwaysFalse();
        return player -> player != null && player.onGround()
            && pred.test(player.level(), player.blockPosition().below());
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
        // Nested sub-condition form: {"type":"origins:biome","condition":{...}}.
        if (json.has("condition") && json.get("condition").isJsonObject()) {
            return parseBiomeSubCondition(json.getAsJsonObject("condition"));
        }
        return EntityCondition.alwaysTrue();
    }

    /**
     * The nested biome-condition grammar Apoli allows under
     * {@code origins:biome}'s {@code condition} field. Distinct from the
     * entity-condition grammar: {@code in_tag} here means "the biome is in this
     * biome tag", not "the entity is in this entity-type tag", and
     * {@code temperature}/{@code precipitation}/{@code high_humidity} read the
     * biome's climate rather than anything about the player.
     *
     * <p>Everything except the combinators reads the biome at the player's
     * current block position, which is what Apoli does: a biome condition that
     * cached the biome at power-grant time would go stale the moment the player
     * walked over a border.
     *
     * <p>Unrecognised sub-types still fail CLOSED. Failing open here would make
     * a biome-gated power fire in every biome, which is a far louder bug than a
     * power that never fires — and, unlike the entity-condition parser, this
     * grammar is small enough that an unknown verb really is a gap rather than
     * a niche we chose not to cover.
     */
    static EntityCondition parseBiomeSubCondition(JsonObject sub) {
        if (sub == null) return EntityCondition.alwaysFalse();
        String subType = sub.has("type") ? sub.get("type").getAsString() : "";
        String bare = subType.contains(":") ? subType.substring(subType.indexOf(':') + 1) : subType;
        boolean inverted = sub.has("inverted") && sub.get("inverted").getAsBoolean();

        EntityCondition inner;
        switch (bare) {
            case "and", "all_of" -> {
                List<EntityCondition> parts = parseBiomeSubList(sub);
                inner = player -> { for (EntityCondition c : parts) if (!c.test(player)) return false; return true; };
            }
            case "or", "any_of" -> {
                List<EntityCondition> parts = parseBiomeSubList(sub);
                inner = player -> { for (EntityCondition c : parts) if (c.test(player)) return true; return false; };
            }
            case "not" -> {
                EntityCondition negated = sub.has("condition") && sub.get("condition").isJsonObject()
                    ? parseBiomeSubCondition(sub.getAsJsonObject("condition"))
                    : EntityCondition.alwaysFalse();
                inner = player -> !negated.test(player);
            }
            case "constant" -> {
                boolean value = sub.has("value") && sub.get("value").getAsBoolean();
                inner = value ? EntityCondition.alwaysTrue() : EntityCondition.alwaysFalse();
            }
            case "biome" -> {
                // A nested exact-id / tag biome condition, e.g. an or-list whose
                // branches are themselves {"type":"origins:biome","biome":...}.
                inner = parseBiome(sub);
            }
            case "in_tag" -> {
                String subTag = sub.has("tag") ? sub.get("tag").getAsString() : null;
                if (subTag == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] biome sub-condition 'in_tag' has no 'tag' field — failing closed");
                    return EntityCondition.alwaysFalse();
                }
                TagKey<Biome> subBiomeTag = TagKey.create(Registries.BIOME, Identifier.parse(subTag));
                inner = player -> player.level().getBiome(player.blockPosition()).is(subBiomeTag);
            }
            case "temperature" -> {
                String comp = sub.has("comparison") ? sub.get("comparison").getAsString() : ">=";
                double target = sub.has("compare_to") ? sub.get("compare_to").getAsDouble() : 0.0;
                ComparisonType comparison = ComparisonType.fromString(comp);
                inner = player -> {
                    float temp = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
                    return comparison.test(temp, target);
                };
            }
            case "precipitation" -> {
                Biome.Precipitation want = parsePrecipitation(sub);
                if (want == null) return EntityCondition.alwaysFalse();
                inner = player -> {
                    BlockPos pos = player.blockPosition();
                    // Position-aware, not biome-wide: the same biome rains at sea
                    // level and snows on the peaks, and Apoli resolves it per-block.
                    // 26.1 takes the sea level explicitly, where 1.21.1 read it off
                    // a hardcoded 63 internally; pass the level's own so a datapack
                    // dimension with a raised sea level still answers correctly.
                    return player.level().getBiome(pos).value()
                        .getPrecipitationAt(pos, player.level().getSeaLevel()) == want;
                };
            }
            case "high_humidity" -> {
                // Apoli's threshold, kept verbatim so packs tuned against it agree.
                // getModifiedClimateSettings() is NeoForge's public accessor for the
                // otherwise-private climate record; it also honours biome modifiers,
                // which the raw constructor value would not.
                inner = player -> player.level().getBiome(player.blockPosition())
                    .value().getModifiedClimateSettings().downfall() > 0.85f;
            }
            default -> {
                NeoOrigins.LOGGER.warn("[CompatB] biome condition has unsupported sub-condition type '{}' — " +
                    "failing closed (power will not activate). Pack authors should use biome tags instead.", subType);
                return EntityCondition.alwaysFalse();
            }
        }
        return inverted ? player -> !inner.test(player) : inner;
    }

    private static List<EntityCondition> parseBiomeSubList(JsonObject sub) {
        // asArray, not getAsJsonArray: a single-child combinator is routinely
        // authored as a bare object in legacy packs.
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(sub, "conditions");
        List<EntityCondition> parts = new ArrayList<>();
        for (JsonElement el : arr) if (el.isJsonObject()) parts.add(parseBiomeSubCondition(el.getAsJsonObject()));
        return parts;
    }

    /** {@code "none" | "rain" | "snow"} — Apoli's spelling of {@link Biome.Precipitation}. */
    static Biome.Precipitation parsePrecipitation(JsonObject sub) {
        String raw = sub.has("precipitation") ? sub.get("precipitation").getAsString() : null;
        if (raw == null) {
            NeoOrigins.LOGGER.warn("[CompatB] biome sub-condition 'precipitation' has no 'precipitation' field — failing closed");
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "none" -> Biome.Precipitation.NONE;
            case "rain" -> Biome.Precipitation.RAIN;
            case "snow" -> Biome.Precipitation.SNOW;
            default -> {
                NeoOrigins.LOGGER.warn("[CompatB] biome sub-condition 'precipitation' has unknown value '{}' " +
                    "(expected none/rain/snow) — failing closed", raw);
                yield null;
            }
        };
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

        // Check for item_condition sub-object
        JsonObject itemCond = json.has("item_condition") ? json.getAsJsonObject("item_condition") : null;

        // Accessory branch, intercepted BEFORE mapEquipmentSlot: that maps any
        // unrecognised slot name to MAINHAND, so "accessory" used to be answered
        // with whatever the player was holding. Equipped accessory stacks come
        // from the shared, soft-dep AccessoryInspector; the optional slot_type
        // narrows to one named curio slot (ring, belt, hands, ...).
        if ("accessory".equalsIgnoreCase(slot)) {
            String slotType = json.has("slot_type") && !json.get("slot_type").isJsonNull()
                ? json.get("slot_type").getAsString() : null;
            if (itemCond == null) {
                // Slot-presence check: any accessory equipped, optionally in the
                // named slot type. Deliberately not the alwaysTrue() the vanilla
                // branch below falls back to — a condition that cannot fail is no
                // condition, and there is no pack behaviour to preserve here.
                return player -> !AccessoryInspector.getEquippedAccessories(player, slotType).isEmpty();
            }
            ItemCondition accPredicate = ItemConditionParser.parse(itemCond);
            return player -> {
                for (ItemStack stack : AccessoryInspector.getEquippedAccessories(player, slotType)) {
                    if (accPredicate.test(stack)) return true;
                }
                return false;
            };
        }

        EquipmentSlot eqSlot = mapEquipmentSlot(slot);
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

    /**
     * {@code neoorigins:block} — the block at the player's own position. This is
     * the same test {@code in_block} performs, so it shares that compiler rather
     * than keeping a second, narrower copy: the hand-rolled version understood
     * only {@code block}/{@code id}/{@code tag} and returned always-true for
     * everything else, silently, while its {@code FieldSpec} advertised the whole
     * {@code block_condition.schema.json}. An editor could therefore author an
     * {@code and} / {@code height} / {@code adjacent} node that the runtime ignored.
     *
     * <p>The one behaviour deliberately preserved is the bare wrapper: a node with
     * no discriminating field (and no type beyond {@code block} itself) still means
     * "any block", which is what the field docs promise and what packs rely on.
     */
    static EntityCondition parseBlockCondition(JsonObject json, String contextId) {
        JsonObject blockCond = json.has("block_condition") && json.get("block_condition").isJsonObject()
            ? json.getAsJsonObject("block_condition") : json;

        String type = blockCond.has("type") ? blockCond.get("type").getAsString() : "";
        String bare = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        boolean hasLeaf = blockCond.has("block") || blockCond.has("id") || blockCond.has("tag");
        if (!hasLeaf && (bare.isEmpty() || bare.equals("block"))) return EntityCondition.alwaysTrue();

        BlockPosCondition pred = compileInBlockPredicate(blockCond, contextId);
        if (pred == null) return EntityCondition.alwaysFalse();
        return player -> pred.test(player.level(), player.blockPosition());
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
     * packs (the Seer origin) gate nearly every power on.
     *
     * <p>The entity NBT is read via {@code saveWithoutId} bridged through
     * {@code TagValueOutput} (26.2 entity save/load flows through ValueOutput/
     * ValueInput rather than raw CompoundTag). An unparseable SNBT string fails
     * closed (never matches) with a one-shot warning.
     */
    static EntityCondition parseNbt(JsonObject json) {
        String snbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (snbt == null || snbt.isBlank()) return EntityCondition.alwaysTrue();
        final CompoundTag expected;
        try {
            // 26.2: TagParser.parseTag is gone; use parseCompoundFully.
            expected = TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] origins:nbt: could not parse SNBT '{}' ({}); condition will never match",
                snbt, e.getMessage());
            return EntityCondition.alwaysFalse();
        }
        // An empty expectation ({}) is trivially contained — match everything,
        // mirroring Apoli (an empty nbt block is a no-op gate).
        if (expected.isEmpty()) return EntityCondition.alwaysTrue();
        return player -> {
            var provider = player.level().registryAccess();
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, provider);
            player.saveWithoutId(out);
            CompoundTag actual = out.buildResult();
            return neoorigins$nbtContains(actual, expected);
        };
    }

    /**
     * Recursive structural-containment match: returns true when {@code actual}
     * contains every key/value declared in {@code expected}. For list tags,
     * every element in the expected list must appear somewhere in the actual list
     * (required for Tags:[\"x\"] partial-list matching, mirroring Apoli behaviour).
     */
    private static boolean neoorigins$nbtContains(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.keySet()) {
            if (!actual.contains(key)) return false;
            Tag exp = expected.get(key);
            Tag act = actual.get(key);
            if (exp instanceof CompoundTag expCt && act instanceof CompoundTag actCt) {
                if (!neoorigins$nbtContains(actCt, expCt)) return false;
            } else if (exp instanceof net.minecraft.nbt.ListTag expList
                    && act instanceof net.minecraft.nbt.ListTag actList) {
                // Each element in expected must be present in actual list.
                for (Tag expElem : expList) {
                    boolean found = false;
                    for (Tag actElem : actList) {
                        if (expElem.equals(actElem)) { found = true; break; }
                    }
                    if (!found) return false;
                }
            } else if (exp != null && !exp.equals(act)) {
                return false;
            }
        }
        return true;
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

    // ── statistic ────────────────────────────────────────────────────────
    //
    // Apoli's statistic condition compares a player's vanilla statistic against
    // a threshold. Two shapes are in the wild and both are accepted:
    //
    //   canonical (Apoli):  "stat": { "type": "minecraft:custom",
    //                                 "stat": "minecraft:time_since_rest" }
    //   legacy flat string: "statistic": "minecraft:time_since_rest"
    //
    // The flat form implies the `minecraft:custom` category, which is what every
    // pack using it means (time_since_rest, play_time, walk_one_cm, …). The other
    // vanilla categories — mined, crafted, used, broken, picked_up, dropped,
    // killed, killed_by — work through the nested form, as do modded stat types
    // (the category is resolved against the live stat-type registry).

    /**
     * The vanilla stat-type categories. Used only to reject a typo in the
     * {@code minecraft} namespace at parse time (so the pack author gets a
     * warning rather than a silently dead gate); a {@code modid:} category is
     * let through and resolved against the live stat-type registry instead.
     */
    private static final java.util.Set<String> VANILLA_STAT_CATEGORIES = java.util.Set.of(
        "minecraft:custom", "minecraft:mined", "minecraft:crafted", "minecraft:used",
        "minecraft:broken", "minecraft:picked_up", "minecraft:dropped",
        "minecraft:killed", "minecraft:killed_by");

    /**
     * A stat reference split into its category (stat-type id) and the id within
     * that category — the shape-normalised result of reading a {@code statistic}
     * condition's {@code statistic}/{@code stat} field.
     */
    record StatRef(Identifier typeId, Identifier statId) {}

    /**
     * Read the stat reference out of a {@code statistic} condition, accepting the
     * nested-object and flat-string shapes alike. Returns {@code null} — so the
     * caller fails closed — when the field is missing, either id is not a valid
     * resource location, or the category is a {@code minecraft:} one that does
     * not exist. Pure: touches no registry, so it is safe before Minecraft
     * bootstrap; the ids are resolved lazily on first evaluation.
     */
    static StatRef readStatRef(JsonObject json) {
        JsonElement raw = json.has("statistic") ? json.get("statistic")
                        : json.has("stat") ? json.get("stat")
                        : null;
        if (raw == null) return null;

        String typeId = "minecraft:custom";
        String statId;
        if (raw.isJsonObject()) {
            JsonObject inner = raw.getAsJsonObject();
            if (inner.has("type") && inner.get("type").isJsonPrimitive()) {
                typeId = inner.get("type").getAsString();
            }
            JsonElement id = inner.has("stat") ? inner.get("stat")
                           : inner.has("statistic") ? inner.get("statistic")
                           : null;
            if (id == null || !id.isJsonPrimitive()) return null;
            statId = id.getAsString();
        } else if (raw.isJsonPrimitive()) {
            statId = raw.getAsString();
        } else {
            return null;
        }

        if (typeId.indexOf(':') < 0) typeId = "minecraft:" + typeId;
        if (statId.indexOf(':') < 0) statId = "minecraft:" + statId;
        if (typeId.startsWith("minecraft:") && !VANILLA_STAT_CATEGORIES.contains(typeId)) return null;
        Identifier parsedType = Identifier.tryParse(typeId);
        Identifier parsedStat = Identifier.tryParse(statId);
        if (parsedType == null || parsedStat == null) return null;
        return new StatRef(parsedType, parsedStat);
    }

    /** Resolve the registry entry behind a stat id, or null when it is unknown. */
    private static <T> net.minecraft.stats.Stat<T> resolveStat(
            net.minecraft.stats.StatType<T> type, Identifier id) {
        T value = type.getRegistry().getOptional(id).orElse(null);
        return value == null ? null : type.get(value);
    }

    static EntityCondition parseStatistic(JsonObject json, String contextId) {
        StatRef ref = readStatRef(json);
        if (ref == null) return EntityCondition.alwaysFalse();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        // Resolution is deferred to first evaluation: the stat registries are not
        // populated at pack-parse time, and modded custom stats register later
        // still. The resolved Stat is memoised; an id that never resolves fails
        // closed once, loudly, instead of warning every tick.
        return new EntityCondition() {
            private net.minecraft.stats.Stat<?> resolved;
            private boolean unresolvable;

            @Override
            public boolean test(ServerPlayer player) {
                if (unresolvable) return false;
                if (resolved == null) {
                    net.minecraft.stats.StatType<?> type =
                        BuiltInRegistries.STAT_TYPE.getOptional(ref.typeId()).orElse(null);
                    resolved = type == null ? null : resolveStat(type, ref.statId());
                    if (resolved == null) {
                        unresolvable = true;
                        NeoOrigins.LOGGER.warn(
                            "[Compat] statistic condition in '{}' references unknown stat {} of type {} — condition is false.",
                            contextId, ref.statId(), ref.typeId());
                        return false;
                    }
                }
                return comparison.test(player.getStats().getValue(resolved), target);
            }
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
        //
        // An ABSENT block_condition still means "any block" (always true) — that
        // is authored intent. An UNCOMPILABLE one does not: the compiler reports
        // match-none by returning null, and honouring that is the whole point of
        // the fail-closed policy. Returning always-true here used to invert it,
        // so an unknown type fired the power unconditionally — and because
        // `inverted` is only applied to a non-null base, the negated twin of the
        // same node fired too, leaving both halves of a wet/dry pair permanently on.
        JsonObject blockCond = json.has("block_condition") && json.get("block_condition").isJsonObject()
            ? json.getAsJsonObject("block_condition") : null;
        if (blockCond == null) return EntityCondition.alwaysTrue();
        BlockPosCondition pred = compileInBlockPredicate(blockCond, contextId);
        if (pred == null) return EntityCondition.alwaysFalse();
        return player -> pred.test(player.level(), player.blockPosition());
    }

    /**
     * origins:in_block_anywhere — Apoli semantics: count every block position the
     * entity's bounding box overlaps whose state matches the nested
     * block_condition, then compare via comparison/compare_to (defaults
     * {@code >=} / 1). Distinct from in_block, which only samples the single
     * block at the entity's feet — a crouching spider brushing a cobweb with its
     * hitbox edge must still count as "in" it.
     */
    static EntityCondition parseInBlockAnywhere(JsonObject json, String contextId) {
        JsonObject blockCond = json.has("block_condition") && json.get("block_condition").isJsonObject()
            ? json.getAsJsonObject("block_condition") : null;
        if (blockCond == null) return EntityCondition.alwaysTrue();
        BlockPosCondition pred = compileInBlockPredicate(blockCond, contextId);
        // Fail closed, not "count zero". Substituting a zero count would still
        // satisfy an at-most comparison (`<= 2` on an unknown type reads true),
        // which is the same fail-open trap in a different shape.
        if (pred == null) return EntityCondition.alwaysFalse();

        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType comparison = ComparisonType.fromString(comp);
        long stopAt = switch (comparison) {
            case GREATER_THAN_OR_EQUAL, LESS_THAN -> Math.max(0, target);
            case GREATER_THAN, LESS_THAN_OR_EQUAL, EQUAL, NOT_EQUAL -> Math.max(0, (long) target + 1);
        };

        return player -> {
            var box = player.getBoundingBox();
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            long count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (pred.test(player.level(), pos)) {
                    count++;
                    if (count >= stopAt) break;
                }
            }
            return comparison.test(count, target);
        };
    }

    /**
     * neoorigins:climbing_gate — internal condition emitted by the compat
     * translator for conditioned {@code origins:climbing} powers. Carries
     * Apoli's climb state machine: active while {@code condition} holds; once
     * active, {@code allow_holding} (default true) keeps it active while
     * airborne and {@code hold_condition} passes, releasing on touchdown.
     *
     * <p>The result is memoized per player per tick, which both keeps every
     * same-tick evaluation (capability check, client sync, signature) coherent
     * and breaks the self-reference cycle packs use — a hold_condition of
     * {@code power_active(<this climbing power>)} re-enters this gate during
     * its own computation and receives last tick's result ("was I climbing?"),
     * which is exactly Apoli's hold semantic.
     */
    static EntityCondition parseClimbingGate(JsonObject json, String contextId) {
        EntityCondition active = json.has("condition") && json.get("condition").isJsonObject()
            ? parse(json.getAsJsonObject("condition"), contextId + "#climbing_gate")
            : EntityCondition.alwaysTrue();
        EntityCondition hold = json.has("hold_condition") && json.get("hold_condition").isJsonObject()
            ? parse(json.getAsJsonObject("hold_condition"), contextId + "#climbing_gate_hold")
            : null;
        boolean allowHolding = !json.has("allow_holding") || json.get("allow_holding").getAsBoolean();

        // Per-parsed-gate per-player state; the map dies with the parsed power
        // on datapack reload, so no explicit cleanup is needed.
        java.util.Map<java.util.UUID, ClimbGateState> states = new java.util.concurrent.ConcurrentHashMap<>();
        return player -> {
            ClimbGateState st = states.computeIfAbsent(player.getUUID(), k -> new ClimbGateState());
            long now = player.level().getGameTime();
            if (st.computing) return st.lastResult;       // self-reference via power_active
            if (st.lastTick == now) return st.lastResult; // memoized within the tick
            boolean result;
            st.computing = true;
            try {
                if (active.test(player)) {
                    result = true;
                } else {
                    result = allowHolding && st.lastResult && !player.onGround()
                        && (hold == null || hold.test(player));
                }
            } finally {
                st.computing = false;
            }
            st.lastTick = now;
            st.lastResult = result;
            return result;
        };
    }

    private static final class ClimbGateState {
        long lastTick = Long.MIN_VALUE;
        boolean lastResult;
        boolean computing;
    }

    /**
     * A block condition evaluated at a position rather than against a bare
     * {@link BlockState}. Apoli's {@code height}, {@code adjacent} and
     * {@code offset} leaves read the world <em>around</em> the block, which a
     * position-blind {@code Predicate<BlockState>} cannot express — every
     * caller of the compiler below already has the position in hand, so the
     * whole chain is positional.
     */
    @FunctionalInterface
    public interface BlockPosCondition {
        boolean test(net.minecraft.world.level.BlockGetter level, BlockPos pos);
    }

    /** The six face neighbours Apoli's {@code adjacent} scans. */
    private static final net.minecraft.core.Direction[] FACES = net.minecraft.core.Direction.values();

    /**
     * Recursively compiles an {@code origins:in_block} block_condition node into
     * a {@link BlockPosCondition}. Self-contained on purpose — kept separate
     * from the {@code action_on_event} block-predicate compiler. Returns
     * {@code null} for an unrecognised leaf so callers can fall back to
     * always-true. Honours a per-node {@code inverted} flag.
     */
    static BlockPosCondition compileInBlockPredicate(JsonObject bc, String contextId) {
        boolean inverted = bc.has("inverted") && bc.get("inverted").getAsBoolean();
        BlockPosCondition base = compileInBlockLeaf(bc, contextId);
        if (base == null) return null;
        return inverted ? (level, pos) -> !base.test(level, pos) : base;
    }

    private static BlockPosCondition compileInBlockLeaf(JsonObject bc, String contextId) {
        String type = bc.has("type") ? bc.get("type").getAsString() : "";
        String bare = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;

        String blockId = bc.has("block") ? bc.get("block").getAsString()
                       : bc.has("id") ? bc.get("id").getAsString() : null;
        if ((bare.equals("block") || blockId != null) && blockId != null && !blockId.isBlank()) {
            Identifier bid = Identifier.parse(
                com.cyberday1.neoorigins.compat.LegacyBlockIds.remap(blockId));
            return (level, pos) -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(bid);
        }
        if (bare.equals("in_tag") && bc.has("tag")) {
            TagKey<Block> tag = parseBlockTag(bc.get("tag").getAsString());
            return (level, pos) -> level.getBlockState(pos).is(tag);
        }
        // fluid — test the FLUID occupying the position rather than the block.
        // Waterlogged blocks are the reason this is not the same as a `block`
        // match: a waterlogged slab is `minecraft:oak_slab` as a block and
        // `minecraft:water` as a fluid, and a "am I wet" power means the latter.
        if (bare.equals("fluid")) {
            JsonObject fc = bc.has("fluid_condition") && bc.get("fluid_condition").isJsonObject()
                ? bc.getAsJsonObject("fluid_condition") : null;
            if (fc == null) return null;
            java.util.function.Predicate<FluidState> fluidPred = compileFluidPredicate(fc, contextId);
            if (fluidPred == null) return null;
            return (level, pos) -> fluidPred.test(level.getFluidState(pos));
        }
        // light_level / exposed_to_sky need the light engine, which a bare
        // BlockGetter does not carry — every real caller hands us the entity's
        // Level, so narrow to it and match nothing if some future caller does
        // not. Silently answering true would put the fail-open bug straight back.
        if (bare.equals("light_level")) {
            ComparisonType comparison = ComparisonType.fromString(
                bc.has("comparison") ? bc.get("comparison").getAsString() : ">=");
            int target = bc.has("compare_to") ? bc.get("compare_to").getAsInt() : 0;
            String lightType = bc.has("light_type") ? bc.get("light_type").getAsString() : "";
            return (level, pos) -> {
                if (!(level instanceof Level l)) return false;
                int light = switch (lightType) {
                    case "sky"   -> l.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
                    case "block" -> l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
                    default      -> l.getMaxLocalRawBrightness(pos);
                };
                return comparison.test(light, target);
            };
        }
        if (bare.equals("exposed_to_sky")) {
            return (level, pos) -> level instanceof Level l && l.canSeeSky(pos);
        }
        // movement_blocking — "is this block solid enough to stand in the way".
        // Not the same as "not air": a torch and tall grass are both non-air and
        // both non-blocking, which is exactly the distinction Giant's slam wants.
        if (bare.equals("movement_blocking")) {
            return (level, pos) -> level.getBlockState(pos).blocksMotion();
        }
        if (bare.equals("and") || bare.equals("all_of") || bare.equals("or") || bare.equals("any_of")) {
            boolean isAnd = bare.equals("and") || bare.equals("all_of");
            JsonArray conditions = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(bc, "conditions");
            List<BlockPosCondition> subs = new ArrayList<>();
            for (JsonElement el : conditions) {
                if (!el.isJsonObject()) continue;
                var sub = compileInBlockPredicate(el.getAsJsonObject(), contextId);
                // Propagate, don't drop. Skipping an uncompilable branch silently
                // rewrites the author's condition: an `and` loses a clause and
                // gets BROADER, which is the fail-open direction. Treat the whole
                // node as uncompilable instead and let the caller fail closed.
                if (sub == null) return null;
                subs.add(sub);
            }
            return (level, pos) -> {
                for (var s : subs) {
                    boolean r = s.test(level, pos);
                    if (isAnd && !r) return false;
                    if (!isAnd && r) return true;
                }
                return isAnd;
            };
        }
        // offset — evaluate the nested condition at pos + (x, y, z). Already
        // supported by the action_on_event compiler; now that this one is
        // positional too it can honour the same structural wrapper.
        if (bare.equals("offset")) {
            int ox = bc.has("x") ? bc.get("x").getAsInt() : 0;
            int oy = bc.has("y") ? bc.get("y").getAsInt() : 0;
            int oz = bc.has("z") ? bc.get("z").getAsInt() : 0;
            JsonObject nested = bc.has("condition") && bc.get("condition").isJsonObject()
                ? bc.getAsJsonObject("condition") : null;
            if (nested == null) return null;
            BlockPosCondition sub = compileInBlockPredicate(nested, contextId);
            if (sub == null) return null;
            return (level, pos) -> sub.test(level, pos.offset(ox, oy, oz));
        }
        // block_state — match a single blockstate property. Origins++ gates its
        // Kelperet swim penalty on `waterlogged: true`, which used to drop out
        // here silently and leave the whole in_block matching nothing.
        if (bare.equals("block_state")) {
            var statePred = compileBlockStateProperty(bc);
            if (statePred == null) return null;
            return (level, pos) -> statePred.test(level.getBlockState(pos));
        }
        // height — the block's own Y, not the entity's. Fairytale's height
        // affinity checks "block_in_radius { height <= 63 }" to mean sea level.
        if (bare.equals("height")) {
            ComparisonType comparison = ComparisonType.fromString(
                bc.has("comparison") ? bc.get("comparison").getAsString() : ">=");
            double target = bc.has("compare_to") ? bc.get("compare_to").getAsDouble() : 0.0;
            return (level, pos) -> comparison.test(pos.getY(), target);
        }
        // adjacent — count the face neighbours matching `adjacent_condition`
        // and compare (default >= 1). Origins++ Glacier refuses to sleep unless
        // at most two neighbours are snow or ice.
        if (bare.equals("adjacent")) {
            JsonObject inner = bc.has("adjacent_condition") && bc.get("adjacent_condition").isJsonObject()
                ? bc.getAsJsonObject("adjacent_condition") : null;
            if (inner == null) return null;
            BlockPosCondition sub = compileInBlockPredicate(inner, contextId);
            if (sub == null) return null;
            ComparisonType comparison = ComparisonType.fromString(
                bc.has("comparison") ? bc.get("comparison").getAsString() : ">=");
            double target = bc.has("compare_to") ? bc.get("compare_to").getAsDouble() : 1.0;
            return (level, pos) -> {
                int count = 0;
                for (var face : FACES) {
                    if (sub.test(level, pos.relative(face))) count++;
                }
                return comparison.test(count, target);
            };
        }
        // Route through the collector, not a bare debug line. This used to log at
        // DEBUG only, with no WARN-level counterpart, so a headless pack gate that
        // reads the `[CompatB] Compatibility summary` block could report a clean
        // run while an unsupported block condition was live in the pack.
        com.cyberday1.neoorigins.compat.CompatWarningCollector.recordUnsupportedCondition(
            type.isEmpty() ? "<no type>" : type, contextId,
            "unsupported block_condition type — block condition matches nothing");
        return null;
    }

    /**
     * Compiles the {@code fluid_condition} sub-grammar carried by a {@code fluid}
     * block condition into a {@link FluidState} predicate.
     *
     * <p>Only {@code in_tag} is attested in the pack corpus (Mycelium Construct's
     * {@code hal:wet} gates on {@code minecraft:water}); the id / {@code empty} /
     * {@code still} leaves and the boolean combinators are the unambiguous
     * siblings, added so the common authorings do not each become a new gap. The
     * upstream Apoli verb list was NOT verified against the jar, so anything not
     * handled here fails closed and is reported rather than guessed at.
     *
     * @return the predicate, or {@code null} if the node is not compilable — the
     *         caller must treat that as match-none, never as match-all.
     */
    private static java.util.function.Predicate<FluidState> compileFluidPredicate(
            JsonObject fc, String contextId) {
        boolean inverted = fc.has("inverted") && fc.get("inverted").getAsBoolean();
        java.util.function.Predicate<FluidState> base = compileFluidLeaf(fc, contextId);
        if (base == null) return null;
        return inverted ? base.negate() : base;
    }

    private static java.util.function.Predicate<FluidState> compileFluidLeaf(
            JsonObject fc, String contextId) {
        String type = fc.has("type") ? fc.get("type").getAsString() : "";
        String bare = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;

        if (bare.equals("in_tag") && fc.has("tag")) {
            String raw = fc.get("tag").getAsString();
            if (raw.startsWith("#")) raw = raw.substring(1);
            TagKey<net.minecraft.world.level.material.Fluid> tag =
                TagKey.create(Registries.FLUID, Identifier.parse(raw));
            return state -> state.is(tag);
        }
        String fluidId = fc.has("fluid") ? fc.get("fluid").getAsString()
                       : fc.has("id") ? fc.get("id").getAsString() : null;
        if ((bare.equals("fluid") || fluidId != null) && fluidId != null && !fluidId.isBlank()) {
            Identifier fid = Identifier.parse(fluidId);
            return state -> BuiltInRegistries.FLUID.getKey(state.getType()).equals(fid);
        }
        if (bare.equals("empty")) return FluidState::isEmpty;
        if (bare.equals("still")) return FluidState::isSource;
        if (bare.equals("constant")) {
            boolean value = fc.has("value") && fc.get("value").getAsBoolean();
            return state -> value;
        }
        if (bare.equals("and") || bare.equals("all_of") || bare.equals("or") || bare.equals("any_of")) {
            boolean isAnd = bare.equals("and") || bare.equals("all_of");
            JsonArray conditions = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(fc, "conditions");
            List<java.util.function.Predicate<FluidState>> subs = new ArrayList<>();
            for (JsonElement el : conditions) {
                if (!el.isJsonObject()) continue;
                var sub = compileFluidPredicate(el.getAsJsonObject(), contextId);
                if (sub == null) return null;
                subs.add(sub);
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
        com.cyberday1.neoorigins.compat.CompatWarningCollector.recordUnsupportedCondition(
            type.isEmpty() ? "<no type>" : type, contextId,
            "unsupported fluid_condition type — fluid condition matches nothing");
        return null;
    }

    /**
     * Compiles an Apoli {@code block_state} node into a {@link BlockState}
     * predicate. Public because
     * {@link com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader}'s
     * block-condition compiler needs the same matching without inheriting this
     * one's fail-closed policy — the two compilers deliberately fail in
     * opposite directions, so only the property lookup is shared.
     *
     * <p>Three authored shapes are honoured: {@code value} (Origins++ writes
     * {@code "property": "waterlogged", "value": true}), {@code enum} (a name or
     * an array of accepted names — Origins++ writes {@code "property": "facing",
     * "enum": "south"}), and {@code comparison}/{@code compare_to} for numeric
     * properties. A block that does not carry the property never matches, which
     * is Apoli's own behaviour.
     *
     * @return the predicate, or {@code null} if the node names no {@code property}.
     */
    public static java.util.function.Predicate<BlockState> compileBlockStateProperty(JsonObject bc) {
        if (bc == null || !bc.has("property")) return null;
        final String propName = bc.get("property").getAsString();

        final List<String> accepted = new ArrayList<>();
        for (String key : new String[] { "value", "enum" }) {
            if (!bc.has(key)) continue;
            JsonElement el = bc.get(key);
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) accepted.add(e.getAsString().toLowerCase(Locale.ROOT));
                }
            } else if (el.isJsonPrimitive()) {
                accepted.add(el.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        final ComparisonType comparison = bc.has("comparison")
            ? ComparisonType.fromString(bc.get("comparison").getAsString()) : null;
        final double compareTo = bc.has("compare_to") ? bc.get("compare_to").getAsDouble() : 0.0;
        final List<String> finalAccepted = List.copyOf(accepted);

        return state -> {
            var prop = state.getBlock().getStateDefinition().getProperty(propName);
            if (prop == null) return false;
            String name = blockPropertyValueName(state, prop);
            if (comparison != null) {
                try {
                    return comparison.test(Double.parseDouble(name), compareTo);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            if (finalAccepted.isEmpty()) return false;
            return finalAccepted.contains(name.toLowerCase(Locale.ROOT));
        };
    }

    /** Stringifies a blockstate property value ("north", "true", "3") without its type. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String blockPropertyValueName(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<?> prop) {
        net.minecraft.world.level.block.state.properties.Property raw = prop;
        return raw.getName(state.getValue(raw));
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
                // 26.x removed ServerLevel#getSharedSpawnPos(); the world spawn is
                // now reached via the level's RespawnData (see ActiveRecallPower /
                // SpawnHelper). overworld() resolves the reference dimension.
                BlockPos spawn = sl.getServer() != null
                    ? sl.getServer().overworld().getRespawnData().pos()
                    : sl.getRespawnData().pos();
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
     * near_block (alias {@code origins:block_in_radius}): counts matching blocks
     * within {@code radius} blocks (default 4, capped at 16) of the player and
     * compares the count via {@code comparison}/{@code compare_to} (Apoli
     * defaults {@code >=} / 1, i.e. "any match"). Accepts any combination of:
     * <ul>
     *   <li>{@code block} — single block ID</li>
     *   <li>{@code blocks} — list of block IDs</li>
     *   <li>{@code tag} — single block tag (with or without leading {@code #})</li>
     *   <li>{@code tags} — list of block tags</li>
     *   <li>{@code block_condition} — nested Apoli block condition (full
     *       block/in_tag/combinator/inverted support)</li>
     * </ul>
     * A block matches if it's in ANY of the provided matchers (logical OR).
     * {@code shape} may be {@code cube} (default), {@code star} or
     * {@code sphere}. The scan bails out early once the comparison outcome is
     * decided, so simple ">= 1" checks stay cheap.
     *
     * <pre>{ "type": "neoorigins:near_block", "block": "minecraft:lava", "radius": 3 }</pre>
     * <pre>{ "type": "origins:block_in_radius", "block_condition": { "type": "origins:block", "block": "minecraft:soul_sand" }, "radius": 9, "comparison": ">=", "compare_to": 5 }</pre>
     */
    static EntityCondition parseNearBlock(JsonObject json, String contextId) {
        // Apoli block_in_radius semantics: COUNT matching blocks in the shape and
        // compare against compare_to (defaults ">=" 1, which is the old any-match
        // behaviour). Capped at radius 16 to bound the per-tick scan.
        int radius = Math.min(16, Math.max(1,
            json.has("radius") ? json.get("radius").getAsInt() : 4));
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType comparison = ComparisonType.fromString(comp);
        String shape = json.has("shape") ? json.get("shape").getAsString().toLowerCase(Locale.ROOT) : "cube";
        if (!shape.equals("cube") && !shape.equals("star") && !shape.equals("sphere")) {
            NeoOrigins.LOGGER.debug("[CompatB] near_block {}: unknown shape '{}' — treating as cube", contextId, shape);
            shape = "cube";
        }

        List<Identifier> blockIds = new ArrayList<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        BlockPosCondition condPred = null;

        if (json.has("block")) {
            blockIds.add(Identifier.parse(
                com.cyberday1.neoorigins.compat.LegacyBlockIds.remap(json.get("block").getAsString())));
        }
        if (json.has("blocks") && json.get("blocks").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("blocks")) {
                if (el.isJsonPrimitive()) blockIds.add(Identifier.parse(
                    com.cyberday1.neoorigins.compat.LegacyBlockIds.remap(el.getAsString())));
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
        // Origins block_in_radius format: nested block_condition object. The
        // full recursive compiler handles block/in_tag/combinators/inverted.
        if (json.has("block_condition") && json.get("block_condition").isJsonObject()) {
            condPred = compileInBlockPredicate(json.getAsJsonObject("block_condition"), contextId);
        }

        if (blockIds.isEmpty() && tags.isEmpty() && condPred == null) {
            return failClosed("neoorigins:near_block",
                contextId, "requires 'block'/'blocks' or 'tag'/'tags' or 'block_condition'");
        }

        // Resolve block ids once; unknown ids simply never match.
        final List<Block> blocks = blockIds.stream()
            .map(BuiltInRegistries.BLOCK::getOptional)
            .flatMap(Optional::stream)
            .toList();
        final List<TagKey<Block>> finalTags = List.copyOf(tags);
        final BlockPosCondition finalCondPred = condPred;
        BlockPosCondition matcher = (level, pos) -> {
            BlockState state = level.getBlockState(pos);
            for (Block b : blocks) if (state.is(b)) return true;
            for (TagKey<Block> tag : finalTags) if (state.is(tag)) return true;
            return finalCondPred != null && finalCondPred.test(level, pos);
        };

        // Once the count reaches stopAt the comparison outcome can't change, so
        // the scan can bail out early (matters for high-radius tiered powers).
        final long stopAt = switch (comparison) {
            case GREATER_THAN_OR_EQUAL, LESS_THAN -> Math.max(0, target);
            case GREATER_THAN, LESS_THAN_OR_EQUAL, EQUAL, NOT_EQUAL -> Math.max(0, (long) target + 1);
        };

        final int r = radius;
        final String finalShape = shape;
        return p -> {
            BlockPos origin = p.blockPosition();
            Level level = p.level();
            long count = 0;
            outer:
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        switch (finalShape) {
                            case "star" -> { if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > r) continue; }
                            case "sphere" -> { if ((long) dx * dx + (long) dy * dy + (long) dz * dz > (long) r * r) continue; }
                            default -> { }
                        }
                        if (matcher.test(level, origin.offset(dx, dy, dz))) {
                            count++;
                            if (count >= stopAt) break outer;
                        }
                    }
                }
            }
            return comparison.test(count, target);
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
     * nearby_entities: count the entities around the player that match the
     * selectors, then compare that count (Apoli defaults {@code >=} 1). The
     * counting form of near_entity — Fairytale's wolf powers ask for "at least
     * one wolf within 10 blocks", but the same verb expresses "fewer than three
     * mobs nearby" just as naturally, which near_entity cannot.
     *
     * <p>Two authored shapes, both honoured:
     * <pre>{ "type": "origins:nearby_entities", "entity_type": "minecraft:wolf", "distance": 10, "comparison": ">=", "compare_to": 1 }</pre>
     * <pre>{ "type": "apoli:nearby_entities", "bientity_condition": { … }, "radius": 8 }</pre>
     *
     * <p>{@code entity_type} accepts an id or a {@code #tag}; {@code entity_types}
     * accepts a list of either. A {@code bientity_condition} is compiled through
     * {@link TargetConditionParser#parseBiEntity} so it can be evaluated against
     * a non-player entity; a condition that cannot be (a player-only verb, an
     * unknown verb) fails the whole condition closed rather than quietly
     * counting the wrong entities.
     *
     * <p>With no selector at all every entity in range counts, which is Apoli's
     * own behaviour for a bare {@code nearby_entities}.
     */
    static EntityCondition parseNearbyEntities(JsonObject json, String contextId) {
        double distance = Math.min(64.0, Math.max(1.0,
            json.has("radius") ? json.get("radius").getAsDouble()
          : json.has("distance") ? json.get("distance").getAsDouble() : 16.0));

        List<net.minecraft.world.entity.EntityType<?>> types = new ArrayList<>();
        List<TagKey<net.minecraft.world.entity.EntityType<?>>> typeTags = new ArrayList<>();
        List<String> rawTypes = new ArrayList<>();
        if (json.has("entity_type") && json.get("entity_type").isJsonPrimitive()) {
            rawTypes.add(json.get("entity_type").getAsString());
        }
        for (JsonElement el : com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "entity_types")) {
            if (el.isJsonPrimitive()) rawTypes.add(el.getAsString());
        }
        for (String raw : rawTypes) {
            if (raw == null || raw.isBlank()) continue;
            if (raw.startsWith("#")) {
                typeTags.add(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(raw.substring(1))));
            } else {
                var typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(raw));
                if (typeOpt.isEmpty()) {
                    return failClosed("neoorigins:nearby_entities", contextId,
                        "unknown entity type '" + raw + "'");
                }
                types.add(typeOpt.get());
            }
        }

        java.util.function.BiPredicate<ServerPlayer, net.minecraft.world.entity.Entity> biCond = null;
        if (json.has("bientity_condition") && json.get("bientity_condition").isJsonObject()) {
            biCond = TargetConditionParser.parseBiEntity(json.getAsJsonObject("bientity_condition"), contextId);
            if (biCond == null) {
                return failClosed("neoorigins:nearby_entities", contextId,
                    "bientity_condition uses a verb that cannot be evaluated against a non-player entity");
            }
        }

        ComparisonType comparison = ComparisonType.fromString(
            json.has("comparison") ? json.get("comparison").getAsString() : ">=");
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 1.0;

        final double dist = distance;
        final double distSq = distance * distance;
        final List<net.minecraft.world.entity.EntityType<?>> finalTypes = List.copyOf(types);
        final List<TagKey<net.minecraft.world.entity.EntityType<?>>> finalTags = List.copyOf(typeTags);
        final java.util.function.BiPredicate<ServerPlayer, net.minecraft.world.entity.Entity> finalBi = biCond;
        // Once the count passes this the comparison outcome is settled, so the
        // scan stops early — matters for a ">= 1" check in a crowded area.
        final long stopAt = switch (comparison) {
            case GREATER_THAN_OR_EQUAL, LESS_THAN -> (long) Math.max(0, Math.ceil(target));
            case GREATER_THAN, LESS_THAN_OR_EQUAL, EQUAL, NOT_EQUAL -> (long) Math.max(0, Math.floor(target) + 1);
        };

        return p -> {
            var aabb = p.getBoundingBox().inflate(dist);
            long count = 0;
            for (var entity : p.level().getEntities(p, aabb)) {
                if (entity.distanceToSqr(p) > distSq) continue;
                if (!finalTypes.isEmpty() || !finalTags.isEmpty()) {
                    boolean typeMatch = false;
                    for (var t : finalTypes) if (entity.getType() == t) { typeMatch = true; break; }
                    if (!typeMatch) {
                        for (var tag : finalTags) if (entity.getType().getTags().anyMatch(tk -> tk.equals(tag))) { typeMatch = true; break; }
                    }
                    if (!typeMatch) continue;
                }
                if (finalBi != null && !finalBi.test(p, entity)) continue;
                count++;
                if (count >= stopAt) break;
            }
            return comparison.test(count, target);
        };
    }

    /**
     * near_villager: true when a villager is within {@code distance} blocks.
     * Not an Apoli verb — Fairytale Origins invented it for its Village Hero
     * power, and a pack that uses it would otherwise fail closed and take the
     * whole power with it.
     *
     * <p>Counts {@link net.minecraft.world.entity.npc.villager.Villager} only:
     * wandering traders and zombie villagers are deliberately excluded, since
     * the intent every pack expresses with this verb is "near a settlement".
     * The optional {@code comparison}/{@code compare_to} pair compares the
     * villager count and defaults to {@code >=} 1.
     *
     * <pre>{ "type": "origins:near_villager", "distance": 32 }</pre>
     */
    static EntityCondition parseNearVillager(JsonObject json) {
        double distance = Math.min(64.0, Math.max(1.0,
            json.has("distance") ? json.get("distance").getAsDouble()
          : json.has("radius") ? json.get("radius").getAsDouble() : 16.0));
        ComparisonType comparison = ComparisonType.fromString(
            json.has("comparison") ? json.get("comparison").getAsString() : ">=");
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 1.0;

        final double dist = distance;
        final double distSq = distance * distance;
        final long stopAt = switch (comparison) {
            case GREATER_THAN_OR_EQUAL, LESS_THAN -> (long) Math.max(0, Math.ceil(target));
            case GREATER_THAN, LESS_THAN_OR_EQUAL, EQUAL, NOT_EQUAL -> (long) Math.max(0, Math.floor(target) + 1);
        };
        return p -> {
            var aabb = p.getBoundingBox().inflate(dist);
            long count = 0;
            for (var entity : p.level().getEntitiesOfClass(
                    net.minecraft.world.entity.npc.villager.Villager.class, aabb)) {
                if (entity.distanceToSqr(p) > distSq) continue;
                count++;
                if (count >= stopAt) break;
            }
            return comparison.test(count, target);
        };
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

    // ── Umbrella detection (weather-damage shielding) ───────────────────

    /**
     * Cached result of the Vampires Need Umbrellas mod-loaded check so we don't
     * query ModList every tick. It gates only the VNU whole-namespace shortcut;
     * the {@link #UMBRELLAS} item tag is consulted whether or not VNU is present.
     */
    private static final boolean VNU_LOADED = neoorigins$modLoaded("vampiresneedumbrellas");

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
     * Returns true if the entity has an umbrella equipped — in either hand or in
     * any Curios/Accessories slot. Used by both weather-damage conditions
     * ({@code exposed_to_sun} and {@code in_rain}) so one umbrella shields the
     * holder from both.
     *
     * <p>The accessory-slot scan is delegated to {@link AccessoryInspector},
     * which owns the Curios reflection this method used to inline, so the
     * umbrella check, {@code equipped_item} and {@code keep_inventory} all read
     * the same slots.
     *
     * @see #neoorigins$isUmbrella for what counts as an umbrella
     */
    static boolean neoorigins$isHoldingUmbrella(net.minecraft.world.entity.LivingEntity entity) {
        if (neoorigins$isUmbrella(entity.getMainHandItem())) return true;
        if (neoorigins$isUmbrella(entity.getOffhandItem())) return true;
        for (net.minecraft.world.item.ItemStack stack
                : AccessoryInspector.getEquippedAccessories(entity, null)) {
            if (neoorigins$isUmbrella(stack)) return true;
        }
        return false;
    }

    /**
     * An item counts as an umbrella if it is in the {@code neoorigins:umbrellas}
     * item tag — which ships {@code artifacts:umbrella} as an optional entry and
     * is open for datapacks to extend — or, when Vampires Need Umbrellas is
     * installed, if it comes from that mod's namespace at all. That whole-namespace
     * match is inherited unchanged from the original check.
     * Neither mod is a dependency: detection is by tag and item id only.
     */
    private static boolean neoorigins$isUmbrella(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(UMBRELLAS)) return true;
        if (!VNU_LOADED) return false;
        net.minecraft.resources.Identifier id =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "vampiresneedumbrellas".equals(id.getNamespace());
    }
}
