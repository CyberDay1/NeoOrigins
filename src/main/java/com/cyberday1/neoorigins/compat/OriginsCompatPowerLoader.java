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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

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
        "origins:active_self",          "apace:active_self",
        "origins:action_over_time",     "apace:action_over_time",
        "origins:action_on_callback",   "apace:action_on_callback",
        "origins:resource",             "apace:resource",
        "origins:toggle",               "apace:toggle",
        "origins:conditioned_attribute","apace:conditioned_attribute",
        "origins:conditioned_status_effect", "apace:conditioned_status_effect",
        "origins:action_on_being_hit",  "apace:action_on_being_hit",
        "origins:self_action_when_hit", "apace:self_action_when_hit",
        "origins:action_on_hit",        "apace:action_on_hit"
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
                NeoOrigins.LOGGER.error("OriginsCompatB: Error reading {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager rm, ProfilerFiller profiler) {
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
                NeoOrigins.LOGGER.debug("OriginsCompatB: loaded {} ({})", id, type);

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
                NeoOrigins.LOGGER.warn("OriginsCompatB: Failed to load {} ({}): {}", id, type, reason);
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
        NeoOrigins.LOGGER.info("OriginsCompatB: Injected {} Route B powers", injected.size());
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
                 "origins:action_on_hit",              "apace:action_on_hit"              -> parseSelfActionWhenHit(id, json);
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
        EntityAction respawnAction = json.has("respawn_action")
            ? ActionParser.parse(json.getAsJsonObject("respawn_action"), idStr) : EntityAction.noop();
        EntityAction addedAction   = json.has("added_action")
            ? ActionParser.parse(json.getAsJsonObject("added_action"), idStr)   : EntityAction.noop();
        EntityAction removedAction = json.has("removed_action")
            ? ActionParser.parse(json.getAsJsonObject("removed_action"), idStr) : EntityAction.noop();

        return CompatPower.Config.builder()
            .onGranted(addedAction::execute)
            .onRevoked(removedAction::execute)
            .onRespawn(respawnAction::execute)
            .build();
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

        Identifier attrIdent = Identifier.parse(json.get("attribute").getAsString());

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
                var attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrIdent);
                if (attrOpt.isEmpty()) return;
                AttributeInstance inst = player.getAttribute(attrOpt.get());
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
                var attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrIdent);
                if (attrOpt.isEmpty()) return;
                AttributeInstance inst = player.getAttribute(attrOpt.get());
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
        if (effectId == null) return null;

        EntityCondition condition = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), idStr)
            : EntityCondition.alwaysTrue();

        Identifier effId    = Identifier.parse(effectId);
        int  finalAmp       = amplifier;
        boolean finalAmb    = ambient;
        boolean finalPart   = particles;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!condition.test(player)) return;
                BuiltInRegistries.MOB_EFFECT.get(effId).ifPresent(holder -> {
                    var existing = player.getEffect(holder);
                    // Re-apply at 200t duration if missing or about to expire (<100t).
                    if (existing == null || existing.getDuration() < 100) {
                        player.addEffect(new MobEffectInstance(
                            holder, 200, finalAmp, finalAmb, finalPart, true));
                    }
                });
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
}
