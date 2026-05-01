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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.SmallFireball;
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
public class OriginsCompatPowerLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

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
        "origins:modify_xp_gain",       "apace:modify_xp_gain"
    );

    private static final Set<String> MULTIPLE_META_KEYS = OriginsMultipleExpander.META_KEYS;

    private static final FileToIdConverter FILE_CONVERTER  = FileToIdConverter.json("origins/powers");
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    // ---- SimplePreparableReloadListener ----

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER,  rm, map);
        scanConverter(COMPAT_CONVERTER, rm, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager rm,
                                Map<ResourceLocation, JsonElement> map) {
        for (var entry : converter.listMatchingResources(rm).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id     = converter.fileToId(fileId);
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
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager rm, ProfilerFiller profiler) {
        // Clear event power state from the previous reload cycle
        CompatPlayerState.clearAll();
        ModifyCraftingRegistry.clearAll();
        NumericModifierRegistry.clearAll();
        CompatAttachments.clearResourceMeta();

        // Inline-expand any origins:multiple entries so sub-power JSONs are accessible.
        Map<ResourceLocation, JsonObject> expanded = inlineExpand(data);

        Map<ResourceLocation, PowerHolder<?>> injected = new HashMap<>();
        // Track new synthetic IDs to add to MULTIPLE_EXPANSION_MAP
        Map<ResourceLocation, List<ResourceLocation>> newExpansions = new HashMap<>();

        for (var entry : expanded.entrySet()) {
            ResourceLocation id   = entry.getKey();
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
                    ResourceLocation parentId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), parentPath);
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
            List<ResourceLocation> existing = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP
                .getOrDefault(entry.getKey(), List.of());
            List<ResourceLocation> merged = new ArrayList<>(existing);
            for (ResourceLocation newId : entry.getValue()) {
                if (!merged.contains(newId)) merged.add(newId);
            }
            OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.put(entry.getKey(),
                Collections.unmodifiableList(merged));
        }

        // Inject synthetic powers for well-known Origins built-in power IDs.
        // These are referenced by addon packs as bare IDs (e.g. "origins:elytra")
        // with no JSON file — the original Origins mod hardcodes them.
        injectWellKnownPowers(injected);

        PowerDataManager.INSTANCE.injectExternalPowers(injected);
        NeoOrigins.LOGGER.info("[CompatB] Injected {} Route B powers", injected.size());
    }

    /**
     * Inline-expand origins:multiple entries in the raw data map.
     * Returns a flat map of id -> JsonObject covering both direct powers and sub-powers.
     * Does NOT call OriginsMultipleExpander (avoids touching its state twice).
     */
    /**
     * Registers synthetic PowerHolders for well-known Origins built-in power IDs
     * that addon packs reference by ID without providing a JSON file.
     */
    private void injectWellKnownPowers(Map<ResourceLocation, PowerHolder<?>> injected) {
        // Map of origins:id -> NeoOrigins JSON equivalent
        Map<String, java.util.function.Supplier<com.google.gson.JsonObject>> WELL_KNOWN = Map.ofEntries(
            Map.entry("origins:elytra",              () -> json("neoorigins:natural_glide")),
            Map.entry("origins:fire_immunity",       () -> json("neoorigins:prevent_action", "action", "fire")),
            Map.entry("origins:fresh_air",           () -> json("neoorigins:prevent_action", "action", "sleep")),
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
            Map.entry("origins:translucent",         () -> json("neoorigins:model_color", "red", 1.0, "green", 1.0, "blue", 1.0, "alpha", 0.5))
        );

        for (var entry : WELL_KNOWN.entrySet()) {
            ResourceLocation id = ResourceLocation.parse(entry.getKey());
            // Skip if already loaded via JSON or Route B
            if (PowerDataManager.INSTANCE.hasPower(id) || injected.containsKey(id)) continue;
            try {
                com.google.gson.JsonObject powerJson = entry.getValue().get();
                String typeStr = powerJson.get("type").getAsString();
                ResourceLocation typeId = ResourceLocation.parse(typeStr);
                com.cyberday1.neoorigins.api.power.PowerType<?> powerType =
                    com.cyberday1.neoorigins.power.registry.PowerTypes.get(typeId);
                if (powerType != null) {
                    // Route A — parse via native codec
                    injectViaNativeCodec(id, powerType, powerJson, injected);
                    NeoOrigins.LOGGER.debug("[CompatSynth] Registered well-known power {} -> {}", id, typeStr);
                } else {
                    // Fall back to Route B parsing
                    CompatPower.Config config = parseRouteB(id, typeStr, powerJson);
                    if (config != null) {
                        injected.put(id, new PowerHolder<>(id, CompatPower.INSTANCE, config, net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty()));
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
            ResourceLocation id, com.cyberday1.neoorigins.api.power.PowerType<C> type,
            com.google.gson.JsonObject json, Map<ResourceLocation, PowerHolder<?>> target) {
        com.google.gson.JsonObject configJson = json.deepCopy();
        configJson.addProperty("_power_id", id.toString());
        type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, configJson)
            .resultOrPartial(err -> NeoOrigins.LOGGER.warn("[CompatSynth] codec error for {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(id, type, config, net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty())));
    }

    private static com.google.gson.JsonObject json(String type) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("type", type);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, String v1) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, double v1, String k2, double v2, String k3, double v3, String k4, double v4) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1); o.addProperty(k2, v2);
        o.addProperty(k3, v3); o.addProperty(k4, v4);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, String v1, String k2, double v2, String k3, String v3) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1); o.addProperty(k2, v2); o.addProperty(k3, v3);
        return o;
    }

    private Map<ResourceLocation, JsonObject> inlineExpand(Map<ResourceLocation, JsonElement> data) {
        Map<ResourceLocation, JsonObject> result = new HashMap<>();
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

    private void expandMultiple(ResourceLocation parentId, JsonObject json, Map<ResourceLocation, JsonObject> out) {
        for (var subEntry : json.entrySet()) {
            if (MULTIPLE_META_KEYS.contains(subEntry.getKey())) continue;
            if (!subEntry.getValue().isJsonObject()) continue;
            JsonObject subJson = subEntry.getValue().getAsJsonObject();
            ResourceLocation syntheticId = ResourceLocation.fromNamespaceAndPath(
                parentId.getNamespace(), parentId.getPath() + "/" + subEntry.getKey()
            );
            String subType = OriginsFormatDetector.getType(subJson);
            if ("origins:multiple".equals(subType) || "apace:multiple".equals(subType)) {
                expandMultiple(syntheticId, subJson, out); // recurse
            } else {
                out.put(syntheticId, subJson);
            }
        }
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

    private CompatPower.Config parseRouteB(ResourceLocation id, String type, JsonObject json) {
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
            case "origins:prevent_sprinting",          "apace:prevent_sprinting"          -> parsePreventSprinting(id, json);
            case "origins:modify_crafting",            "apace:modify_crafting"            -> parseModifyCrafting(id, json);
            case "origins:modify_lava_speed",          "apace:modify_lava_speed"          -> parseNumericModifier(id, json, NumericModifierRegistry.Kind.LAVA_SPEED);
            case "origins:modify_xp_gain",             "apace:modify_xp_gain"             -> parseNumericModifier(id, json, NumericModifierRegistry.Kind.XP_GAIN);
            // Conditioned modify_damage_taken — only dispatched here when a condition is present.
            case "origins:modify_damage_taken",        "apace:modify_damage_taken"        -> parseConditionedModifyDamageTaken(id, json);
            default -> null;
        };
    }

    private static boolean isModifyDamageTakenType(String type) {
        return "origins:modify_damage_taken".equals(type) || "apace:modify_damage_taken".equals(type);
    }

    private CompatPower.Config parseConditionedModifyDamageTaken(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseActiveSelf(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseActionOverTime(ResourceLocation id, JsonObject json) {
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
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : json.has("entity_condition")
                ? ConditionParser.parse(json.getAsJsonObject("entity_condition"), idStr)
                : EntityCondition.alwaysTrue();

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

    private CompatPower.Config parseActionOnCallback(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseResource(ResourceLocation id, JsonObject json) {
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

        // HUD display metadata — parse from hud_render block or fall back to defaults.
        String label = "Resource";
        int color = 0xFF55AAFF;
        if (json.has("hud_render") && json.get("hud_render").isJsonObject()) {
            JsonObject hud = json.getAsJsonObject("hud_render");
            if (hud.has("bar_index")) {
                // Apoli hud_render has bar_index, sprite_location, condition — we
                // use a flat color bar, so just derive a label from the power ID.
            }
        }
        // Derive a human-readable label from the power ID path segment.
        String path = id.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        label = path.replace('_', ' ');
        // Capitalize first letter of each word
        StringBuilder sb = new StringBuilder();
        for (String word : label.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        label = sb.toString();

        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(min, max, label, color));

        return CompatPower.Config.builder()
            .onGranted(player -> {
                player.getData(CompatAttachments.resourceState()).set(key, startValue);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onRevoked(player -> {
                player.getData(CompatAttachments.resourceState()).set(key, 0);
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

    private CompatPower.Config parseToggle(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseConditionedAttribute(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        if (!json.has("attribute")) {
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "missing 'attribute' field in JSON");
            return null;
        }

        ResourceLocation rawAttrIdent = ResourceLocation.parse(json.get("attribute").getAsString());
        // Normalize legacy "generic." prefix (removed in MC 1.21.2+)
        ResourceLocation attrIdent = rawAttrIdent.getPath().startsWith("generic.")
            ? ResourceLocation.fromNamespaceAndPath(rawAttrIdent.getNamespace(),
                rawAttrIdent.getPath().substring("generic.".length()))
            : rawAttrIdent;

        // Cache attribute holder at parse time
        var attrOpt = BuiltInRegistries.ATTRIBUTE.getOptional(attrIdent);
        if (attrOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown attribute '{}' (raw: '{}') — power will no-op",
                idStr, attrIdent, rawAttrIdent);
            // Surface the bad attribute id in the compat log too — pack
            // authors usually only check that file when debugging, not the
            // main game log. Without the id they only see "no handler
            // produced a config" which is unactionable.
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "unknown attribute '" + rawAttrIdent + "' — pack-side fix: confirm attribute exists in 1.21.1");
            return null;
        }
        var attrHolder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attrOpt.get());

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
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("neoorigins", "condattr_" + safeKey);

        return CompatPower.Config.builder()
            .onTick(player -> {
                AttributeInstance inst = player.getAttribute(attrHolder);
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
                AttributeInstance inst = player.getAttribute(attrHolder);
                if (inst != null) inst.removeModifier(modifierId);
            })
            .build();
    }

    private CompatPower.Config parseConditionedStatusEffect(ResourceLocation id, JsonObject json) {
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
        if (effectId == null) return null;

        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        // Cache mob effect holder at parse time
        ResourceLocation effectIdent = ResourceLocation.parse(effectId);
        var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effectIdent);
        if (effectOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown mob effect '{}' — power will no-op", idStr, effectIdent);
            return null;
        }
        var effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effectOpt.get());

        int  finalAmp       = amplifier;
        boolean finalAmb    = ambient;
        boolean finalPart   = particles;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!condition.test(player)) return;
                var existing = player.getEffect(effectHolder);
                // Re-apply at 200t duration if missing or about to expire (<100t).
                if (existing == null || existing.getDuration() < 100) {
                    player.addEffect(new MobEffectInstance(
                        effectHolder, 200, finalAmp, finalAmb, finalPart, true));
                }
            })
            .build();
    }

    private CompatPower.Config parseSelfActionWhenHit(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();
        // bientity_action is skipped (requires attacker reference).
        return CompatPower.Config.builder()
            .onHit(action::execute)
            .build();
    }

    private CompatPower.Config parseDamageOverTime(ResourceLocation id, JsonObject json) {
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

        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

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

    private CompatPower.Config parseFireProjectile(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseTargetActionOnHit(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        // [LOSSY] target_action_on_hit fires on kill, not on every hit (no hit event for target entity)
        EntityAction action = json.has("entity_action")
            ? ActionParser.parse(json.getAsJsonObject("entity_action"), idStr)
            : EntityAction.noop();
        return CompatPower.Config.builder()
            .onKill(action::execute)
            .build();
    }

    private CompatPower.Config parseSelfActionOnKill(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseLaunch(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseEntityGlow(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventDeath(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseActionOnLand(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventItemUse(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseRestrictArmor(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventSleep(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr) : null;
        var data = CompatPlayerState.EventPowerData.withEntityCondition(
            idStr, CompatPlayerState.EventType.PREVENT_SLEEP, condition);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parsePreventBlockUse(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventEntityUse(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        var data = CompatPlayerState.EventPowerData.noCondition(
            idStr, CompatPlayerState.EventType.PREVENT_ENTITY_USE);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    /**
     * Shared parser for {@code modify_lava_speed} and {@code modify_xp_gain}
     * — both shape-identical Apoli verbs of the form
     * {@code { "modifier": { "operation": ..., "value": ... } }}. Registers
     * the resolved numeric entry against the player's UUID so the consumer
     * (mixin for lava-speed, event handler for xp-gain) can apply it.
     */
    private CompatPower.Config parseNumericModifier(ResourceLocation id, JsonObject json,
                                                     NumericModifierRegistry.Kind kind) {
        String idStr = id.toString();
        if (!json.has("modifier") || !json.get("modifier").isJsonObject()) {
            NeoOrigins.LOGGER.warn("[CompatB] {} '{}' missing modifier object — skipped", kind, id);
            return null;
        }
        JsonObject mod = json.getAsJsonObject("modifier");
        String operation = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
        double value = mod.has("value") ? mod.get("value").getAsDouble() : 0.0;
        return CompatPower.Config.builder()
            .onGranted(player -> NumericModifierRegistry.register(player, kind, idStr, operation, value))
            .onRevoked(player -> NumericModifierRegistry.unregister(player, kind, idStr))
            .build();
    }

    /**
     * {@code modify_crafting} — overrides the result of a specific recipe
     * for players holding this power. Apoli authors use it to swap potion
     * outputs, give Origins-exclusive equipment, etc.
     *
     * <p>Apoli shape:
     * <pre>{@code
     * { "type": "origins:modify_crafting",
     *   "recipe": "ns:my_recipe",
     *   "result": { "item": "minecraft:potion",
     *               "tag": "{Potion:\"minecraft:water_breathing\"}" }
     * }
     * }</pre>
     *
     * <p>Implementation: registers a {@link ModifyCraftingRegistry.Entry}
     * keyed by player + recipe id. {@code CompatEventPowers.onItemCrafted}
     * matches the crafted recipe against the registry and swaps the result
     * stack at craft time.
     */
    private CompatPower.Config parseModifyCrafting(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        if (!json.has("recipe") || !json.has("result")) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' missing 'recipe' or 'result' — skipped", id);
            return null;
        }
        ResourceLocation recipeId = ResourceLocation.tryParse(json.get("recipe").getAsString());
        if (recipeId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' has malformed recipe id — skipped", id);
            return null;
        }
        JsonObject result = json.getAsJsonObject("result");
        String resultItemStr = result.has("item") ? result.get("item").getAsString() : null;
        if (resultItemStr == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' result missing 'item' — skipped", id);
            return null;
        }
        ResourceLocation resultItem = ResourceLocation.tryParse(resultItemStr);
        if (resultItem == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' result item '{}' is malformed — skipped", id, resultItemStr);
            return null;
        }
        int resultCount = result.has("amount") ? result.get("amount").getAsInt()
                        : result.has("count")  ? result.get("count").getAsInt()
                        : 1;
        String resultTag = result.has("tag") ? result.get("tag").getAsString() : "";

        var entry = new ModifyCraftingRegistry.Entry(idStr, recipeId, resultItem, resultCount, resultTag);
        return CompatPower.Config.builder()
            .onGranted(player -> ModifyCraftingRegistry.register(player, entry))
            .onRevoked(player -> ModifyCraftingRegistry.unregister(player, idStr))
            .build();
    }

    /**
     * {@code prevent_sprinting} — clamps {@link net.minecraft.world.entity.Entity#setSprinting(boolean)}
     * back to false every tick the holder's optional {@code condition} is met.
     *
     * <p>Implemented as a per-tick handler rather than via an event because the
     * sprint flag is set by client input and synced via packet; the cheapest
     * server-side veto is to re-clear it once per tick. This causes one frame
     * of visible sprint before the client gets the corrective sync, which is
     * acceptable parity with Apoli's behaviour on Fabric.
     */
    private CompatPower.Config parsePreventSprinting(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();
        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!player.isSprinting()) return;
                if (condition.test(player)) player.setSprinting(false);
            })
            .build();
    }

    private CompatPower.Config parseModifyFood(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        var data = CompatPlayerState.EventPowerData.noCondition(
            idStr, CompatPlayerState.EventType.MODIFY_FOOD);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
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
                    ResourceLocation itemId = ResourceLocation.parse(ing.get("item").getAsString());
                    return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId);
                }
                if (ing.has("tag")) {
                    var tagKey = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.ITEM,
                        ResourceLocation.parse(ing.get("tag").getAsString()));
                    return stack -> stack.is(tagKey);
                }
            }
        }

        // Direct item ID check
        if (condJson.has("id")) {
            ResourceLocation itemId = ResourceLocation.parse(condJson.get("id").getAsString());
            return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId);
        }

        // Item tag check
        if (condJson.has("tag")) {
            var tagKey = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.parse(condJson.get("tag").getAsString()));
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
            ResourceLocation bid = ResourceLocation.parse(blockId);
            return (player, pos) -> {
                var block = player.level().getBlockState(pos).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }

        String tag = condJson.has("tag") ? condJson.get("tag").getAsString() : null;
        if (tag != null) {
            var tagKey = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(tag));
            return (player, pos) -> player.level().getBlockState(pos).is(tagKey);
        }

        return (player, pos) -> true;
    }

    private CompatPower.Config parseModifyJump(ResourceLocation id, JsonObject json) {
        // Extract the jump modifier value
        double value = 0.0;
        if (json.has("modifier") && json.get("modifier").isJsonObject()) {
            JsonObject mod = json.getAsJsonObject("modifier");
            value = mod.has("value") ? mod.get("value").getAsDouble()
                  : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        }

        // Cache attribute holder at parse time
        ResourceLocation jumpAttrId = ResourceLocation.parse("minecraft:generic.jump_strength");
        var jumpOpt = BuiltInRegistries.ATTRIBUTE.getOptional(jumpAttrId);
        if (jumpOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: jump_strength attribute '{}' not found — modify_jump power will no-op",
                id, jumpAttrId);
            return null;
        }
        var jumpHolder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(jumpOpt.get());

        String safeKey = id.getPath().replace('/', '_');
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("neoorigins", "modjump_" + safeKey);
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
}
