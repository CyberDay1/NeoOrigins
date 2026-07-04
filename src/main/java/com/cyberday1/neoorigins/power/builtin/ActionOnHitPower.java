package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.action.BiEntityAction;
import com.cyberday1.neoorigins.compat.action.BiEntityActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.TargetAction;
import com.cyberday1.neoorigins.compat.action.TargetActionParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Random;

/**
 * Fires an action each time the player deals damage to a living entity, optionally
 * restricted to a target entity group (e.g. {@code undead}, {@code arthropod}),
 * a specific entity type, or a damage type.
 *
 * <p>Wired from {@link com.cyberday1.neoorigins.event.CombatPowerEvents#onLivingDamage}
 * — the event fires before damage is finalised, so {@code min_damage} is checked
 * against the (possibly multiplier-modified) incoming amount.
 *
 * <p>Action values (flat {@code action} schema):
 * <ul>
 *   <li>{@code restore_health} — heals the attacker by {@code amount}</li>
 *   <li>{@code restore_hunger} — feeds the attacker by {@code amount} food points</li>
 *   <li>{@code grant_effect}   — applies {@code effect} to the attacker (self)</li>
 *   <li>{@code target_effect}  — applies {@code effect} to the entity being hit</li>
 * </ul>
 *
 * <p>Additionally an optional Apoli-style {@code bientity_action} (alias
 * {@code entity_action}) may be declared — a parsed action that runs against
 * the (actor = attacker, target = victim) pair. A bare non-wrapper verb (e.g.
 * {@code apply_effect}) runs against the <em>victim</em>; wrap in
 * {@code actor_action} / {@code target_action} to route explicitly. This is the
 * form documented in ACTIONS.md and is what a pack author copying the docs
 * expects. It runs in addition to (and after) the flat {@code action}, gated by
 * the same filters (min_damage, damage_type, target_group, target_type, chance).
 */
public class ActionOnHitPower extends PowerType<ActionOnHitPower.Config> {

    private static final Random RANDOM = new Random();

