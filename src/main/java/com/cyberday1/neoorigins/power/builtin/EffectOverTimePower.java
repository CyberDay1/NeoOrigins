package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import net.minecraft.resources.Identifier;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Unified aura / sustained "effect over time" power — one {@code entity_action}
 * tree applied repeatedly on an interval, in one of two modes selected by the
 * {@code activation} field. Built for auras: a constant effect (often an
 * {@code area_of_effect} damage/heal pulse) that radiates from the holder.
 *
 * <ul>
 *   <li>{@code "passive"} (default) — an always-on aura: runs
 *       {@code entity_action} every {@code interval} ticks while granted, with no
 *       upkeep cost. {@code condition} gates the pulse; {@code else_action} runs
 *       when it fails. With {@code toggleable:true} it also gains an on/off
 *       keybind (still free), and starts <em>on</em> by default.</li>
 *   <li>{@code "active"} — a maintained aura: a keybind toggles it on/off, and
 *       while on it pulses {@code entity_action} every {@code interval} AND pays
 *       an upkeep cost ({@code hunger_cost} and/or
 *       {@code resource_cost}/{@code resource_cost_amount}) each interval. When
 *       the holder can no longer pay the upkeep the aura switches itself off.
 *       Active auras start <em>off</em> by default (opt-in).</li>
 * </ul>
 *
 * <p>The initial on/off state of a toggleable aura follows its mode — active
 * starts off, toggleable-passive starts on — and an explicit {@code default_off}
 * overrides that either way.</p>
 *
 * <p>For one-shot, cooldown-gated abilities use {@code neoorigins:active_ability}
 * instead — this type is specifically for sustained auras.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:effect_over_time",
 *   "activation": "active",         // passive | active
 *   "toggleable": false,             // passive only: add an on/off keybind
 *   "interval": 20,                  // ticks between pulses (and upkeep charges)
 *   "condition": { ... optional EntityCondition tree ... },
 *   "entity_action": { "type": "origins:area_of_effect", ... },
 *   "else_action": { ... optional; runs when condition false ... },
 *   "hunger_cost": 2,                // active upkeep: food points per interval
 *   "resource_cost": "",             // active upkeep: resource power id
 *   "resource_cost_amount": 0,       // active upkeep: amount per interval
 *   "default_off": false,            // override initial state (def: active=on→off, passive-toggle=on)
 *   "cooldown_icon": "",
 *   "always_show_icon": false
 * }
 * }</pre>
 */
public class EffectOverTimePower extends PowerType<EffectOverTimePower.Config> {

    /** Activation modes for {@link Config#activation()}. */
    public static final String PASSIVE = "passive";
    public static final String ACTIVE = "active";

