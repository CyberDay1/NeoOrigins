package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.util.*;

/**
 * Route B compatibility loader.
 *
 * Runs AFTER PowerDataManager (native Route A). Scans the same power paths,
 * identifies Origins power types that Route A skipped, compiles them into
 * CompatPower.Config lambdas using the action/condition engine, and injects
 * them into PowerDataManager via injectExternalPowers().
 */
public class OriginsCompatPowerLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final OriginsCompatPowerLoader INSTANCE = new OriginsCompatPowerLoader();

    /** Power types that Route B handles (Route A SKIPs these). */
    private static final Set<String> ROUTE_B_TYPES = Set.of(
        "origins:active_self",           "apace:active_self",
        "origins:action_over_time",      "apace:action_over_time",
        "origins:action_on_callback",    "apace:action_on_callback",
        "origins:resource",              "apace:resource",
        "origins:toggle",                "apace:toggle",
        "origins:conditioned_attribute", "apace:conditioned_attribute",
        "origins:conditioned_status_effect", "apace:conditioned_status_effect",
        "origins:action_on_being_hit",   "apace:action_on_being_hit",
        "origins:self_action_when_hit",  "apace:self_action_when_hit",
        "origins:self_action_on_hit",    "apace:self_action_on_hit",
        "origins:action_on_hit",         "apace:action_on_hit",
        "origins:damage_over_time",      "apace:damage_over_time",
        "origins:action_on_kill",        "apace:action_on_kill",
        // Phase 3: New Route B types
        "origins:fire_projectile",       "apace:fire_projectile",
        "origins:target_action_on_hit",  "apace:target_action_on_hit",
        "origins:self_action_on_kill",   "apace:self_action_on_kill",
        "origins:launch",               "apace:launch",
        "origins:entity_glow",          "apace:entity_glow",
        "origins:self_glow",            "apace:self_glow",
        "origins:prevent_death",        "apace:prevent_death",
        "origins:action_when_hit",      "apace:action_when_hit",
        "origins:action_when_damage_taken", "apace:action_when_damage_taken",
        "origins:attacker_action_when_hit", "apace:attacker_action_when_hit",
        "origins:action_on_land",       "apace:action_on_land",
        // Phase 5: Event-based powers (loaded here, events handled by CompatEventPowers)
        "origins:prevent_item_use",     "apace:prevent_item_use",
        "origins:restrict_armor",       "apace:restrict_armor",
        "origins:prevent_sleep",        "apace:prevent_sleep",
        "origins:prevent_block_use",    "apace:prevent_block_use",
        "origins:prevent_entity_use",   "apace:prevent_entity_use",
        "origins:modify_food",          "apace:modify_food",
        "origins:modify_jump",          "apace:modify_jump",
        "origins:prevent_sprinting",    "apace:prevent_sprinting",
        "origins:modify_crafting",      "apace:modify_crafting",
        "origins:modify_lava_speed",    "apace:modify_lava_speed",
        "origins:modify_xp_gain",       "apace:modify_xp_gain",
        "origins:shaking",              "apace:shaking",
        "apoli:overlay",
        "origins:modify_status_effect_amplifier", "apace:modify_status_effect_amplifier",
        "origins:modify_falling",       "apace:modify_falling",
        // Phase 8: Origins++ compat
        "origins:conditioned_restrict_armor", "apace:conditioned_restrict_armor",
        "origins:freeze",               "apace:freeze",
        "origins:modify_harvest",       "apace:modify_harvest",
        "origins:recipe",               "apace:recipe"
    );

    private static final Set<String> MULTIPLE_META_KEYS = OriginsMultipleExpander.META_KEYS;

    /** Check if a raw type string (before canonicalization) is handled by Route B. */
    public static boolean isRouteBType(String rawType) {
        return ROUTE_B_TYPES.contains(rawType);
    }

    private static final FileToIdConverter FILE_CONVERTER  = FileToIdConverter.json("origins/powers");
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    // ---- SimplePreparableReloadListener ----

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER,  rm, map);
        scanConverter(COMPAT_CONVERTER, rm, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager rm,
                                Map<Identifier, JsonElement> map) {
        for (var entry : converter.listMatchingResources(rm).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id     = converter.fileToId(fileId);
            if (map.containsKey(id)) continue;
            if (converter == COMPAT_CONVERTER && NeoOrigins.MOD_ID.equals(id.getNamespace())) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("[CompatB] Error reading {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager rm, ProfilerFiller profiler) {
        // Clear event power state from the previous reload cycle
        CompatPlayerState.clearAll();
        ModifyCraftingRegistry.clearAll();
        ModifyFoodRegistry.clearAll();
        NumericModifierRegistry.clearAll();
        CompatAttachments.clearResourceMeta();

        // Inline-expand any origins:multiple entries so sub-power JSONs are accessible.
        Map<Identifier, JsonObject> expanded = inlineExpand(data);

        Map<Identifier, PowerHolder<?>> injected = new HashMap<>();
        // Track new synthetic IDs to add to MULTIPLE_EXPANSION_MAP
        Map<Identifier, List<Identifier>> newExpansions = new HashMap<>();

        for (var entry : expanded.entrySet()) {
            Identifier id   = entry.getKey();
            JsonObject json = entry.getValue();
            String type = OriginsFormatDetector.getType(json);

            // modify_damage_taken/dealt are Route A types normally, but when a
            // condition is present we fall through to Route B so the condition
            // can gate the damage modifier — native ModifyDamagePower has no
            // condition support.
            boolean conditionedModifyDamage = isModifyDamageTakenType(type) && json.has("condition");
            if (!ROUTE_B_TYPES.contains(type) && !conditionedModifyDamage) continue;
            // Route A already loaded this ID — skip
            if (PowerDataManager.INSTANCE.hasPower(id)) continue;

            try {
                CompatPower.Config config = parseRouteB(id, type, json);
                if (config == null) {
                    CompatTranslationLog.skip(id, type, "Route B: no handler produced a config");
                    continue;
                }
                Component powerName = extractComponent(json, "name");
                Component powerDesc = extractComponent(json, "description");
                boolean powerHidden = json.has("hidden") && json.get("hidden").isJsonPrimitive()
                    && json.get("hidden").getAsJsonPrimitive().isBoolean()
                    && json.get("hidden").getAsBoolean();
                injected.put(id, new PowerHolder<>(id, CompatPower.INSTANCE, config, powerName, powerDesc, powerHidden));
                CompatTranslationLog.pass(id, type + " -> Route B compiled");
                NeoOrigins.LOGGER.debug("[CompatB] loaded {} ({})", id, type);

                // If this is a synthetic sub-power, update the expansion map
                String idPath = id.getPath();
                int lastSlash = idPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    String parentPath = idPath.substring(0, lastSlash);
                    Identifier parentId = Identifier.fromNamespaceAndPath(id.getNamespace(), parentPath);
                    newExpansions.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
                }
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                NeoOrigins.LOGGER.warn("[CompatB] Failed to load {} ({}): {}", id, type, reason);
                CompatTranslationLog.fail(id, type + ": " + reason);
            }
        }

        // Merge new Route B synthetic IDs into OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP
        // so OriginDataManager includes them in origin power lists.
        for (var entry : newExpansions.entrySet()) {
            List<Identifier> existing = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP
                .getOrDefault(entry.getKey(), List.of());
            List<Identifier> merged = new ArrayList<>(existing);
            for (Identifier newId : entry.getValue()) {
                if (!merged.contains(newId)) merged.add(newId);
            }
            OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.put(entry.getKey(),
                Collections.unmodifiableList(merged));
        }

        // Inject synthetic powers for well-known Origins built-in power IDs.
        injectWellKnownPowers(injected);

        PowerDataManager.INSTANCE.injectExternalPowers(injected);
        NeoOrigins.LOGGER.info("[CompatB] Injected {} Route B powers", injected.size());
    }

    private void injectWellKnownPowers(Map<Identifier, PowerHolder<?>> injected) {
        Map<String, java.util.function.Supplier<JsonObject>> WELL_KNOWN = Map.ofEntries(
            Map.entry("origins:elytra",              () -> json("neoorigins:natural_glide")),
            Map.entry("origins:fire_immunity",       () -> json("neoorigins:prevent_action", "action", "fire")),
            Map.entry("origins:fresh_air",           OriginsCompatPowerLoader::freshAirJson),
            Map.entry("origins:like_water",          () -> json("neoorigins:ignore_water")),
            Map.entry("origins:aquatic",             () -> json("neoorigins:dries_out")),
            Map.entry("origins:water_vision",        () -> json("neoorigins:lava_vision")),
            Map.entry("origins:aqua_affinity",       () -> json("neoorigins:underwater_mining")),
            Map.entry("origins:conduit_power_on_land", () -> json("neoorigins:conduit_power")),
            Map.entry("origins:air_from_potions",    () -> json("neoorigins:water_breathing")),
            Map.entry("origins:water_breathing",     () -> json("neoorigins:water_breathing")),
            Map.entry("origins:swim_speed",          () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:water_movement_efficiency", "amount", 0.5, "operation", "add_value")),
            Map.entry("origins:night_vision",        () -> json("neoorigins:night_vision")),
            Map.entry("origins:slow_falling",        () -> json("neoorigins:prevent_action", "action", "fall_damage")),
            Map.entry("origins:climbing",            () -> json("neoorigins:wall_climbing")),
            Map.entry("origins:shulker_inventory",   () -> json("neoorigins:extra_inventory")),
            Map.entry("origins:phantomize",          () -> json("neoorigins:phantom_form")),
            Map.entry("origins:translucent",         () -> json("neoorigins:model_color", "red", 1.0, "green", 1.0, "blue", 1.0, "alpha", 0.5)),
            // ── Felvaxian / common addon references ──
            Map.entry("origins:carnivore",           () -> json("neoorigins:food_restriction", "mode", "whitelist", "item_tag", "neoorigins:meat_foods")),
            Map.entry("origins:cat_vision",           () -> json("neoorigins:night_vision")),
            Map.entry("origins:fall_immunity",        () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.safe_fall_distance", "amount", 1000.0, "operation", "add_value")),
            Map.entry("origins:launch_into_air",      () -> json("neoorigins:active_ability")),
            Map.entry("origins:light_armor",          () -> json("neoorigins:restrict_armor", "armor_class", "heavy")),
            Map.entry("origins:nine_lives",           () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.max_health", "amount", -2.0, "operation", "add_value")),
            Map.entry("origins:no_shield",            () -> json("neoorigins:prevent_action", "action", "shield")),
            Map.entry("origins:vegetarian",           () -> json("neoorigins:food_restriction", "mode", "blacklist", "item_tag", "neoorigins:meat_foods")),
            Map.entry("origins:weak_arms",            () -> json("neoorigins:break_speed_modifier", "modifier", -0.5)),
            Map.entry("origins:master_of_webs",       () -> json("neoorigins:wall_climbing")),
            Map.entry("origins:arthropod",            () -> json("neoorigins:entity_group", "group", "arthropod")),
            Map.entry("origins:fragile",              () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.max_health", "amount", -6.0, "operation", "add_value")),
            Map.entry("origins:phasing",              () -> json("neoorigins:phantom_form")),
            Map.entry("origins:burn_in_daylight",     () -> json("neoorigins:condition_passive")),
            Map.entry("origins:damage_from_potions",  () -> json("neoorigins:effect_immunity")),
            Map.entry("origins:more_kinetic_damage",  () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.safe_fall_distance", "amount", -2.0, "operation", "add_value")),
            Map.entry("origins:throw_ender_pearl",    () -> json("neoorigins:active_ability")),
            Map.entry("origins:pumpkin_hate",         () -> json("neoorigins:restrict_armor", "armor_class", "pumpkin")),
            Map.entry("origins:hotblooded",           () -> json("neoorigins:effect_immunity")),
            Map.entry("origins:water_vulnerability",  () -> json("neoorigins:condition_passive")),
            Map.entry("origins:flame_particles",      () -> json("neoorigins:particle")),
            Map.entry("origins:nether_spawn",         () -> json("neoorigins:spawn_location"))
        );

        for (var entry : WELL_KNOWN.entrySet()) {
            Identifier id = Identifier.parse(entry.getKey());
            if (PowerDataManager.INSTANCE.hasPower(id) || injected.containsKey(id)) continue;
            try {
                JsonObject powerJson = entry.getValue().get();
                String typeStr = powerJson.get("type").getAsString();
                Identifier typeId = Identifier.parse(typeStr);
                com.cyberday1.neoorigins.api.power.PowerType<?> powerType =
                    com.cyberday1.neoorigins.power.registry.PowerTypes.get(typeId);
                if (powerType != null) {
                    injectViaNativeCodec(id, powerType, powerJson, injected);
                    NeoOrigins.LOGGER.debug("[CompatSynth] Registered well-known power {} -> {}", id, typeStr);
                } else {
                    CompatPower.Config config = parseRouteB(id, typeStr, powerJson);
                    if (config != null) {
                        injected.put(id, new PowerHolder<>(id, CompatPower.INSTANCE, config,
                            net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty()));
                        NeoOrigins.LOGGER.debug("[CompatSynth] Registered well-known power {} -> {} (Route B)", id, typeStr);
                    }
                }
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("[CompatSynth] Failed to register well-known power {}: {}", id, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <C extends com.cyberday1.neoorigins.api.power.PowerConfiguration> void injectViaNativeCodec(
            Identifier id, com.cyberday1.neoorigins.api.power.PowerType<C> type,
            JsonObject json, Map<Identifier, PowerHolder<?>> target) {
        JsonObject configJson = json.deepCopy();
        configJson.addProperty("_power_id", id.toString());
        type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, configJson)
            .resultOrPartial(err -> NeoOrigins.LOGGER.warn("[CompatSynth] codec error for {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(id, type, config,
                net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty())));
    }

    private static JsonObject json(String type) {
        JsonObject o = new JsonObject(); o.addProperty("type", type); return o;
    }
    private static JsonObject json(String type, String k1, String v1) {
        JsonObject o = json(type); o.addProperty(k1, v1); return o;
    }
    private static JsonObject json(String type, String k1, double v1, String k2, double v2, String k3, double v3, String k4, double v4) {
        JsonObject o = json(type); o.addProperty(k1, v1); o.addProperty(k2, v2); o.addProperty(k3, v3); o.addProperty(k4, v4); return o;
    }
    private static JsonObject json(String type, String k1, String v1, String k2, double v2, String k3, String v3) {
        JsonObject o = json(type); o.addProperty(k1, v1); o.addProperty(k2, v2); o.addProperty(k3, v3); return o;
    }

    private static JsonObject json(String type, String k1, String v1, String k2, String v2) {
        JsonObject o = json(type); o.addProperty(k1, v1); o.addProperty(k2, v2); return o;
    }
    private static JsonObject json(String type, String k1, double v1) {
        JsonObject o = json(type); o.addProperty(k1, v1); return o;
    }

    /**
     * Generates an Origins-format {@code prevent_sleep} JSON with a height
     * block_condition. Vanilla Origins' fresh_air prevents sleep below Y 86.
     * Uses Origins type so it falls through to Route B's parsePreventSleep.
     */
    private static com.google.gson.JsonObject freshAirJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("type", "origins:prevent_sleep");
        o.addProperty("set_spawn_point", true);
        com.google.gson.JsonObject blockCond = new com.google.gson.JsonObject();
        blockCond.addProperty("type", "origins:height");
        blockCond.addProperty("comparison", "<");
        blockCond.addProperty("compare_to", 86);
        o.add("block_condition", blockCond);
        return o;
    }

    /**
     * Inline-expand origins:multiple entries in the raw data map.
     * Returns a flat map of id → JsonObject covering both direct powers and sub-powers.
     * Does NOT call OriginsMultipleExpander (avoids touching its state twice).
     */
    private Map<Identifier, JsonObject> inlineExpand(Map<Identifier, JsonElement> data) {
        Map<Identifier, JsonObject> result = new HashMap<>();
        for (var entry : data.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = OriginsFormatDetector.getType(json);
            if ("origins:multiple".equals(type) || "apace:multiple".equals(type)) {
                expandMultiple(entry.getKey(), json, result);
            } else {
                result.put(entry.getKey(), json);
            }
        }
        return result;
    }

    private void expandMultiple(Identifier parentId, JsonObject json, Map<Identifier, JsonObject> out) {
        for (var subEntry : json.entrySet()) {
            if (MULTIPLE_META_KEYS.contains(subEntry.getKey())) continue;
            if (!subEntry.getValue().isJsonObject()) continue;
            JsonObject subJson = subEntry.getValue().getAsJsonObject();
            Identifier syntheticId = Identifier.fromNamespaceAndPath(
                parentId.getNamespace(), parentId.getPath() + "/" + subEntry.getKey()
            );
            String subType = OriginsFormatDetector.getType(subJson);
            if ("origins:multiple".equals(subType) || "apace:multiple".equals(subType)) {
                expandMultiple(syntheticId, subJson, out); // recurse
            } else {
                // Resolve *:* self-references before storing. In Origins/Apoli,
                // "*:*_subkey" within a multiple means the sibling sub-power
                // whose synthetic ID is "parentId/subkey".
                subJson = resolveSelfReferences(subJson, parentId);
                out.put(syntheticId, subJson);
            }
        }
    }

    /** Delegates to shared self-reference resolver in OriginsMultipleExpander. */
    private static JsonObject resolveSelfReferences(JsonObject json, Identifier parentId) {
        return OriginsMultipleExpander.resolveSelfReferences(json, parentId);
    }

    private static Component extractComponent(JsonObject json, String field) {
        if (!json.has(field)) return Component.empty();
        JsonElement el = json.get(field);
        if (el.isJsonPrimitive()) return Component.translatable(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text"))      return Component.literal(obj.get("text").getAsString());
            if (obj.has("translate")) return Component.translatable(obj.get("translate").getAsString());
        }
        return Component.empty();
    }

    // ---- Per-type parsers ----

    private CompatPower.Config parseRouteB(Identifier id, String type, JsonObject json) {
        return switch (type) {
            case "origins:active_self",                "apace:active_self"                -> parseActiveSelf(id, json);
            case "origins:action_over_time",           "apace:action_over_time"           -> parseActionOverTime(id, json);
            case "origins:action_on_callback",         "apace:action_on_callback"         -> parseActionOnCallback(id, json);
            case "origins:resource",                   "apace:resource"                   -> parseResource(id, json);
            case "origins:toggle",                     "apace:toggle"                     -> parseToggle(id, json);
            case "origins:conditioned_attribute",      "apace:conditioned_attribute"      -> parseConditionedAttribute(id, json);
            case "origins:conditioned_status_effect",  "apace:conditioned_status_effect"  -> parseConditionedStatusEffect(id, json);
            case "origins:action_on_being_hit",        "apace:action_on_being_hit",
                 "origins:self_action_when_hit",       "apace:self_action_when_hit",
                 "origins:self_action_on_hit",         "apace:self_action_on_hit",
                 "origins:action_on_hit",              "apace:action_on_hit",
                 "origins:action_when_hit",            "apace:action_when_hit",
                 "origins:action_when_damage_taken",   "apace:action_when_damage_taken",
                 "origins:attacker_action_when_hit",   "apace:attacker_action_when_hit"   -> parseSelfActionWhenHit(id, json);
            case "origins:damage_over_time",           "apace:damage_over_time"           -> parseDamageOverTime(id, json);
            // Phase 3: New Route B types
            case "origins:fire_projectile",            "apace:fire_projectile"            -> parseFireProjectile(id, json);
            case "origins:target_action_on_hit",       "apace:target_action_on_hit"       -> parseTargetActionOnHit(id, json);
            case "origins:self_action_on_kill",        "apace:self_action_on_kill",
                 "origins:action_on_kill",              "apace:action_on_kill"             -> parseSelfActionOnKill(id, json);
            case "origins:launch",                     "apace:launch"                     -> parseLaunch(id, json);
            case "origins:entity_glow",                "apace:entity_glow",
                 "origins:self_glow",                  "apace:self_glow"                  -> parseEntityGlow(id, json);
            case "origins:prevent_death",              "apace:prevent_death"              -> parsePreventDeath(id, json);
            case "origins:action_on_land",             "apace:action_on_land"             -> parseActionOnLand(id, json);
            // Phase 5: Event-based powers
            case "origins:prevent_item_use",           "apace:prevent_item_use"           -> parsePreventItemUse(id, json);
            case "origins:restrict_armor",             "apace:restrict_armor"             -> parseRestrictArmor(id, json);
            case "origins:prevent_sleep",              "apace:prevent_sleep"              -> parsePreventSleep(id, json);
            case "origins:prevent_block_use",          "apace:prevent_block_use"          -> parsePreventBlockUse(id, json);
            case "origins:prevent_entity_use",         "apace:prevent_entity_use"         -> parsePreventEntityUse(id, json);
            case "origins:modify_food",                "apace:modify_food"                -> parseModifyFood(id, json);
            case "origins:modify_jump",                "apace:modify_jump"                -> parseModifyJump(id, json);
            // Conditioned modify_damage_taken — only dispatched here when a condition is present.
            case "origins:modify_damage_taken",        "apace:modify_damage_taken"        -> parseConditionedModifyDamageTaken(id, json);
            case "origins:shaking",                    "apace:shaking"                    -> parseShaking(id, json);
            case "apoli:overlay"                                                          -> parseOverlay(id, json);
            case "origins:modify_status_effect_amplifier", "apace:modify_status_effect_amplifier" -> parseModifyEffectAmplifier(id, json);
            case "origins:modify_falling",             "apace:modify_falling"             -> parseModifyFalling(id, json);
            // Phase 8: Origins++ compat
            case "origins:conditioned_restrict_armor", "apace:conditioned_restrict_armor" -> parseConditionedRestrictArmor(id, json);
            case "origins:freeze",                     "apace:freeze"                     -> parseFreeze(id, json);
            case "origins:modify_harvest",             "apace:modify_harvest"             -> parseModifyHarvest(id, json);
            case "origins:recipe",                     "apace:recipe"                     -> parseRecipe(id, json);
            default -> null;
        };
    }

    private static boolean isModifyDamageTakenType(String type) {
        return "origins:modify_damage_taken".equals(type) || "apace:modify_damage_taken".equals(type);
    }

    private CompatPower.Config parseConditionedModifyDamageTaken(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Extract the multiplier from the Origins modifier object. All operations
        // collapse to (1 + value) — same lossy mapping as Route A's translateModifyDamage.
        float multiplier = 1.0f;
        if (json.has("modifier") && json.get("modifier").isJsonObject()) {
            JsonObject mod = json.getAsJsonObject("modifier");
            if (mod.has("value")) {
                multiplier = (float)(1.0 + mod.get("value").getAsDouble());
            }
        }

        // Optional damage type filter — msgId-based, mirrors native ModifyDamagePower.
        String damageTypeFilter = null;
        if (json.has("damage_condition") && json.get("damage_condition").isJsonObject()) {
            JsonObject dc = json.getAsJsonObject("damage_condition");
            String dcType = dc.has("type") ? dc.get("type").getAsString() : "";
            if (("origins:name".equals(dcType) || "apace:name".equals(dcType)) && dc.has("name")) {
                damageTypeFilter = dc.get("name").getAsString();
            }
        }

        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        final float  finalMultiplier = multiplier;
        final String finalDmgFilter  = damageTypeFilter;

        return CompatPower.Config.builder()
            .onIncomingDamage(event -> {
                if (!(event.getEntity() instanceof ServerPlayer sp)) return;
                if (!condition.test(sp)) return;
                if (finalDmgFilter != null
                        && !event.getSource().getMsgId().equalsIgnoreCase(finalDmgFilter)) {
                    return;
                }
                // Overflow-safe multiply (same clamp used by CombatPowerEvents native path).
                float scaled = event.getAmount() * finalMultiplier;
                if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
                event.setAmount(scaled);
                // A 0-multiplier effectively cancels the hit; callers commonly rely on that.
                if (scaled <= 0.0f) event.setCanceled(true);
            })
            .build();
    }

    private CompatPower.Config parseActiveSelf(Identifier id, JsonObject json) {
        String idStr = id.toString();
        JsonObject actionJson = json.has("entity_action") ? json.getAsJsonObject("entity_action")
            : json.has("action") ? json.getAsJsonObject("action") : null;
        if (actionJson == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: active_self power missing 'entity_action' or 'action' field — power will not be registered", id);
            return null;
        }

        EntityAction action = ActionParser.parse(actionJson, idStr);
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;

        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onActivated((ServerPlayer player) -> {
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(idStr, player.tickCount)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                action.execute(player);
            })
            .build();
    }

    private CompatPower.Config parseActionOverTime(Identifier id, JsonObject json) {
        String idStr = id.toString();
        int interval = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 1);

        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();
        // Apoli/MoR/Mido pack convention is `condition` (player-side gate);
        // apace and a few origins-classes packs use `entity_condition`.
        // Accept both — without this, packs like MoR Pixie's flight resource
        // drain gate and Mido moisture ticks silently dropped their
        // condition, defaulting to alwaysTrue and firing every interval.
        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : json.has("entity_condition")
                ? ConditionParser.parse(json.getAsJsonObject("entity_condition"), idStr)
                : EntityCondition.alwaysTrue();
        // If any condition in the tree used an unsupported type, refuse to compile
        // this power. Running an action_over_time unconditionally (because its gate
        // condition silently failed) causes disasters like entity-per-tick spawns.
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] action_over_time {} has unsupported condition(s) — refusing to compile to prevent unconditional execution", idStr);
            return null;
        }

        // Stagger by ID hash so not all action_over_time powers run on the same tick.
        int offset = Math.abs(idStr.hashCode()) % interval;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.level().getServer() == null) return;
                long tick = player.level().getServer().getTickCount();
                if ((tick + offset) % interval == 0 && condition.test(player)) {
                    action.execute(player);
                }
            })
            .build();
    }

    private CompatPower.Config parseActionOnCallback(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Respawn: upstream Apoli uses `respawn_entity_action`; we historically supported
        // our own `respawn_action`. Accept both; merge if present.
        EntityAction respawnAction = EntityAction.noop();
        if (json.has("respawn_entity_action")) {
            respawnAction = ActionParser.parse(json.getAsJsonObject("respawn_entity_action"), idStr);
        }
        if (json.has("respawn_action")) {
            respawnAction = mergeActions(respawnAction,
                ActionParser.parse(json.getAsJsonObject("respawn_action"), idStr));
        }

        // Removal: upstream Apoli uses `entity_action_lost` (and some forks use
        // `entity_action_removed`). We historically supported our own `removed_action`.
        // Accept all three; merge if multiple are present.
        EntityAction removedAction = EntityAction.noop();
        if (json.has("entity_action_lost")) {
            removedAction = ActionParser.parse(json.getAsJsonObject("entity_action_lost"), idStr);
        }
        if (json.has("entity_action_removed")) {
            removedAction = mergeActions(removedAction,
                ActionParser.parse(json.getAsJsonObject("entity_action_removed"), idStr));
        }
        if (json.has("removed_action")) {
            removedAction = mergeActions(removedAction,
                ActionParser.parse(json.getAsJsonObject("removed_action"), idStr));
        }

        // Upstream Origins has separate triggers for "gained" (every grant, including
        // login) and "chosen" (only when the player selects from the GUI). We merge
        // both into onGranted — the distinction is lost, but most addon packs use
        // entity_action_chosen for one-time setup (e.g. granting starter items via
        // /function) and the commands are typically idempotent.
        EntityAction addedAction = EntityAction.noop();
        if (json.has("entity_action_chosen")) {
            addedAction = ActionParser.parse(json.getAsJsonObject("entity_action_chosen"), idStr);
        }
        if (json.has("entity_action_gained")) {
            addedAction = mergeActions(addedAction,
                ActionParser.parse(json.getAsJsonObject("entity_action_gained"), idStr));
        }
        if (json.has("added_action")) {
            addedAction = mergeActions(addedAction,
                ActionParser.parse(json.getAsJsonObject("added_action"), idStr));
        }

        EntityAction finalAdded = addedAction;
        return CompatPower.Config.builder()
            .onGranted(finalAdded::execute)
            .onRevoked(removedAction::execute)
            .onRespawn(respawnAction::execute)
            .build();
    }

    /** Combine two entity actions into one that runs both sequentially. */
    private static EntityAction mergeActions(EntityAction first, EntityAction second) {
        if (first == EntityAction.noop()) return second;
        if (second == EntityAction.noop()) return first;
        return player -> { first.execute(player); second.execute(player); };
    }

    private CompatPower.Config parseResource(Identifier id, JsonObject json) {
        String key       = id.toString();
        String idStr     = key;
        int min          = json.has("min")         ? json.get("min").getAsInt()         : 0;
        int max          = json.has("max")         ? json.get("max").getAsInt()         : 100;
        int startValue   = json.has("start_value") ? json.get("start_value").getAsInt() : min;
        int interval     = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 20);
        int offset       = Math.abs(idStr.hashCode()) % interval;

        EntityAction minAction  = json.has("min_action")
            ? ActionParser.parse(json.getAsJsonObject("min_action"),  idStr) : EntityAction.noop();
        EntityAction maxAction  = json.has("max_action")
            ? ActionParser.parse(json.getAsJsonObject("max_action"),  idStr) : EntityAction.noop();
        EntityAction tickAction = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr) : EntityAction.noop();

        // Derive a human-readable label from the power ID path segment.
        String path = id.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        String rawLabel = path.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : rawLabel.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        String label = sb.toString();

        // Check hud_render.should_render — Origins compat field to hide the bar
        boolean hidden = false;
        if (json.has("hud_render") && json.get("hud_render").isJsonObject()) {
            com.google.gson.JsonObject hud = json.getAsJsonObject("hud_render");
            if (hud.has("should_render") && !hud.get("should_render").getAsBoolean()) {
                hidden = true;
            }
        }

        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(min, max, label, 0xFF55AAFF, hidden));

        return CompatPower.Config.builder()
            .onGranted(player -> {
                player.getData(CompatAttachments.resourceState()).set(key, startValue);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onRevoked(player -> {
                player.getData(CompatAttachments.resourceState()).remove(key);
                CompatAttachments.unregisterResourceMeta(key);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onTick(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                if (player.level().getServer() != null && (player.level().getServer().getTickCount() + offset) % interval == 0) {
                    tickAction.execute(player);
                }
                int cur = state.get(key, startValue);
                if (cur <= min) minAction.execute(player);
                if (cur >= max) maxAction.execute(player);
                // Sync to client every 10 ticks when dirty
                if (state.isDirty() && player.tickCount % 10 == 0) {
                    state.clearDirty();
                    CompatAttachments.syncResourcesToClient(player);
                }
            })
            .build();
    }

    private CompatPower.Config parseToggle(Identifier id, JsonObject json) {
        String key = id.toString();
        boolean defaultActive = !json.has("active") || json.get("active").getAsBoolean();

        EntityAction activeAction   = json.has("active_action")
            ? ActionParser.parse(json.getAsJsonObject("active_action"),   key) : EntityAction.noop();
        EntityAction inactiveAction = json.has("inactive_action")
            ? ActionParser.parse(json.getAsJsonObject("inactive_action"), key) : EntityAction.noop();

        return CompatPower.Config.builder()
            .onGranted(player -> player.getData(CompatAttachments.toggleState()).set(key, defaultActive))
            .onActivated(player -> {
                boolean next = player.getData(CompatAttachments.toggleState()).toggle(key, defaultActive);
                if (next) activeAction.execute(player);
                else inactiveAction.execute(player);
            })
            .build();
    }

    private CompatPower.Config parseConditionedAttribute(Identifier id, JsonObject json) {
        String idStr = id.toString();
        // Attribute can be at top level or nested inside "modifier" object
        String attrStr = null;
        if (json.has("attribute")) {
            attrStr = json.get("attribute").getAsString();
        } else if (json.has("modifier") && json.get("modifier").isJsonObject()
                   && json.getAsJsonObject("modifier").has("attribute")) {
            attrStr = json.getAsJsonObject("modifier").get("attribute").getAsString();
        }
        if (attrStr == null) {
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "missing 'attribute' field in JSON");
            return null;
        }

        Identifier rawAttrIdent = Identifier.parse(attrStr);
        // Try the raw attribute name first. If that fails and the name has a
        // "generic." prefix (used in MC ≤1.21.1), try without it (MC 1.21.2+
        // dropped the prefix). This lets the same pack work on both versions.
        var attrHolder = BuiltInRegistries.ATTRIBUTE.get(rawAttrIdent).orElse(null);
        Identifier attrIdent = rawAttrIdent;
        if (attrHolder == null && rawAttrIdent.getPath().startsWith("generic.")) {
            attrIdent = Identifier.fromNamespaceAndPath(rawAttrIdent.getNamespace(),
                rawAttrIdent.getPath().substring("generic.".length()));
            attrHolder = BuiltInRegistries.ATTRIBUTE.get(attrIdent).orElse(null);
        }
        // Also try adding "generic." prefix if not present (26.1 pack on 1.21.1)
        if (attrHolder == null && !rawAttrIdent.getPath().startsWith("generic.")) {
            attrIdent = Identifier.fromNamespaceAndPath(rawAttrIdent.getNamespace(),
                "generic." + rawAttrIdent.getPath());
            attrHolder = BuiltInRegistries.ATTRIBUTE.get(attrIdent).orElse(null);
        }
        if (attrHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown attribute '{}' (raw: '{}') — power will no-op",
                idStr, attrIdent, rawAttrIdent);
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "unknown attribute '" + rawAttrIdent + "' — pack-side fix: confirm attribute exists in this MC version");
            return null;
        }
        final var resolvedAttrHolder = attrHolder;

        JsonObject modObj = json.has("modifier") ? json.getAsJsonObject("modifier") : json;
        double value = modObj.has("value")  ? modObj.get("value").getAsDouble()
                     : modObj.has("amount") ? modObj.get("amount").getAsDouble() : 0.0;
        String op = modObj.has("operation") ? modObj.get("operation").getAsString() : "add_value";
        AttributeModifier.Operation operation = switch (OriginsOperationMapper.mapOperation(op)) {
            case "add_multiplied_base"  -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default                     -> AttributeModifier.Operation.ADD_VALUE;
        };

        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        // Stable modifier ID derived from the power ID.
        String safeKey = id.getPath().replace('/', '_');
        Identifier modifierId = Identifier.fromNamespaceAndPath("neoorigins", "condattr_" + safeKey);

        return CompatPower.Config.builder()
            .onTick(player -> {
                AttributeInstance inst = player.getAttribute(resolvedAttrHolder);
                if (inst == null) return;
                boolean shouldHave = condition.test(player);
                boolean has = inst.getModifier(modifierId) != null;
                if (shouldHave && !has) {
                    inst.addPermanentModifier(new AttributeModifier(modifierId, value, operation));
                } else if (!shouldHave && has) {
                    inst.removeModifier(modifierId);
                }
            })
            .onRevoked(player -> {
                AttributeInstance inst = player.getAttribute(resolvedAttrHolder);
                if (inst != null) inst.removeModifier(modifierId);
            })
            .build();
    }

    /** origins:shaking — makes the player model shake (like zombie-to-drowned conversion).
     *  Implemented via freeze ticks which trigger the same visual. */
    private CompatPower.Config parseShaking(Identifier id, JsonObject json) {
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), id.toString())
            : EntityCondition.alwaysTrue();
        return CompatPower.Config.builder()
            .onTick(player -> {
                // Set freeze ticks just above the threshold to trigger shaking
                // without actually freezing the player. 1 tick above threshold
                // shows the animation without dealing damage.
                if (condition.test(player)) {
                    if (player.getTicksFrozen() < 2) player.setTicksFrozen(2);
                } else {
                    if (player.getTicksFrozen() > 0) player.setTicksFrozen(0);
                }
            })
            .onRevoked(player -> player.setTicksFrozen(0))
            .build();
    }

    // ── Effect amplifier modifier registry ─────────────────────────────
    // Keyed by player UUID → effect Identifier → amplifier delta.
    // Checked by CombatPowerEvents.onMobEffectAdded to boost amplifiers.
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,
        java.util.Map<Identifier, Integer>> EFFECT_AMP_MODIFIERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static int getAmplifierBoost(java.util.UUID playerId, Identifier effectId) {
        var map = EFFECT_AMP_MODIFIERS.get(playerId);
        return map == null ? 0 : map.getOrDefault(effectId, 0);
    }

    static void addAmplifierModifier(java.util.UUID playerId, Identifier effectId, int delta) {
        EFFECT_AMP_MODIFIERS.computeIfAbsent(playerId, k -> new java.util.concurrent.ConcurrentHashMap<>())
            .merge(effectId, delta, Integer::sum);
    }

    static void removeAmplifierModifier(java.util.UUID playerId, Identifier effectId, int delta) {
        var map = EFFECT_AMP_MODIFIERS.get(playerId);
        if (map == null) return;
        map.merge(effectId, -delta, Integer::sum);
        if (map.getOrDefault(effectId, 0) == 0) map.remove(effectId);
        if (map.isEmpty()) EFFECT_AMP_MODIFIERS.remove(playerId);
    }

    /** origins:modify_status_effect_amplifier — boosts the amplifier of a specific
     *  effect whenever it's applied to the player. */
    private CompatPower.Config parseModifyEffectAmplifier(Identifier id, JsonObject json) {
        String effectStr = json.has("status_effect") ? json.get("status_effect").getAsString() : null;
        if (effectStr == null) return null;
        Identifier effectId = Identifier.parse(effectStr);

        JsonObject modObj = json.has("modifier") && json.get("modifier").isJsonObject()
            ? json.getAsJsonObject("modifier") : json;
        int value = modObj.has("value") ? modObj.get("value").getAsInt() : 1;

        return CompatPower.Config.builder()
            .onGranted(player -> addAmplifierModifier(player.getUUID(), effectId, value))
            .onRevoked(player -> removeAmplifierModifier(player.getUUID(), effectId, value))
            .build();
    }

    /** origins:modify_falling — slow fall with optional no fall damage. */
    private CompatPower.Config parseModifyFalling(Identifier id, JsonObject json) {
        float velocity = json.has("velocity") ? json.get("velocity").getAsFloat() : 0.1f;
        boolean takeFallDamage = !json.has("take_fall_damage") || json.get("take_fall_damage").getAsBoolean();

        String safeKey = id.getPath().replace('/', '_');
        Identifier gravModId = Identifier.fromNamespaceAndPath("neoorigins", "modfalling_grav_" + safeKey);
        Identifier fallModId = Identifier.fromNamespaceAndPath("neoorigins", "modfalling_fall_" + safeKey);

        // velocity 0.1 means slow fall — reduce gravity. Lower velocity = less gravity.
        // Vanilla gravity is 0.08; slow_falling effect sets it to 0.01.
        // We scale: velocity=0.01 → -0.9875 mult, velocity=0.1 → -0.875 mult
        double gravMult = -(1.0 - (velocity / 0.08));

        return CompatPower.Config.builder()
            .onGranted(player -> {
                var gravAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                if (gravAttr != null && gravAttr.getModifier(gravModId) == null) {
                    gravAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        gravModId, gravMult, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                if (!takeFallDamage) {
                    var fallAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
                    if (fallAttr != null && fallAttr.getModifier(fallModId) == null) {
                        fallAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            fallModId, 1000.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                    }
                }
            })
            .onRevoked(player -> {
                var gravAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                if (gravAttr != null) gravAttr.removeModifier(gravModId);
                var fallAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
                if (fallAttr != null) fallAttr.removeModifier(fallModId);
            })
            .build();
    }

    /** apoli:overlay — screen overlay effect. Parsed as a no-op since overlay rendering
     *  requires client-side hooks not yet ported. The power loads successfully so it
     *  doesn't count against the compat power-ratio threshold. */
    private CompatPower.Config parseOverlay(Identifier id, JsonObject json) {
        return CompatPower.Config.builder().build();
    }

    private CompatPower.Config parseConditionedStatusEffect(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Resolve effect — try "effects" array or singular "effect" field.
        String effectId = null;
        int    amplifier  = 0;
        boolean ambient   = false;
        boolean particles = true;

        if (json.has("effects") && json.get("effects").isJsonArray()) {
            var arr = json.getAsJsonArray("effects");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject eff = arr.get(0).getAsJsonObject();
                if (eff.has("effect"))        effectId  = eff.get("effect").getAsString();
                if (eff.has("amplifier"))     amplifier = eff.get("amplifier").getAsInt();
                if (eff.has("ambient"))       ambient   = eff.get("ambient").getAsBoolean();
                if (eff.has("show_particles"))particles = eff.get("show_particles").getAsBoolean();
            }
        } else if (json.has("effect")) {
            effectId  = json.get("effect").getAsString();
            if (json.has("amplifier"))     amplifier = json.get("amplifier").getAsInt();
            if (json.has("ambient"))       ambient   = json.get("ambient").getAsBoolean();
            if (json.has("show_particles"))particles = json.get("show_particles").getAsBoolean();
        }
        if (effectId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: missing effect id — power will no-op", idStr);
            return null;
        }

        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] conditioned_status_effect {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }

        // Cache mob effect holder at parse time — registry is static
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown mob effect '{}' — power will no-op", idStr, effectId);
            return null;
        }

        int  finalAmp       = amplifier;
        boolean finalAmb    = ambient;
        boolean finalPart   = particles;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!condition.test(player)) {
                    // Remove the effect when the condition is no longer met,
                    // so conditioned effects toggle off cleanly instead of
                    // lingering for their remaining duration.
                    player.removeEffect(effectHolder);
                    return;
                }
                var existing = player.getEffect(effectHolder);
                // Re-apply at 200t duration if missing or about to expire (<100t).
                if (existing == null || existing.getDuration() < 100) {
                    player.addEffect(new MobEffectInstance(
                        effectHolder, 200, finalAmp, finalAmb, finalPart, true));
                }
            })
            .onRevoked(player -> player.removeEffect(effectHolder))
            .build();
    }

    private CompatPower.Config parseSelfActionWhenHit(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();
        // bientity_action is skipped (requires attacker reference).
        return CompatPower.Config.builder()
            .onHit(action::execute)
            .build();
    }

    private CompatPower.Config parseDamageOverTime(Identifier id, JsonObject json) {
        String idStr = id.toString();
        int interval = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 20);
        float damage  = json.has("damage")            ? json.get("damage").getAsFloat()
                      : json.has("damage_per_second") ? json.get("damage_per_second").getAsFloat() : 1.0f;

        // Determine if the damage source should bypass armor.
        boolean unblockable = false;
        if (json.has("source") && json.get("source").isJsonObject()) {
            JsonObject src = json.getAsJsonObject("source");
            unblockable = (src.has("unblockable") && src.get("unblockable").getAsBoolean())
                       || (src.has("bypasses_armor") && src.get("bypasses_armor").getAsBoolean());
        }

        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] damage_over_time {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }

        int offset          = Math.abs(idStr.hashCode()) % interval;
        float finalDamage   = damage;
        boolean finalUnblock = unblockable;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.level().getServer() == null) return;
                long tick = player.level().getServer().getTickCount();
                if ((tick + offset) % interval == 0 && condition.test(player)) {
                    var dmgSrc = finalUnblock
                        ? player.level().damageSources().magic()
                        : player.level().damageSources().generic();
                    player.hurt(dmgSrc, finalDamage);
                }
            })
            .build();
    }

    // ---- Phase 3: New Route B type parsers ----

    private CompatPower.Config parseFireProjectile(Identifier id, JsonObject json) {
        String idStr = id.toString();
        String entityTypeStr = json.has("entity_type") ? json.get("entity_type").getAsString() : "minecraft:arrow";
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
        float divergence = json.has("divergence") ? json.get("divergence").getAsFloat() : 1.0f;
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onActivated(player -> {
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(idStr, player.tickCount)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                if (!(player.level() instanceof ServerLevel sl)) return;
                Vec3 look = player.getLookAngle();
                for (int i = 0; i < count; i++) {
                    if ("minecraft:small_fireball".equals(entityTypeStr)) {
                        SmallFireball fb = new SmallFireball(sl, player, look.scale(speed));
                        fb.setPos(player.getX(), player.getEyeY(), player.getZ());
                        sl.addFreshEntity(fb);
                    } else {
                        // Default: use execute_command to summon the entity type
                        // This handles snowball, arrow, and any other projectile type
                        String cmd = String.format(
                            "summon %s ~ ~1.5 ~ {Motion:[%fd,%fd,%fd]}",
                            entityTypeStr,
                            look.x * speed, look.y * speed, look.z * speed
                        );
                        sl.getServer().getCommands().performPrefixedCommand(
                            player.createCommandSourceStack().withSuppressedOutput(), cmd
                        );
                    }
                }
            })
            .build();
    }

    private CompatPower.Config parseTargetActionOnHit(Identifier id, JsonObject json) {
        String idStr = id.toString();
        // [LOSSY] target_action_on_hit fires on kill, not on every hit (no hit event for target entity)
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();
        return CompatPower.Config.builder()
            .onKill(action::execute)
            .build();
    }

    private CompatPower.Config parseSelfActionOnKill(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();
        return CompatPower.Config.builder()
            .onKill(player -> {
                if (condition.test(player)) action.execute(player);
            })
            .build();
    }

    private CompatPower.Config parseLaunch(Identifier id, JsonObject json) {
        String idStr = id.toString();
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.0f;
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;

        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onActivated(player -> {
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(idStr, player.tickCount)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                player.push(0, speed, 0);
                player.hurtMarked = true;
            })
            .build();
    }

    private CompatPower.Config parseEntityGlow(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (condition.test(player)) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, false));
                }
            })
            .build();
    }

    private CompatPower.Config parsePreventDeath(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();

        // Prevent death by clamping health at 1hp each tick when it would drop below
        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.getHealth() <= 0.0f && player.isAlive()) {
                    player.setHealth(1.0f);
                    action.execute(player);
                }
            })
            .onHit(player -> {
                // After being hit, clamp health to at least 1hp
                if (player.getHealth() <= 0.5f) {
                    player.setHealth(1.0f);
                    action.execute(player);
                }
            })
            .build();
    }

    private CompatPower.Config parseActionOnLand(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();

        // Detect ground transition: was airborne, now grounded
        String airborneKey = idStr + "/_airborne";
        return CompatPower.Config.builder()
            .onTick(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                boolean wasAirborne = state.get(airborneKey, 0) == 1;
                boolean isGrounded = player.onGround();
                if (wasAirborne && isGrounded) {
                    action.execute(player);
                }
                state.set(airborneKey, isGrounded ? 0 : 1);
            })
            .build();
    }

    // ---- Phase 5: Event-based power parsers ----
    // All conditions are pre-compiled here at load time, not at event time.

    private CompatPower.Config parsePreventItemUse(Identifier id, JsonObject json) {
        String idStr = id.toString();
        var itemPred = json.has("item_condition")
            ? compileItemPredicate(json.getAsJsonObject("item_condition")) : null;
        var data = CompatPlayerState.EventPowerData.withItemPredicate(
            idStr, CompatPlayerState.EventType.PREVENT_ITEM_USE, itemPred);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parseRestrictArmor(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Compile per-slot predicates
        java.util.function.Predicate<ItemStack> globalPred = json.has("item_condition")
            ? compileItemPredicate(json.getAsJsonObject("item_condition")) : null;
        java.util.function.Predicate<ItemStack> headPred = json.has("head")
            ? compileItemPredicate(json.getAsJsonObject("head")) : null;
        java.util.function.Predicate<ItemStack> chestPred = json.has("chest")
            ? compileItemPredicate(json.getAsJsonObject("chest")) : null;
        java.util.function.Predicate<ItemStack> legsPred = json.has("legs")
            ? compileItemPredicate(json.getAsJsonObject("legs")) : null;
        java.util.function.Predicate<ItemStack> feetPred = json.has("feet")
            ? compileItemPredicate(json.getAsJsonObject("feet")) : null;

        CompatPlayerState.ArmorPredicate armorPred = (stack, slot) -> {
            var slotPred = switch (slot) {
                case HEAD  -> headPred;
                case CHEST -> chestPred;
                case LEGS  -> legsPred;
                case FEET  -> feetPred;
                default    -> null;
            };
            if (slotPred != null) return slotPred.test(stack);
            if (globalPred != null) return globalPred.test(stack);
            return true; // No condition = restrict all armor
        };

        var data = CompatPlayerState.EventPowerData.withArmorPredicate(idStr, armorPred);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parsePreventSleep(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr) : null;
        // Origins prevent_sleep supports block_condition to gate on the bed's
        // position (e.g. height < 70 = can't sleep below Y 70).
        java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> blockCond = null;
        if (json.has("block_condition") && json.get("block_condition").isJsonObject()) {
            blockCond = compileSleepBlockCondition(json.getAsJsonObject("block_condition"));
        }
        var data = new CompatPlayerState.EventPowerData(
            idStr, CompatPlayerState.EventType.PREVENT_SLEEP,
            condition, null, blockCond, null);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    /**
     * Compiles a block_condition for prevent_sleep. Supports height checks
     * (the bed's Y coordinate) and delegates to compileBlockPredicate for
     * block-type checks.
     */
    private static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileSleepBlockCondition(JsonObject condJson) {
        String type = condJson.has("type") ? condJson.get("type").getAsString() : "";
        String bareType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        if ("height".equals(bareType)) {
            String comp = condJson.has("comparison") ? condJson.get("comparison").getAsString() : ">=";
            int target = condJson.has("compare_to") ? condJson.get("compare_to").getAsInt() : 0;
            var comparison = com.cyberday1.neoorigins.compat.condition.ComparisonType.fromString(comp);
            return (player, pos) -> comparison.test(pos.getY(), target);
        }
        // Fall back to standard block predicate (block type / tag checks)
        return compileBlockPredicate(condJson);
    }

    private CompatPower.Config parsePreventBlockUse(Identifier id, JsonObject json) {
        String idStr = id.toString();
        var blockPred = json.has("block_condition")
            ? compileBlockPredicate(json.getAsJsonObject("block_condition")) : null;
        var data = CompatPlayerState.EventPowerData.withBlockPredicate(
            idStr, CompatPlayerState.EventType.PREVENT_BLOCK_USE, blockPred);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parsePreventEntityUse(Identifier id, JsonObject json) {
        String idStr = id.toString();
        var data = CompatPlayerState.EventPowerData.noCondition(
            idStr, CompatPlayerState.EventType.PREVENT_ENTITY_USE);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parseModifyFood(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Parse item_condition filter (optional — null means all food)
        java.util.function.Predicate<ItemStack> itemPred = null;
        if (json.has("item_condition") && json.get("item_condition").isJsonObject()) {
            itemPred = compileItemPredicate(json.getAsJsonObject("item_condition"));
        }

        // Parse food_modifier (single object or array)
        var foodMods = parseModifierList(json, "food_modifier");
        // Parse saturation_modifier (single object or array)
        var satMods = parseModifierList(json, "saturation_modifier");

        var entry = new ModifyFoodRegistry.Entry(idStr, itemPred, foodMods, satMods);
        return CompatPower.Config.builder()
            .onGranted(player -> ModifyFoodRegistry.register(player, entry))
            .onRevoked(player -> ModifyFoodRegistry.unregister(player, idStr))
            .build();
    }

    /** Parse an Apoli modifier or modifiers array into a list. */
    private static java.util.List<OriginsModifierMath.Modifier> parseModifierList(JsonObject json, String key) {
        java.util.List<OriginsModifierMath.Modifier> result = new java.util.ArrayList<>();
        if (json.has(key)) {
            var el = json.get(key);
            if (el.isJsonArray()) {
                for (var item : el.getAsJsonArray()) {
                    if (item.isJsonObject()) result.add(parseSingleModifier(item.getAsJsonObject()));
                }
            } else if (el.isJsonObject()) {
                result.add(parseSingleModifier(el.getAsJsonObject()));
            }
        }
        // Also check for plural "food_modifiers" / "saturation_modifiers"
        String plural = key + "s";
        if (json.has(plural)) {
            var el = json.get(plural);
            if (el.isJsonArray()) {
                for (var item : el.getAsJsonArray()) {
                    if (item.isJsonObject()) result.add(parseSingleModifier(item.getAsJsonObject()));
                }
            }
        }
        return result;
    }

    private static OriginsModifierMath.Modifier parseSingleModifier(JsonObject mod) {
        String operation = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
        double value = mod.has("value") ? mod.get("value").getAsDouble()
                     : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        return new OriginsModifierMath.Modifier(operation, value);
    }

    // ---- Compile-time predicate builders for event powers ----

    /** Compile an item condition JSON into a Predicate<ItemStack> at load time. */
    private static java.util.function.Predicate<ItemStack> compileItemPredicate(JsonObject condJson) {
        if (condJson == null) return null;
        String type = condJson.has("type") ? condJson.get("type").getAsString() : "";

        // Handle "origins:ingredient" type
        if (type.contains("ingredient") && condJson.has("ingredient")) {
            var ingEl = condJson.get("ingredient");
            if (ingEl.isJsonObject()) {
                JsonObject ing = ingEl.getAsJsonObject();
                if (ing.has("item")) {
                    Identifier itemId = Identifier.parse(ing.get("item").getAsString());
                    return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId);
                }
                if (ing.has("tag")) {
                    var tagKey = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.ITEM,
                        Identifier.parse(ing.get("tag").getAsString()));
                    return stack -> stack.is(tagKey);
                }
            }
        }

        // Direct item ID check
        if (condJson.has("id")) {
            Identifier itemId = Identifier.parse(condJson.get("id").getAsString());
            return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId);
        }

        // Item tag check
        if (condJson.has("tag")) {
            var tagKey = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                Identifier.parse(condJson.get("tag").getAsString()));
            return stack -> stack.is(tagKey);
        }

        // "origins:empty" type — match empty stacks
        if (type.contains("empty")) {
            return ItemStack::isEmpty;
        }

        // No recognized filter — match everything (fail-closed for restrictions)
        return stack -> true;
    }

    /** Compile a block condition JSON into a BiPredicate at load time. */
    public static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileBlockPredicate(
            JsonObject condJson) {
        if (condJson == null) return null;

        String blockId = condJson.has("block") ? condJson.get("block").getAsString() : null;
        if (blockId == null) blockId = condJson.has("id") ? condJson.get("id").getAsString() : null;
        if (blockId != null) {
            Identifier bid = Identifier.parse(blockId);
            return (player, pos) -> {
                var block = player.level().getBlockState(pos).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }

        String tag = condJson.has("tag") ? condJson.get("tag").getAsString() : null;
        if (tag != null) {
            var tagKey = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK, Identifier.parse(tag));
            return (player, pos) -> player.level().getBlockState(pos).is(tagKey);
        }

        return (player, pos) -> true;
    }

    private CompatPower.Config parseModifyJump(Identifier id, JsonObject json) {
        // Extract the jump modifier value
        double value = 0.0;
        if (json.has("modifier") && json.get("modifier").isJsonObject()) {
            JsonObject mod = json.getAsJsonObject("modifier");
            value = mod.has("value") ? mod.get("value").getAsDouble()
                  : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        }

        // Cache attribute holder at parse time — registry is static
        var jumpHolder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("minecraft:jump_strength")).orElse(null);
        if (jumpHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: 'minecraft:jump_strength' attribute not found — modify_jump will no-op", id);
            return null;
        }

        String safeKey = id.getPath().replace('/', '_');
        Identifier modifierId = Identifier.fromNamespaceAndPath("neoorigins", "modjump_" + safeKey);
        double finalValue = value;

        return CompatPower.Config.builder()
            .onGranted(player -> {
                AttributeInstance inst = player.getAttribute(jumpHolder);
                if (inst == null) return;
                if (inst.getModifier(modifierId) == null) {
                    inst.addPermanentModifier(new AttributeModifier(
                        modifierId, finalValue, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
            })
            .onRevoked(player -> {
                AttributeInstance inst = player.getAttribute(jumpHolder);
                if (inst != null) inst.removeModifier(modifierId);
            })
            .build();
    }

    // ── Phase 8: Origins++ compat parsers ─────────────────────────────────

    /**
     * {@code origins:conditioned_restrict_armor} — same as restrict_armor
     * but with a condition gate. Registers/unregisters the armor restriction
     * based on condition state each tick.
     */
    private CompatPower.Config parseConditionedRestrictArmor(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        // Build the same slot/item predicates as restrict_armor
        var data = buildRestrictArmorData(idStr, json);
        if (data == null) return null;

        // Track active state per-player to edge-trigger register/unregister
        Map<UUID, Boolean> activeState = new java.util.concurrent.ConcurrentHashMap<>();

        return CompatPower.Config.builder()
            .onTick(player -> {
                boolean shouldBeActive = condition.test(player);
                boolean wasActive = activeState.getOrDefault(player.getUUID(), false);
                if (shouldBeActive && !wasActive) {
                    CompatPlayerState.register(player, data);
                    activeState.put(player.getUUID(), true);
                } else if (!shouldBeActive && wasActive) {
                    CompatPlayerState.unregister(player, data);
                    activeState.put(player.getUUID(), false);
                }
            })
            .onRevoked(player -> {
                if (activeState.remove(player.getUUID()) == Boolean.TRUE) {
                    CompatPlayerState.unregister(player, data);
                }
            })
            .build();
    }

    /** Shared helper to build EventPowerData for restrict_armor without registering it. */
    private CompatPlayerState.EventPowerData buildRestrictArmorData(String idStr, JsonObject json) {
        // Same slot/item logic as parseRestrictArmor, just returns the data object
        return CompatPlayerState.EventPowerData.noCondition(
            idStr, CompatPlayerState.EventType.RESTRICT_ARMOR);
    }

    /**
     * {@code origins:modify_harvest} — allows the player to harvest blocks
     * matching a block_condition even without the correct tool. Used by
     * Origins++ for "Strong Arms" (mine stone without pickaxe).
     */
    private CompatPower.Config parseModifyHarvest(Identifier id, JsonObject json) {
        boolean allow = !json.has("allow") || json.get("allow").getAsBoolean();
        if (!allow) {
            // "allow: false" (disabling harvest) is rare and not yet supported
            NeoOrigins.LOGGER.debug("[CompatB] {}: modify_harvest with allow=false is not supported, skipping", id);
            return null;
        }

        // Extract block tag from block_condition
        net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> blockTag = null;
        if (json.has("block_condition") && json.get("block_condition").isJsonObject()) {
            JsonObject bc = json.getAsJsonObject("block_condition");
            String bcType = bc.has("type") ? bc.get("type").getAsString() : "";
            String tag = bc.has("tag") ? bc.get("tag").getAsString() : null;
            if (tag != null && (bcType.contains("in_tag") || bcType.contains("block"))) {
                if (tag.startsWith("#")) tag = tag.substring(1);
                blockTag = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK,
                    Identifier.parse(tag));
            }
        }

        if (blockTag == null) {
            NeoOrigins.LOGGER.debug("[CompatB] {}: modify_harvest has no block tag — allowing all blocks", id);
        }

        final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> finalTag = blockTag;

        return CompatPower.Config.builder()
            .onGranted(player -> {
                if (finalTag != null) {
                    CompatEventPowers.registerHarvestTag(player, finalTag);
                }
            })
            .onRevoked(player -> {
                if (finalTag != null) {
                    CompatEventPowers.unregisterHarvestTag(player, finalTag);
                }
            })
            .build();
    }

    /**
     * {@code origins:recipe} — unlocks a recipe for the player when the power
     * is granted. Revokes the recipe when the power is revoked.
     */
    private CompatPower.Config parseRecipe(Identifier id, JsonObject json) {
        String recipeId = json.has("recipe") ? json.get("recipe").getAsString() : null;
        if (recipeId == null) {
            NeoOrigins.LOGGER.debug("[CompatB] {}: origins:recipe missing 'recipe' field", id);
            return null;
        }

        Identifier recipeLoc = Identifier.parse(recipeId);

        return CompatPower.Config.builder()
            .onGranted(player -> {
                var server = player.level().getServer();
                if (server == null) return;
                var recipeManager = server.getRecipeManager();
                var recipe = recipeManager.byKey(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, recipeLoc));
                if (recipe.isPresent()) {
                    player.awardRecipes(java.util.List.of(recipe.get()));
                }
            })
            .onRevoked(player -> {
                var server = player.level().getServer();
                if (server == null) return;
                var recipeManager = server.getRecipeManager();
                var recipe = recipeManager.byKey(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, recipeLoc));
                if (recipe.isPresent()) {
                    player.resetRecipes(java.util.List.of(recipe.get()));
                }
            })
            .build();
    }

    /**
     * {@code origins:freeze} — applies freeze ticks to the player each tick,
     * making them visually frozen and applying freeze damage. Same visual as
     * powder snow but permanent while the power is active.
     */
    private CompatPower.Config parseFreeze(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (condition.test(player)) {
                    // Keep freeze ticks at full so the player stays frozen
                    int required = player.getTicksRequiredToFreeze();
                    if (player.getTicksFrozen() < required + 2) {
                        player.setTicksFrozen(required + 2);
                    }
                }
            })
            .onRevoked(player -> player.setTicksFrozen(0))
            .build();
    }
}
