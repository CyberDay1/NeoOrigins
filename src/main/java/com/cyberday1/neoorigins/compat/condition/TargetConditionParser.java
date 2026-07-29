package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

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
 * {@code has_effect}/{@code status_effect}, {@code nbt}, {@code constant}, and the boolean
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
        String type = canonicalType(json);

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
            case "neoorigins:in_tag"            -> parseInEntityTypeTag(json);
            case "neoorigins:target_group"      -> parseTargetGroup(json);
            case "neoorigins:health"            -> parseHealth(json, false);
            case "neoorigins:relative_health"   -> parseHealth(json, true);
            case "neoorigins:has_effect",
                 "neoorigins:status_effect"     -> parseHasEffect(json);
            case "neoorigins:nbt"               -> parseNbt(json);
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

    /** Canonicalise the {@code type} field to the {@code neoorigins:} namespace. */
    private static String canonicalType(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            return "neoorigins:" + type;
        }
        if (type.startsWith("origins:") || type.startsWith("apace:") || type.startsWith("apoli:")) {
            return "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }
        return type;
    }

    /**
     * Parses a {@code bientity_condition} whose TARGET is an arbitrary
     * {@link Entity} (not necessarily a player) into a
     * {@code (actor, target) -> boolean} predicate.
     *
     * <p>The counterpart of {@link #parse}: {@code target_condition} leaves are
     * routed through the entity-general engine above (so they can be evaluated
     * against a wolf, a villager, a horse …) while {@code actor_condition}
     * leaves keep the player-typed {@link ConditionParser} — the actor of an
     * interaction is always the holder, a {@link ServerPlayer}.
     *
     * <p><b>Fail direction: closed.</b> Returns {@code null} whenever any leaf
     * is unsupported or not generalizable to a non-player target, so a caller
     * that is compiling a <em>prevention</em> can refuse to compile rather than
     * silently degrade to "applies to everything". This is deliberately the
     * opposite of {@code OriginsCompatPowerLoader.parseBiEntityConditionNarrow},
     * whose fail-open behaviour is correct for wrapping an <em>action</em>.
     *
     * <p>A non-living target can only satisfy a {@code target_condition} when
     * the underlying verb needs nothing beyond {@link Entity}; the entity-general
     * engine is {@link LivingEntity}-typed, so boats/minecarts/item frames
     * evaluate as false (= the narrowed power does not apply to them).
     *
     * <p>Pair-level verbs handled directly here: {@code constant},
     * {@code can_see}, and the combinators {@code and}/{@code all_of},
     * {@code or}/{@code any_of}, {@code not}.
     *
     * @return the compiled predicate, or {@code null} if it cannot be compiled.
     */
    public static java.util.function.BiPredicate<ServerPlayer, Entity> parseBiEntity(
            JsonObject json, String contextId) {
        if (json == null) return null;
        String type = canonicalType(json);

        java.util.function.BiPredicate<ServerPlayer, Entity> base = switch (type) {
            case "neoorigins:constant" -> {
                boolean v = json.has("value") && json.get("value").getAsBoolean();
                yield (actor, target) -> v;
            }
            case "neoorigins:target_condition" -> {
                if (!json.has("condition") || !json.get("condition").isJsonObject()) yield null;
                TargetCondition tc = parse(json.getAsJsonObject("condition"), contextId);
                yield tc == null ? null : asTargetPredicate(tc);
            }
            case "neoorigins:actor_condition" -> {
                if (!json.has("condition") || !json.get("condition").isJsonObject()) yield null;
                CompatPolicy.resetFailClosedCount();
                EntityCondition ac = ConditionParser.parse(json.getAsJsonObject("condition"), contextId);
                // An unsupported actor verb fail-closes to FALSE_CONDITION, which
                // for a prevention would mean "never prevents" — quiet but wrong.
                // Surface it as unparseable so the caller can refuse to compile.
                yield CompatPolicy.failClosedCount() > 0 ? null : (actor, target) -> ac.test(actor);
            }
            // can_see: unobstructed line of sight from the actor to the target.
            // Apoli's own bientity verb, and the only filter Origins++ Ignisian
            // puts on its quake and wrath AoEs — without it the filter was
            // dropped and the shockwave went through walls.
            case "neoorigins:can_see" -> (actor, target) -> actor.hasLineOfSight(target);
            case "neoorigins:and", "neoorigins:all_of" -> parseBiEntityAndOr(json, contextId, true);
            case "neoorigins:or",  "neoorigins:any_of" -> parseBiEntityAndOr(json, contextId, false);
            case "neoorigins:not" -> {
                if (!json.has("condition") || !json.get("condition").isJsonObject()) yield null;
                var inner = parseBiEntity(json.getAsJsonObject("condition"), contextId);
                yield inner == null ? null : inner.negate();
            }
            default -> null;
        };
        if (base == null) return null;

        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        return inverted ? base.negate() : base;
    }

    /** Adapts an entity-general {@link TargetCondition} to the bientity shape. */
    public static java.util.function.BiPredicate<ServerPlayer, Entity> asTargetPredicate(TargetCondition tc) {
        return (actor, target) -> target instanceof LivingEntity le && tc.test(le, actor);
    }

    /** and/all_of or or/any_of over bientity children; null unless EVERY child compiles. */
    private static java.util.function.BiPredicate<ServerPlayer, Entity> parseBiEntityAndOr(
            JsonObject json, String contextId, boolean matchAll) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) {
            // Apoli: empty/absent → AND is true, OR is false.
            final boolean v = matchAll;
            return (actor, target) -> v;
        }
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "conditions");
        java.util.List<java.util.function.BiPredicate<ServerPlayer, Entity>> children =
            new java.util.ArrayList<>(arr.size());
        for (var el : arr) {
            if (!el.isJsonObject()) return null;
            var child = parseBiEntity(el.getAsJsonObject(), contextId);
            if (child == null) return null;
            children.add(child);
        }
        if (children.isEmpty()) {
            final boolean v = matchAll;
            return (actor, target) -> v;
        }
        final var fChildren = children;
        if (matchAll) {
            return (actor, target) -> {
                for (var c : fChildren) if (!c.test(actor, target)) return false;
                return true;
            };
        }
        return (actor, target) -> {
            for (var c : fChildren) if (c.test(actor, target)) return true;
            return false;
        };
    }

    /**
     * nbt: the target's serialized NBT must <em>contain</em> the expected
     * subtree. Entity-general because it reads nothing but the entity's own
     * save data — Origins++ Calamitous Rogue gates a villager trade on the
     * villager's {@code Tags}, and without this leaf the whole bientity
     * condition failed closed and the trade block never applied.
     *
     * <p>A genuine partial match via
     * {@link net.minecraft.nbt.NbtUtils#compareNbt} with
     * {@code compareListTag=true}, so a single expected {@code Tags:["x"]}
     * entry is satisfied when the entity's full list includes it. Unparseable
     * SNBT fails closed (never matches); an empty expectation matches
     * everything, mirroring Apoli.
     */
    private static TargetCondition parseNbt(JsonObject json) {
        String snbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (snbt == null || snbt.isBlank()) return (t, a) -> true;
        final net.minecraft.nbt.CompoundTag expected;
        try {
            expected = net.minecraft.nbt.TagParser.parseTag(snbt);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] nbt (target): could not parse SNBT '{}' ({}); condition will never match",
                snbt, e.getMessage());
            return (t, a) -> false;
        }
        if (expected.isEmpty()) return (t, a) -> true;
        return (t, a) -> net.minecraft.nbt.NbtUtils.compareNbt(
            expected, t.saveWithoutId(new net.minecraft.nbt.CompoundTag()), true);
    }

    /** entity_type / target_type: match the target's type id, or a {@code #tag}. */
    private static TargetCondition parseEntityType(JsonObject json) {
        String et = json.has("entity_type") ? json.get("entity_type").getAsString()
                  : json.has("type_id") ? json.get("type_id").getAsString() : null;
        if (et == null || et.isBlank()) return (t, a) -> true; // absent → always true (Apoli)
        final String target = et;
        if (target.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(
                Registries.ENTITY_TYPE, ResourceLocation.parse(target.substring(1)));
            return (t, a) -> t.getType().is(tag);
        }
        ResourceLocation expected = ResourceLocation.parse(target);
        return (t, a) -> expected.equals(BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()));
    }

    /**
     * in_tag: entity-type tag membership. On the TARGET side Apoli's
     * {@code in_tag} names an entity-type tag ({@code origins-plus-plus:pets} =
     * cats/wolves/horses/villagers …), i.e. exactly the check
     * {@link #parseEntityType} already performs for a {@code "#tag"} value. It
     * is the dominant narrowing verb for prevent_entity_use in the pack corpus,
     * so without it those powers could not be compiled at all.
     */
    private static TargetCondition parseInEntityTypeTag(JsonObject json) {
        String raw = json.has("tag") ? json.get("tag").getAsString() : null;
        if (raw == null || raw.isBlank()) return (t, a) -> true; // absent → always true (Apoli)
        String id = raw.startsWith("#") ? raw.substring(1) : raw;
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(id));
        return (t, a) -> t.getType().is(tag);
    }

    /** target_group: vanilla mob-category tag (e.g. {@code monsters}). */
    private static TargetCondition parseTargetGroup(JsonObject json) {
        String group = json.has("group") ? json.get("group").getAsString() : null;
        if (group == null || group.isBlank()) return (t, a) -> false;
        TagKey<EntityType<?>> tag = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", group));
        return (t, a) -> t.getType().is(tag);
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
        ResourceLocation effId = ResourceLocation.parse(effectId);
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
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "conditions");
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
