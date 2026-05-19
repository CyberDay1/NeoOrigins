package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Configurable, piglin-style aggression for mob origins. The single most
 * important thing that makes a mob origin interesting isn't its stat block —
 * it's how it acts. This rides the normal Powers tab; full goal-graph
 * authoring stays in 2.2.
 *
 * <p><b>Condition semantics (locked):</b> {@code hostile_when} is a list of
 * the existing {@link EntityCondition} DSL, AND-ed, evaluated against the
 * <em>prospective player target</em> (the player the mob is considering
 * attacking) — NOT the mob. {@link EntityCondition#test} is {@code ServerPlayer}
 * -typed and the plan's examples ("player not wearing X / holding X") are all
 * target-player predicates, so this reuses the DSL verbatim with zero new
 * condition surface and matches vanilla piglin logic exactly. For a non-player
 * {@code target_type}, conditions cannot apply (no player) and are ignored.
 *
 * <p>Mechanism: vanilla {@link NearestAttackableTargetGoal} (+ optional
 * {@link HurtByTargetGoal} for {@code retaliate}) are <em>added</em> to the
 * mob's {@code targetSelector} on grant and removed on revoke — vanilla AI is
 * left otherwise intact (unlike TameMobPower which clears it). The goal's own
 * scan cadence handles throttled re-evaluation; a linger grace keeps the
 * target briefly after conditions clear.
 */
public class MobBehaviorPower extends PowerType<MobBehaviorPower.Config> {

    public enum Aggression {
        /** No targeting added (vanilla behavior); only {@code retaliate} applies. */
        NEUTRAL,
        /** Always target the configured target type on sight. */
        HOSTILE,
        /** Target only player targets for whom every {@code hostile_when}
         *  condition holds (empty list ⇒ behaves like HOSTILE). */
        CONDITIONAL;

        public static final Codec<Aggression> CODEC = Codec.STRING.xmap(
            s -> { try { return Aggression.valueOf(s.toUpperCase(Locale.ROOT)); }
                   catch (IllegalArgumentException e) { return NEUTRAL; } },
            a -> a.name().toLowerCase(Locale.ROOT));
    }

    public record Config(
        Aggression aggression,
        List<EntityCondition> hostileWhen,
        boolean retaliate,
        int angerLingerTicks,
        double aggroRange,
        Optional<ResourceLocation> targetType,
        boolean callForHelp,
        String type
    ) implements PowerConfiguration {

        // Hand-written codec (mirrors ConditionPassivePower): EntityCondition
        // is not RecordCodecBuilder-codable; it's parsed via ConditionParser.
        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try { json = ops.convertTo(JsonOps.INSTANCE, input); }
                catch (Exception e) {
                    return DataResult.error(() -> "mob_behavior: not JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "mob_behavior: expected JSON object");
                }
                JsonObject o = json.getAsJsonObject();
                String t = o.has("type") ? o.get("type").getAsString() : "neoorigins:mob_behavior";

                Aggression mode = o.has("aggression")
                    ? Aggression.CODEC.parse(JsonOps.INSTANCE, o.get("aggression"))
                        .result().orElse(Aggression.NEUTRAL)
                    : Aggression.NEUTRAL;

                List<EntityCondition> hostileWhen = new ArrayList<>();
                if (o.has("hostile_when") && o.get("hostile_when").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("hostile_when")) {
                        if (el.isJsonObject()) hostileWhen.add(ConditionParser.parse(el.getAsJsonObject(), t));
                    }
                } else if (o.has("hostile_when") && o.get("hostile_when").isJsonObject()) {
                    hostileWhen.add(ConditionParser.parse(o.getAsJsonObject("hostile_when"), t));
                }

                boolean retaliate = !o.has("retaliate") || o.get("retaliate").getAsBoolean();
                int linger = o.has("anger_linger_ticks")
                    ? Math.max(0, o.get("anger_linger_ticks").getAsInt()) : 200;
                double range = o.has("aggro_range") ? o.get("aggro_range").getAsDouble() : 16.0;
                Optional<ResourceLocation> target = o.has("target_type")
                    ? Optional.ofNullable(ResourceLocation.tryParse(o.get("target_type").getAsString()))
                    : Optional.empty();
                boolean callForHelp = o.has("call_for_help") && o.get("call_for_help").getAsBoolean();

                return DataResult.success(Pair.of(
                    new Config(mode, List.copyOf(hostileWhen), retaliate, linger, range,
                        target, callForHelp, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override public boolean appliesToMobs(Config config) { return true; }

    @Override
    public void applyToMob(LivingEntity entity, Config config) {
        if (!(entity instanceof Mob mob)) return;

        if (config.retaliate() && mob instanceof PathfinderMob pf) {
            HurtByTargetGoal hurt = new BehaviorHurtGoal(pf);
            if (config.callForHelp()) hurt.setAlertOthers();
            mob.targetSelector.addGoal(1, hurt);
        }

        if (config.aggression() == Aggression.NEUTRAL) return;

        Predicate<LivingEntity> filter = candidate -> {
            // Target-type gate (default: players).
            if (config.targetType().isPresent()) {
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());
                if (!config.targetType().get().equals(key)) return false;
            } else if (!(candidate instanceof Player)) {
                return false;
            }
            if (config.aggression() == Aggression.HOSTILE) return true;
            // CONDITIONAL: every condition must hold for the candidate player.
            if (!(candidate instanceof ServerPlayer sp)) {
                // No player ⇒ conditions can't be evaluated; empty list = hostile.
                return config.hostileWhen().isEmpty();
            }
            for (EntityCondition c : config.hostileWhen()) {
                if (!c.test(sp)) return false;
            }
            return true;
        };

        mob.targetSelector.addGoal(2, new BehaviorTargetGoal(
            mob, config.angerLingerTicks(), config.aggroRange(), filter));
    }

    @Override
    public void removeFromMob(LivingEntity entity, Config config) {
        if (!(entity instanceof Mob mob)) return;
        mob.targetSelector.getAvailableGoals().removeIf(g ->
            g.getGoal() instanceof BehaviorTargetGoal || g.getGoal() instanceof BehaviorHurtGoal);
    }

    // ── Marker goal subclasses (so removeFromMob can strip exactly ours) ─────

    /** Marker so revoke removes only the retaliate goal we added. */
    public static class BehaviorHurtGoal extends HurtByTargetGoal {
        public BehaviorHurtGoal(PathfinderMob mob) { super(mob); }
    }

    /**
     * Conditional target goal: acquires a target matching {@code filter}, and
     * keeps it until the base goal invalidates it OR the filter has been
     * false for longer than {@code angerLingerTicks} (so a piglin-style mob
     * "calms down" a moment after the trigger clears instead of instantly).
     */
    public static class BehaviorTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private final int lingerTicks;
        private int falseSince = -1;

        public BehaviorTargetGoal(Mob mob, int lingerTicks, double range,
                                  Predicate<LivingEntity> filter) {
            super(mob, LivingEntity.class, 10, true, false, filter::test);
            this.lingerTicks = lingerTicks;
        }

        @Override
        public boolean canContinueToUse() {
            if (!super.canContinueToUse()) return false;
            LivingEntity tgt = this.mob.getTarget();
            boolean stillValid = tgt != null && this.targetConditions.test(this.mob, tgt);
            if (stillValid) { falseSince = -1; return true; }
            if (falseSince < 0) falseSince = this.mob.tickCount;
            return (this.mob.tickCount - falseSince) <= lingerTicks;
        }
    }
}
