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
        "origins:modify_jump",          "apace:modify_jump"
    );

    private static final Set<String> MULTIPLE_META_KEYS = OriginsMultipleExpander.META_KEYS;

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

        // Inline-expand any origins:multiple entries so sub-power JSONs are accessible.
        Map<Identifier, JsonObject> expanded = inlineExpand(data);

        Map<Identifier, PowerHolder<?>> injected = new HashMap<>();
        // Track new synthetic IDs to add to MULTIPLE_EXPANSION_MAP
        Map<Identifier, List<Identifier>> newExpansions = new HashMap<>();

        for (var entry : expanded.entrySet()) {
            Identifier id   = entry.getKey();
            JsonObject json = entry.getValue();
            String type = OriginsFormatDetector.getType(json);

            if (!ROUTE_B_TYPES.contains(type)) continue;
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
                injected.put(id, new PowerHolder<>(CompatPower.INSTANCE, config, powerName, powerDesc));
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

        PowerDataManager.INSTANCE.injectExternalPowers(injected);
        NeoOrigins.LOGGER.info("[CompatB] Injected {} Route B powers", injected.size());
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
            default -> null;
        };
    }

    private CompatPower.Config parseActiveSelf(Identifier id, JsonObject json) {
        String idStr = id.toString();
        JsonObject actionJson = json.has("entity_action") ? json.getAsJsonObject("entity_action")
            : json.has("action") ? json.getAsJsonObject("action") : null;
        if (actionJson == null) return null;

        EntityAction action = ActionParser.parse(actionJson, idStr);
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;

        return CompatPower.Config.builder()
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
        EntityCondition condition = json.has("entity_condition")
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

        return CompatPower.Config.builder()
            .onGranted(player -> player.getData(CompatAttachments.resourceState()).set(key, startValue))
            .onTick(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                if (player.level().getServer() != null && (player.level().getServer().getTickCount() + offset) % interval == 0) {
                    tickAction.execute(player);
                }
                int cur = state.get(key, startValue);
                if (cur <= min) minAction.execute(player);
                if (cur >= max) maxAction.execute(player);
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
        if (!json.has("attribute")) return null;

        Identifier rawAttrIdent = Identifier.parse(json.get("attribute").getAsString());
        // Normalize legacy "generic." prefix (removed in MC 1.21.2+)
        Identifier attrIdent = rawAttrIdent.getPath().startsWith("generic.")
            ? Identifier.fromNamespaceAndPath(rawAttrIdent.getNamespace(),
                rawAttrIdent.getPath().substring("generic.".length()))
            : rawAttrIdent;

        // Cache attribute holder at parse time — registry is static
        var attrHolder = BuiltInRegistries.ATTRIBUTE.get(attrIdent).orElse(null);
        if (attrHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown attribute '{}' (raw: '{}') — power will no-op",
                idStr, attrIdent, rawAttrIdent);
            return null;
        }

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

        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

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

    private CompatPower.Config parseFireProjectile(Identifier id, JsonObject json) {
        String idStr = id.toString();
        String entityTypeStr = json.has("entity_type") ? json.get("entity_type").getAsString() : "minecraft:arrow";
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
        float divergence = json.has("divergence") ? json.get("divergence").getAsFloat() : 1.0f;
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        return CompatPower.Config.builder()
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
        var data = CompatPlayerState.EventPowerData.withEntityCondition(
            idStr, CompatPlayerState.EventType.PREVENT_SLEEP, condition);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
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
    private static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileBlockPredicate(
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
}
