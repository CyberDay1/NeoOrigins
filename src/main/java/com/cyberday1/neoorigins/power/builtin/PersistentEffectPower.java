package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic condition-gated, toggleable status effect stack.
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 2). Collapses the
 * behaviour of {@code status_effect}, {@code stacking_status_effects},
 * {@code night_vision}, {@code glow}, {@code water_breathing},
 * {@code breath_in_fluid}, and {@code regen_in_fluid} into a single type
 * that applies an arbitrary list of effects when an optional condition is
 * met. Toggleable via keybind; effects are cleared on toggle-off and on
 * revoke.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:persistent_effect",
 *   "toggleable": true,
 *   "refresh_interval": 300,
 *   "condition": { "type": "origins:in_water" },
 *   "effects": [
 *     { "effect": "minecraft:water_breathing", "amplifier": 0,
 *       "ambient": true, "show_particles": false, "show_icon": true }
 *   ]
 * }
 * }</pre>
 */
public class PersistentEffectPower extends PowerType<PersistentEffectPower.Config> {

    public record EffectSpec(
        Holder<MobEffect> effect,
        int amplifier,
        boolean ambient,
        boolean showParticles,
        boolean showIcon
    ) {}

    public record Config(
        List<EffectSpec> effects,
        EntityCondition condition,
        int refreshInterval,
        boolean toggleable,
        boolean defaultOff,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "persistent_effect: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "persistent_effect: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:persistent_effect";
                int refresh = obj.has("refresh_interval") ? obj.get("refresh_interval").getAsInt() : 300;
                boolean toggleable = !obj.has("toggleable") || obj.get("toggleable").getAsBoolean();
                boolean defaultOff = obj.has("default_off") && obj.get("default_off").getAsBoolean();

                List<EffectSpec> specs = new ArrayList<>();
                if (obj.has("effects") && obj.get("effects").isJsonArray()) {
                    for (var el : obj.getAsJsonArray("effects")) {
                        if (!el.isJsonObject()) continue;
                        EffectSpec spec = parseSpec(el.getAsJsonObject());
                        if (spec != null) specs.add(spec);
                    }
                } else {
                    EffectSpec spec = parseSpec(obj);
                    if (spec != null) specs.add(spec);
                }

                EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();

                return DataResult.success(Pair.of(
                    new Config(List.copyOf(specs), cond, Math.max(1, refresh), toggleable, defaultOff, t),
                    ops.empty()));
            }

            private static EffectSpec parseSpec(JsonObject eff) {
                String effectId = eff.has("effect") ? eff.get("effect").getAsString()
                               : eff.has("id") ? eff.get("id").getAsString() : null;
                if (effectId == null) return null;
                var holderOpt = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId));
                if (holderOpt.isEmpty()) return null;
                int amp = eff.has("amplifier") ? eff.get("amplifier").getAsInt() : 0;
                boolean ambient = !eff.has("ambient") || eff.get("ambient").getAsBoolean();
                boolean particles = eff.has("show_particles") && eff.get("show_particles").getAsBoolean();
                boolean icon = !eff.has("show_icon") || eff.get("show_icon").getAsBoolean();
                return new EffectSpec(holderOpt.get(), amp, ambient, particles, icon);
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public boolean isActivePower(Config config) { return config.toggleable(); }

    /**
     * Per-instance toggle key — includes the effect IDs so that multiple
     * persistent_effect powers on the same player (e.g. Breeze's Cushion of
     * Air and Updraft) don't share one toggle flag. Without this, pressing
     * either keybind would flip both powers' state in lockstep.
     */
    private String toggleKey(Config config) {
        if (config.effects().isEmpty()) return getClass().getName();
        StringBuilder sb = new StringBuilder(getClass().getName());
        for (EffectSpec spec : config.effects()) {
            sb.append(':');
            var key = spec.effect().unwrapKey();
            sb.append(key.map(k -> k.location().toString()).orElse("unknown"));
        }
        return sb.toString();
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Seed the toggle off-state on first grant when the pack authored
        // default_off:true — we want the power to START disabled so the
        // player opts in. Without this, PersistentEffectPower's onTick would
        // immediately apply the effect on the next tick.
        if (config.toggleable() && config.defaultOff()) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            data.setPowerToggledOff(toggleKey(config), true);
        }
    }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        if (!config.toggleable()) return;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        boolean wasOff = data.isPowerToggledOff(toggleKey(config));
        if (wasOff) {
            data.setPowerToggledOff(toggleKey(config), false);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            data.setPowerToggledOff(toggleKey(config), true);
            clearEffects(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.toggleable()) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            if (data.isPowerToggledOff(toggleKey(config))) return;
        }
        if (!config.condition().test(player)) {
            // Condition now false: clear our effects so the player isn't permanently buffed.
            clearEffects(player, config);
            return;
        }
        // Apply effects with INFINITE_DURATION so they never tick down and the
        // vanilla "potion ending" flicker never triggers. Revoke / toggle-off /
        // condition-false all call clearEffects explicitly, so we don't rely on
        // the effect expiring naturally.
        for (EffectSpec spec : config.effects()) {
            var existing = player.getEffect(spec.effect());
            if (existing == null
                || existing.getAmplifier() < spec.amplifier()
                || !existing.isInfiniteDuration()) {
                player.addEffect(new MobEffectInstance(
                    spec.effect(), MobEffectInstance.INFINITE_DURATION, spec.amplifier(),
                    spec.ambient(), spec.showParticles(), spec.showIcon()));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setPowerToggledOff(toggleKey(config), false);
        clearEffects(player, config);
    }

    private void clearEffects(ServerPlayer player, Config config) {
        for (EffectSpec spec : config.effects()) {
            player.removeEffect(spec.effect());
        }
    }
}
