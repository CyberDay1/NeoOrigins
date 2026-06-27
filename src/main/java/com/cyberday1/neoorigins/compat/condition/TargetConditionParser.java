package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Parses a condition JSON into a {@link TargetCondition} for the subset of
 * condition verbs that are <em>entity-general</em> — they read only the target
 * {@link net.minecraft.world.entity.LivingEntity}'s own state and never a
 * player-only subsystem, so they can be evaluated against an arbitrary mob.
 *
 * <p>Returns {@code null} for any verb that is NOT generalizable. The caller
 * ({@link com.cyberday1.neoorigins.compat.action.ActionParser#parseAreaOfEffect})
 * then keeps the existing player-typed {@link EntityCondition} behaviour for that
 * verb (player targets are gated, mobs bypass it, exactly as before).
 *
 * <p>This is the condition mirror of
 * {@link com.cyberday1.neoorigins.compat.action.TargetActionParser}, and is
 * additive: it does not touch the player-typed {@link ConditionParser} path, so
 * existing behaviour and the SchemaFormCheck audit over that path are unaffected.
 *
 * <p>Generalizable verbs: {@code entity_type} (and its {@code target_type}/
 * {@code type_id} forms, with {@code #tag} support), {@code target_group},
 * {@code living}, {@code on_fire}, {@code health}, {@code relative_health},
 * {@code has_effect}/{@code status_effect}, {@code constant}, and the boolean
 * combinators {@code and}/{@code all_of}, {@code or}/{@code any_of}, {@code not}
 * (generalizable only when every child is). The Apoli {@code inverted:true} flag
 * is honoured.
 */
public final class TargetConditionParser {

    private TargetConditionParser() {}

    /**
     * @return a {@link TargetCondition} for a generalizable verb, or {@code null}
     *         if the verb is not generalizable to a non-player target.
     */
    public static TargetCondition parse(JsonObject json, String contextId) {
        if (json == null) return null;
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (type.startsWith("origins:") || type.startsWith("apace:") || type.startsWith("apoli:")) {
            type = "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }

        TargetCondition base = switch (type) {
            case "neoorigins:constant" -> {
                boolean v = json.has("value") && json.get("value").getAsBoolean();
                yield (t, a) -> v;
            }
            case "neoorigins:living"            -> (t, a) -> t.isAlive();
            case "neoorigins:on_fire",
                 "neoorigins:fire"              -> (t, a) -> t.isOnFire();
            case "neoorigins:entity_type",
                 "neoorigins:target_type"       -> parseEntityType(json);
            case "neoorigins:target_group"      -> parseTargetGroup(json);
            case "neoorigins:health"            -> parseHealth(json, false);
            case "neoorigins:relative_health"   -> parseHealth(json, true);
            case "neoorigins:has_effect",
                 "neoorigins:status_effect"     -> parseHasEffect(json);
            case "neoorigins:and",
                 "neoorigins:all_of"            -> parseAndOr(json, contextId, true);
            case "neoorigins:or",
                 "neoorigins:any_of"            -> parseAndOr(json, contextId, false);
            case "neoorigins:not"               -> parseNot(json, contextId);
            // Not generalizable — caller keeps player-typed / bypass behaviour.
            default -> null;
        };
        if (base == null) return null;

        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        if (inverted) {
            final TargetCondition b = base;
            return (t, a) -> !b.test(t, a);
        }
        return base;
    }

    /** entity_type / target_type: match the target's type id, or a {@code #tag}. */
    private static TargetCondition parseEntityType(JsonObject json) {
        String et = json.has("entity_type") ? json.get("entity_type").getAsString()
                  : json.has("type_id") ? json.get("type_id").getAsString() : null;
        if (et == null || et.isBlank()) return (t, a) -> true; // absent → always true (Apoli)
        final String target = et;
        if (target.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(
                Registries.ENTITY_TYPE, Identifier.parse(target.substring(1)));
            return (t, a) -> t.getType().getTags().anyMatch(tk -> tk.equals(tag));
        }
        Identifier expected = Identifier.parse(target);
        return (t, a) -> expected.equals(BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()));
    }

    /** target_group: vanilla mob-category tag (e.g. {@code monsters}). */
    private static TargetCondition parseTargetGroup(JsonObject json) {
        String group = json.has("group") ? json.get("group").getAsString() : null;
        if (group == null || group.isBlank()) return (t, a) -> false;
        TagKey<EntityType<?>> tag = TagKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("minecraft", group));
        return (t, a) -> t.getType().getTags().anyMatch(tk -> tk.equals(tag));
    }

    /** health / relative_health: comparison against (relative) current health. */
    private static TargetCondition parseHealth(JsonObject json, boolean relative) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double threshold = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return (t, a) -> {
            double value = relative ? t.getHealth() / t.getMaxHealth() : t.getHealth();
            return comparison.test(value, threshold);
        };
    }

    /** has_effect / status_effect: target currently has the named mob effect. */
    private static TargetCondition parseHasEffect(JsonObject json) {
        String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
        if (effectId == null || effectId.isBlank()) return (t, a) -> false;
        Identifier effId = Identifier.parse(effectId);
        var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effId);
        if (effectOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] has_effect (target): unknown mob effect '{}' — condition is false", effId);
            return (t, a) -> false;
        }
        var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effectOpt.get());
        return (t, a) -> t.hasEffect(holder);
    }

    /** and/all_of (matchAll=true) or or/any_of: generalizable only when EVERY child is. */
    private static TargetCondition parseAndOr(JsonObject json, String contextId, boolean matchAll) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) {
            // Apoli: empty/absent → AND is true, OR is false.
            final boolean v = matchAll;
            return (t, a) -> v;
        }
        JsonArray arr = json.getAsJsonArray("conditions");
        java.util.List<TargetCondition> children = new java.util.ArrayList<>(arr.size());
        for (var el : arr) {
            if (!el.isJsonObject()) return null;
            TargetCondition child = parse(el.getAsJsonObject(), contextId);
            if (child == null) return null; // any non-generalizable child → stay player-typed
            children.add(child);
        }
        if (children.isEmpty()) {
            final boolean v = matchAll;
            return (t, a) -> v;
        }
        final java.util.List<TargetCondition> fChildren = children;
        if (matchAll) {
            return (t, a) -> { for (TargetCondition c : fChildren) if (!c.test(t, a)) return false; return true; };
        }
        return (t, a) -> { for (TargetCondition c : fChildren) if (c.test(t, a)) return true; return false; };
    }

    /** not: negate the single nested {@code condition}; null unless it is generalizable. */
    private static TargetCondition parseNot(JsonObject json, String contextId) {
        if (!json.has("condition") || !json.get("condition").isJsonObject()) return null;
        TargetCondition inner = parse(json.getAsJsonObject("condition"), contextId);
        if (inner == null) return null;
        final TargetCondition fInner = inner;
        return (t, a) -> !fInner.test(t, a);
    }
}