    public record Config(
        String activation,
        boolean toggleable,
        int interval,
        EntityCondition condition,
        EntityAction action,
        EntityAction elseAction,
        int hungerCost,
        String resourceCost,
        int resourceCostAmount,
        boolean defaultOff,
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, com.cyberday1.neoorigins.power.builtin.base.HudIconConfig {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "effect_over_time: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "effect_over_time: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:effect_over_time";

                // activation: passive (always-on, free) or active (toggled, upkeep).
                // "toggle" is accepted as a friendly alias for active; anything else
                // falls back to passive (safest — never silently steals a keybind).
                String activation = obj.has("activation") ? obj.get("activation").getAsString().toLowerCase() : PASSIVE;
                if (activation.equals("toggle")) activation = ACTIVE;
                if (!activation.equals(ACTIVE)) activation = PASSIVE;
                boolean active = activation.equals(ACTIVE);

                // A passive aura can opt into an on/off keybind via toggleable:true;
                // an active aura is inherently toggled (the keybind + upkeep is the
                // whole point), so it's always toggleable.
                boolean toggleable = active || (obj.has("toggleable") && obj.get("toggleable").getAsBoolean());

                int interval = obj.has("interval") ? Math.max(1, obj.get("interval").getAsInt()) : 20;
                int hunger = obj.has("hunger_cost") ? obj.get("hunger_cost").getAsInt() : 0;
                String resCost = obj.has("resource_cost") ? obj.get("resource_cost").getAsString() : "";
                int resCostAmt = obj.has("resource_cost_amount") ? obj.get("resource_cost_amount").getAsInt() : 0;
                // Mode-aware default: active auras start OFF (opt-in — they burn
                // upkeep), toggleable passives start ON (free, expected to be up).
                // An explicit default_off always wins.
                boolean defaultOff = obj.has("default_off")
                    ? obj.get("default_off").getAsBoolean()
                    : active;
                String cdIcon = obj.has("cooldown_icon") && obj.get("cooldown_icon").isJsonPrimitive()
                    ? obj.get("cooldown_icon").getAsString() : "";
                boolean alwaysShow = obj.has("always_show_icon") && obj.get("always_show_icon").getAsBoolean();

                EntityCondition cond = ConditionParser.parseField(obj, "condition", t);
                EntityAction action = ActionParser.parseField(obj, "entity_action", t);
                EntityAction elseAction = ActionParser.parseField(obj, "else_action", t);

                String finalActivation = activation;
                return DataResult.success(Pair.of(
                    new Config(finalActivation, toggleable, interval, cond, action, elseAction,
                        hunger, resCost, resCostAmt, defaultOff, t, cdIcon, alwaysShow),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                // Compiled action/condition lambdas don't round-trip — sync payloads
                // carry only type ID + display, like the other action-driven types.
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** A power claims a keybind slot when it can be toggled: every active
     *  (maintained, upkeep-paying) aura, plus any passive aura authored
     *  {@code toggleable:true}. Plain passive auras are always on and need no key. */
    @Override
    public boolean isActivePower(Config config) {
        return hasToggle(config);
    }

    /** True when this aura exposes an on/off keybind — active auras always, and
     *  passive auras only when {@code toggleable:true}. */
    private boolean hasToggle(Config config) {
        return config.activation().equals(ACTIVE) || config.toggleable();
    }

    /**
     * Per-instance toggle key: the aura's own resource id.
     *
     * <p>This used to be class + type + {@code interval}. Class and type are
     * constant for this power type, so {@code interval} was the ONLY thing
     * telling two auras apart, and two auras that happened to share an interval
     * (20 by default, so most of them) shared one on/off flag — activating one
     * activated the other. Giving different intervals looked like a fix and was
     * only ever a way of making the keys differ.
     */
    private String toggleKey(Config config) {
        return toggleKey(PowerHolder.currentDispatchId(), config);
    }

    String toggleKey(Identifier id, Config config) {
        return id != null ? id.toString() : legacyToggleKey(config);
    }

    /** The pre-2.2.24 shared key, read as a fallback so saved toggles survive. */
    String legacyToggleKey(Config config) {
        return getClass().getName() + ':' + config.type() + ':' + config.interval();
    }

    /** Current toggle state for HUD sync: true when this aura has a keybind and
     *  is switched off. */
    public boolean isToggledOff(ServerPlayer player, Config config) {
        return isToggledOff(player, config, PowerHolder.currentDispatchId());
    }

    /** As above, for callers outside a {@link PowerHolder} dispatch (the HUD sync). */
    public boolean isToggledOff(ServerPlayer player, Config config, Identifier id) {
        if (!hasToggle(config)) return false;
        return player.getData(OriginAttachments.originData())
            .isPowerToggledOff(toggleKey(id, config), legacyToggleKey(config));
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Seed the off-state on first grant when a toggleable aura is authored
        // (or defaults) off — active auras default off, toggleable passives
        // default on — so it starts in the right state and the player opts in.
        if (hasToggle(config) && config.defaultOff()) {
            player.getData(OriginAttachments.originData())
                .setPowerToggledOff(toggleKey(config), legacyToggleKey(config), true);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        if (hasToggle(config)) {
            player.getData(OriginAttachments.originData())
                .setPowerToggledOff(toggleKey(config), legacyToggleKey(config), false);
        }
    }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        // Only toggleable auras bind a key — flip the maintained on/off state.
        if (!hasToggle(config)) return;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        boolean wasOff = data.isPowerToggledOff(toggleKey(config), legacyToggleKey(config));
        if (wasOff) {
            data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), false);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), true);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.interval() != 0) return;

        if (hasToggle(config)) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            if (data.isPowerToggledOff(toggleKey(config), legacyToggleKey(config))) return;
            // Active auras pay upkeep for each pulse; if the holder can't afford
            // it the aura switches itself off (the "cost to keep it up").
            // Toggleable passives are free, so they skip the charge.
            if (config.activation().equals(ACTIVE) && !payUpkeep(player, config)) {
                data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), true);
                player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                    .withStyle(ChatFormatting.RED));
                return;
            }
        }

        try {
            if (config.condition().test(player)) {
                config.action().execute(player);
            } else {
                config.elseAction().execute(player);
            }
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("effect_over_time pulse errored: {}", e.getMessage());
        }
    }

    /**
     * Charges one interval of upkeep for an active aura. Returns true if paid
     * (or there is no cost), false if the holder can't afford it. Mirrors the
     * {@link com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower}
     * cost model: resource cost falls back to hunger when resource bars are
     * globally disabled.
     */
    private boolean payUpkeep(ServerPlayer player, Config config) {
        int hungerCost = config.hungerCost();
        String resCostKey = config.resourceCost();
        int resCostAmt = config.resourceCostAmount();
        boolean hasResourceCost = !resCostKey.isEmpty() && resCostAmt > 0;
        if (hasResourceCost && ContentTogglesConfig.isResourceBarsDisabled()) {
            hungerCost += resCostAmt;
            hasResourceCost = false;
        }

        if (hungerCost > 0 && !com.cyberday1.neoorigins.util.FoodCost.canAfford(player, hungerCost)) return false;
        if (hasResourceCost && ResourcePower.getValue(player, resCostKey) < resCostAmt) return false;

        if (hungerCost > 0) {
            com.cyberday1.neoorigins.util.FoodCost.spend(player, hungerCost);
        }
        if (hasResourceCost) {
            ResourcePower.deduct(player, resCostKey, resCostAmt);
        }
        return true;
    }
}
