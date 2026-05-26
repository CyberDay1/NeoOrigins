package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatWarningCollector;
import com.cyberday1.neoorigins.service.ActionContextHolder;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Parses Apoli {@code bientity_action} JSON into {@link BiEntityAction}.
 *
 * <p>Most concrete entity-actions in NeoOrigins compat operate on a
 * {@link ServerPlayer}, not a generic {@link LivingEntity}. To remain
 * compatible with that surface area while still expressing the Apoli
 * (actor, target) shape, this parser:
 *
 * <ul>
 *   <li>Wraps {@code actor_action} as: publish target to
 *       {@link ActionContextHolder}, then execute inner EntityAction on
 *       the actor.</li>
 *   <li>Wraps {@code target_action} as: if target is a ServerPlayer, run
 *       the inner EntityAction on it (with actor in context). Otherwise
 *       short-circuit — most EntityAction sub-types require a player.</li>
 *   <li>Implements {@code damage}, {@code chance}, {@code and}, {@code invert}
 *       directly on the (actor, target) pair.</li>
 * </ul>
 */
public final class BiEntityActionParser {

    private BiEntityActionParser() {}

    public static BiEntityAction parse(JsonObject json, String contextId) {
        if (json == null) return BiEntityAction.noop();
        String type = json.has("type") ? json.get("type").getAsString() : "";
        try {
            return switch (type) {
                case "origins:actor_action",   "apoli:actor_action",   "apace:actor_action"   -> parseActorAction(json, contextId);
                case "origins:target_action",  "apoli:target_action",  "apace:target_action"  -> parseTargetAction(json, contextId);
                case "origins:and",            "apoli:and",            "apace:and"            -> parseAnd(json, contextId);
                case "origins:chance",         "apoli:chance",         "apace:chance"         -> parseChance(json, contextId);
                case "origins:invert",         "apoli:invert",         "apace:invert"         -> parseInvert(json, contextId);
                case "origins:damage",         "apoli:damage",         "apace:damage"         -> parseDamage(json);
                case "origins:nothing",        "apoli:nothing"                                -> BiEntityAction.noop();
                default -> {
                    CompatWarningCollector.recordUnsupportedAction(type, contextId, "bientity_action");
                    yield BiEntityAction.noop();
                }
            };
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: failed to parse bientity_action type '{}': {}",
                contextId, type, e.getMessage());
            return BiEntityAction.noop();
        }
    }

    /**
     * {@code actor_action}: run an entity_action on the actor (the holder
     * of the power), with the target published to {@link ActionContextHolder}
     * so sub-actions like {@code damage_target} or {@code execute_command}
     * targeting the hit entity can resolve it.
     */
    private static BiEntityAction parseActorAction(JsonObject json, String contextId) {
        JsonObject inner = json.has("action") && json.get("action").isJsonObject()
            ? json.getAsJsonObject("action") : null;
        if (inner == null) return BiEntityAction.noop();
        EntityAction action = ActionParser.parse(inner, contextId);
        return (actor, target) -> {
            Object prev = ActionContextHolder.set(new EventPowerIndex.EntityInteractContext(target));
            try { action.execute(actor); }
            finally { ActionContextHolder.restore(prev); }
        };
    }

    /**
     * {@code target_action}: run an entity_action on the target. Since
     * NeoOrigins's {@link EntityAction} surface is ServerPlayer-typed,
     * this only fires when the target is itself a player (PvP-style
     * scenarios). For non-player targets, a one-shot warning is logged
     * via {@link CompatWarningCollector}.
     */
    private static BiEntityAction parseTargetAction(JsonObject json, String contextId) {
        JsonObject inner = json.has("action") && json.get("action").isJsonObject()
            ? json.getAsJsonObject("action") : null;
        if (inner == null) return BiEntityAction.noop();
        EntityAction action = ActionParser.parse(inner, contextId);
        return (actor, target) -> {
            if (target instanceof ServerPlayer sp) {
                Object prev = ActionContextHolder.set(new EventPowerIndex.EntityInteractContext(actor));
                try { action.execute(sp); }
                finally { ActionContextHolder.restore(prev); }
            }
            // Non-player target — silently skip; warning would spam since this
            // is the normal case for player-attacks-mob scenarios.
        };
    }

    private static BiEntityAction parseAnd(JsonObject json, String contextId) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) return BiEntityAction.noop();
        java.util.List<BiEntityAction> parts = new java.util.ArrayList<>();
        for (var el : json.getAsJsonArray("actions")) {
            if (el.isJsonObject()) parts.add(parse(el.getAsJsonObject(), contextId));
        }
        if (parts.isEmpty()) return BiEntityAction.noop();
        if (parts.size() == 1) return parts.get(0);
        var arr = parts.toArray(new BiEntityAction[0]);
        return (actor, target) -> { for (BiEntityAction a : arr) a.execute(actor, target); };
    }

    private static BiEntityAction parseChance(JsonObject json, String contextId) {
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 0.5f;
        BiEntityAction action = json.has("action") && json.get("action").isJsonObject()
            ? parse(json.getAsJsonObject("action"), contextId)
            : BiEntityAction.noop();
        BiEntityAction fail = json.has("fail_action") && json.get("fail_action").isJsonObject()
            ? parse(json.getAsJsonObject("fail_action"), contextId)
            : BiEntityAction.noop();
        return (actor, target) -> {
            if (actor.getRandom().nextFloat() < chance) action.execute(actor, target);
            else fail.execute(actor, target);
        };
    }

    private static BiEntityAction parseInvert(JsonObject json, String contextId) {
        BiEntityAction action = json.has("action") && json.get("action").isJsonObject()
            ? parse(json.getAsJsonObject("action"), contextId)
            : BiEntityAction.noop();
        // Apoli's invert swaps actor and target before executing the inner action.
        return (actor, target) -> {
            if (target instanceof ServerPlayer tsp) {
                action.execute(tsp, actor);
            }
            // If target isn't a player, we can't swap (actor must be ServerPlayer).
        };
    }

    /**
     * {@code damage}: directly damage the target, using either the actor
     * or a registered damage type. Supports {@code amount} and optional
     * {@code damage_type} (defaults to {@code minecraft:generic}).
     */
    private static BiEntityAction parseDamage(JsonObject json) {
        float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
        String damageTypeStr = json.has("damage_type") ? json.get("damage_type").getAsString() : "minecraft:generic";
        Identifier dtId = Identifier.tryParse(damageTypeStr);
        return (actor, target) -> {
            DamageSource src;
            if (dtId != null) {
                var lookup = actor.level().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
                var holder = lookup.get(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DAMAGE_TYPE, dtId));
                src = holder.map(h -> new DamageSource(h, actor))
                            .orElseGet(() -> actor.level().damageSources().playerAttack(actor));
            } else {
                src = actor.level().damageSources().playerAttack(actor);
            }
            target.hurt(src, amount);
        };
    }
}
