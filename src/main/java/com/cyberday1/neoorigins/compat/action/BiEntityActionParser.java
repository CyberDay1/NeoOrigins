package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatWarningCollector;
import com.cyberday1.neoorigins.service.ActionContextHolder;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
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
 *       the inner EntityAction on it (with actor in context). For a non-player
 *       {@link LivingEntity} target, run a {@link TargetAction} from
 *       {@link TargetActionParser} for the entity-general verb subset (effects,
 *       damage, fire, knockback, …); player-only verbs no-op on a mob target.</li>
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
                // Apoli mount: the ACTOR starts riding the TARGET. Distinct from the
                // entity-action "mount" verb (which mounts the nearest entity) — a
                // bientity mount must ride the specific pair member (e.g. the player
                // who right-clicked a Giant mounts that Giant; a raycast-grabbed mob
                // inverted-mounts the caster).
                case "origins:mount",          "apoli:mount",          "apace:mount"          ->
                    (actor, target) -> actor.startRiding(target, true);
                case "origins:if_else",        "apoli:if_else",        "apace:if_else"        -> parseIfElse(json, contextId);
                case "origins:nothing",        "apoli:nothing"                                -> BiEntityAction.noop();
                default -> {
                    // Loose packs nest plain entity-actions directly in a bientity
                    // `and` (add_velocity, mount, if_else, spawn_particles, ...),
                    // intending them to run on the ACTOR with the hit entity available
                    // as the dispatch context. Fall back to that rather than no-op:
                    // parse as an entity-action and run it on the actor, publishing the
                    // target to ActionContextHolder. ActionParser canonicalizes the
                    // prefix (origins:/apoli: -> neoorigins:) and itself records any
                    // genuinely-unknown verb (e.g. sync:execute_command, which needs the
                    // Sync mod) as unsupported — so this neither hides nor double-warns.
                    EntityAction actorAction = ActionParser.parse(json, contextId);
                    yield (actor, target) -> {
                        Object prev = ActionContextHolder.set(
                            new EventPowerIndex.EntityInteractContext(target));
                        try { actorAction.execute(actor); }
                        finally { ActionContextHolder.restore(prev); }
                    };
                }
            };
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: failed to parse bientity_action type '{}': {}",
                contextId, type, e.getMessage());
            return BiEntityAction.noop();
        }
    }

    /**
     * {@code if_else}: branch on a <em>bientity</em> condition, running
     * {@code if_action} or {@code else_action} on the same (actor, target) pair.
     *
     * <p>Without this case the node fell through to the loose entity-action
     * fallback, which handed the {@code condition} to the player-typed
     * {@link com.cyberday1.neoorigins.compat.condition.ConditionParser}. A
     * {@code target_condition} means nothing there, so it failed closed and the
     * branch always took the else side — Chaotic Chemist's Benefit of Hindsight
     * never recognised its own replacement potion. The condition is therefore
     * compiled as a bientity predicate first, and only an
     * <em>uncompilable</em> one falls back to the old behaviour.
     */
    private static BiEntityAction parseIfElse(JsonObject json, String contextId) {
        JsonObject condJson = json.has("condition") && json.get("condition").isJsonObject()
            ? json.getAsJsonObject("condition") : null;
        var cond = condJson == null ? null
            : com.cyberday1.neoorigins.compat.condition.TargetConditionParser
                .parseBiEntity(condJson, contextId);
        if (cond == null) {
            CompatWarningCollector.recordUnsupportedAction("neoorigins:if_else", contextId,
                "bientity if_else condition cannot be evaluated against a non-player target"
                    + " — the else branch always runs");
            cond = (actor, target) -> false;
        }
        BiEntityAction ifAction = json.has("if_action") && json.get("if_action").isJsonObject()
            ? parse(json.getAsJsonObject("if_action"), contextId) : BiEntityAction.noop();
        BiEntityAction elseAction = json.has("else_action") && json.get("else_action").isJsonObject()
            ? parse(json.getAsJsonObject("else_action"), contextId) : BiEntityAction.noop();
        final var fCond = cond;
        return (actor, target) -> {
            if (fCond.test(actor, target)) ifAction.execute(actor, target);
            else elseAction.execute(actor, target);
        };
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
     * {@code target_action}: run an entity_action on the target.
     *
     * <p>When the target is a player, the full player-targeted
     * {@link EntityAction} set runs on it (PvP-style scenarios). When the
     * target is a non-player {@link LivingEntity} (e.g. the mob hit by a
     * projectile / on-hit power), {@link TargetActionParser} provides a
     * {@link TargetAction} for the subset of verbs whose effect is
     * entity-general ({@code apply_effect}, {@code damage}, {@code set_on_fire},
     * …) so those run against the mob. Player-only verbs ({@code grant_power},
     * resources, inventory, …) are not generalizable, so on a mob target they
     * silently no-op — the long-standing behaviour for that case.
     */
    private static BiEntityAction parseTargetAction(JsonObject json, String contextId) {
        JsonObject inner = json.has("action") && json.get("action").isJsonObject()
            ? json.getAsJsonObject("action") : null;
        if (inner == null) return BiEntityAction.noop();
        EntityAction playerAction = ActionParser.parse(inner, contextId);
        TargetAction mobAction = TargetActionParser.parse(inner, contextId);
        return (actor, target) -> {
            if (target instanceof ServerPlayer sp) {
                Object prev = ActionContextHolder.set(new EventPowerIndex.EntityInteractContext(actor));
                try { playerAction.execute(sp); }
                finally { ActionContextHolder.restore(prev); }
            } else if (mobAction != null) {
                // Non-player target, generalizable verb — run it on the mob.
                mobAction.execute(target, actor);
            }
            // Non-player target + non-generalizable (player-only) verb — skip.
        };
    }

    private static BiEntityAction parseAnd(JsonObject json, String contextId) {
        java.util.List<BiEntityAction> parts = new java.util.ArrayList<>();
        for (var el : com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "actions")) {
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
        ResourceLocation dtId = ResourceLocation.tryParse(damageTypeStr);
        return (actor, target) -> {
            DamageSource src;
            if (dtId != null) {
                var reg = actor.level().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
                var holder = reg.getHolder(net.minecraft.resources.ResourceKey.create(
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