    public record Config(
        String action,
        float amount,
        Optional<ResourceLocation> effect,
        int duration,
        int amplifier,
        float minDamage,
        float chance,
        Optional<String> targetGroup,
        Optional<String> targetType,
        Optional<String> damageType,
        // Optional parsed bi-entity action (actor = attacker, target = victim).
        // Parsed from `bientity_action` OR its alias `entity_action`. Default =
        // BiEntityAction.noop(). Runs against the victim in CombatPowerEvents
        // after the flat `action`, governed by the same filter gates.
        BiEntityAction onHitAction,
        String type
    ) implements PowerConfiguration {
        // Hand-written codec (mirrors ActionOnEventPower.Config.CODEC): the flat
        // fields are read exactly as the prior RecordCodecBuilder did, plus a raw
        // BiEntityAction is parsed from the nested `bientity_action`/`entity_action`
        // object — something a RecordCodecBuilder can't express because the nested
        // action is a hand-rolled parser, not a Codec.
        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "action_on_hit: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "action_on_hit: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "";

                boolean hasBiField = obj.has("bientity_action") || obj.has("entity_action");
                // Flat `action` defaults to restore_health for backward compat.
                // BUT when a bientity_action/entity_action is supplied and no flat
                // `action` is explicitly set, default to "none" so the parsed
                // action stands alone — a pack copying the ACTIONS.md poison
                // example gets ONLY poison, not a silent 2-HP self-heal on top.
                String action = strOr(obj, "action", hasBiField ? "none" : "restore_health");
                float amount = floatOr(obj, "amount", 2.0f);
                Optional<ResourceLocation> effect =
                    (obj.has("effect") && obj.get("effect").isJsonPrimitive())
                        ? Optional.ofNullable(ResourceLocation.tryParse(obj.get("effect").getAsString()))
                        : Optional.empty();
                int duration = intOr(obj, "duration", 100);
                int amplifier = intOr(obj, "amplifier", 0);
                float minDamage = floatOr(obj, "min_damage", 0.0f);
                float chance = floatOr(obj, "chance", 1.0f);
                Optional<String> targetGroup = optStr(obj, "target_group");
                Optional<String> targetType = optStr(obj, "target_type");
                Optional<String> damageType = optStr(obj, "damage_type");

                // Parse the optional bi-entity action from `bientity_action` OR
                // its alias `entity_action`. Object or array; a bare verb routes
                // to the victim (see parseOnHitAction).
                String pid = obj.has("_power_id") ? obj.get("_power_id").getAsString()
                    : (t.isEmpty() ? "neoorigins:action_on_hit" : t);
                BiEntityAction onHit = parseOnHitAction(obj, pid);

                return DataResult.success(Pair.of(
                    new Config(action, amount, effect, duration, amplifier, minDamage,
                        chance, targetGroup, targetType, damageType, onHit, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        private static String strOr(JsonObject o, String k, String def) {
            return (o.has(k) && o.get(k).isJsonPrimitive()) ? o.get(k).getAsString() : def;
        }
        private static float floatOr(JsonObject o, String k, float def) {
            return (o.has(k) && o.get(k).isJsonPrimitive()) ? o.get(k).getAsFloat() : def;
        }
        private static int intOr(JsonObject o, String k, int def) {
            return (o.has(k) && o.get(k).isJsonPrimitive()) ? o.get(k).getAsInt() : def;
        }
        private static Optional<String> optStr(JsonObject o, String k) {
            return (o.has(k) && o.get(k).isJsonPrimitive())
                ? Optional.of(o.get(k).getAsString()) : Optional.empty();
        }
    }

    /**
     * Parse the optional bi-entity action from {@code bientity_action} (or its
     * alias {@code entity_action}) into a {@link BiEntityAction} that runs
     * against (actor = attacker, target = victim). Accepts a single object or a
     * JSON array (run in order). Returns {@link BiEntityAction#noop()} when
     * absent or unparseable.
     *
     * <p>Routing rule for a bare (non-wrapper) verb such as {@code apply_effect}:
     * it runs against the <em>victim</em>. This matches the documented
     * "target = the entity you hit" contract and what a pack author copying the
     * ACTIONS.md example expects. Explicit {@code actor_action}/{@code target_action}
     * (and the other Apoli wrappers: {@code and}/{@code chance}/{@code invert}/
     * {@code damage}/{@code nothing}) are handed to {@link BiEntityActionParser}
     * unchanged so their actor/target routing is preserved.
     */
    private static BiEntityAction parseOnHitAction(JsonObject obj, String contextId) {
        JsonElement el = obj.has("bientity_action") ? obj.get("bientity_action")
            : (obj.has("entity_action") ? obj.get("entity_action") : null);
        if (el == null) return BiEntityAction.noop();
        if (el.isJsonObject()) {
            return oneBiEntityAction(el.getAsJsonObject(), contextId);
        }
        if (el.isJsonArray()) {
            java.util.List<BiEntityAction> parts = new java.util.ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) parts.add(oneBiEntityAction(item.getAsJsonObject(), contextId));
            }
            java.util.List<BiEntityAction> live = new java.util.ArrayList<>();
            for (BiEntityAction a : parts) if (a != BiEntityAction.noop()) live.add(a);
            if (live.isEmpty()) return BiEntityAction.noop();
            if (live.size() == 1) return live.get(0);
            BiEntityAction[] arr = live.toArray(new BiEntityAction[0]);
            return (actor, target) -> { for (BiEntityAction a : arr) a.execute(actor, target); };
        }
        return BiEntityAction.noop();
    }

    /**
     * Turn one action object into a BiEntityAction. Wrapper verbs go through
     * {@link BiEntityActionParser} (which honours actor/target routing); a bare
     * verb is treated as an implicit target action — generalizable verbs run on
     * any victim via {@link TargetActionParser}, and if no generalizable form
     * exists the player-only {@link EntityAction} runs when the victim is a
     * player (mirroring the area_of_effect dual-parse).
     */
    private static BiEntityAction oneBiEntityAction(JsonObject inner, String contextId) {
        String type = inner.has("type") ? inner.get("type").getAsString() : "";
        String bare = type.indexOf(':') >= 0 ? type.substring(type.indexOf(':') + 1) : type;
        boolean isWrapper = switch (bare) {
            case "actor_action", "target_action", "and", "chance", "invert", "damage", "nothing" -> true;
            default -> false;
        };
        if (isWrapper) {
            return BiEntityActionParser.parse(inner, contextId);
        }
        // Bare verb → run against the victim.
        EntityAction playerAction = ActionParser.parse(inner, contextId);
        TargetAction mobAction = TargetActionParser.parse(inner, contextId);
        return (actor, target) -> {
            if (target instanceof ServerPlayer sp) {
                Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                    new com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext(actor));
                try { playerAction.execute(sp); }
                finally { com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev); }
            } else if (mobAction != null) {
                mobAction.execute(target, actor);
            }
            // Non-player victim + player-only verb → skip (no sensible target).
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Executes the configured action. The roll for {@code chance} and the
     * {@code min_damage} / filter checks are expected to be handled by the
     * caller (CombatPowerEvents) before invoking this.
     */
    public static void execute(ServerPlayer player, Config config, LivingEntity target) {
        switch (config.action()) {
            // `none`/`nothing`: no flat action — used when the effect is expressed
            // purely via bientity_action/entity_action. Silent no-op (not a warn).
            case "none", "nothing", "" -> { }
            case "restore_health" -> player.heal(config.amount());
            case "restore_hunger" -> player.getFoodData().eat((int) config.amount(), 0);
            case "grant_effect" -> applyEffect(player, config);
            case "target_effect" -> applyEffect(target, config);
            default -> NeoOrigins.LOGGER.warn(
                "action_on_hit action '{}' is unknown — expected one of restore_health, restore_hunger, grant_effect, target_effect.",
                config.action());
        }
    }

    public static boolean rollChance(Config config) {
        return config.chance() >= 1.0f || RANDOM.nextFloat() < config.chance();
    }

    private static void applyEffect(LivingEntity recipient, Config config) {
        if (config.effect().isEmpty()) return;
        BuiltInRegistries.MOB_EFFECT.getOptional(config.effect().get()).ifPresent(effect -> {
            var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            recipient.addEffect(new MobEffectInstance(holder, config.duration(), config.amplifier(), false, true));
        });
    }
}
