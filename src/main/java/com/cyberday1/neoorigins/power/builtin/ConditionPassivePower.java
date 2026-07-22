package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Generic condition-gated periodic action ("passive with a trigger").
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 4). Collapses the
 * behaviour of {@code biome_buff}, {@code damage_in_biome},
 * {@code damage_in_daylight}, {@code damage_in_water},
 * {@code burn_at_health_threshold}, {@code mobs_ignore_player},
 * {@code no_mob_spawns_nearby}, and {@code item_magnetism} into a single type.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:condition_passive",
 *   "interval": 20,
 *   "condition": { ... EntityCondition tree ... },
 *   "entity_action": { ... EntityAction tree ... },
 *   "else_action": { ... optional; runs when condition false ... }
 * }
 * }</pre>
 *
 * <p>Also supersedes {@code tick_action} when condition is absent.
 */
public class ConditionPassivePower extends PowerType<ConditionPassivePower.Config> {

    public record Config(
        int interval,
        EntityCondition condition,
        EntityAction action,
        EntityAction elseAction,
        boolean toggleable,
        boolean defaultOff,
        boolean enabled,
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
                    return DataResult.error(() -> "condition_passive: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "condition_passive: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:condition_passive";
                int interval = obj.has("interval") ? Math.max(1, obj.get("interval").getAsInt()) : 20;

                // toggleable: opt-in keybind on/off (defaults false to preserve the
                // passive-by-default behaviour every existing condition_passive relies on).
                boolean toggleable = obj.has("toggleable") && obj.get("toggleable").getAsBoolean();
                boolean defaultOff = obj.has("default_off") && obj.get("default_off").getAsBoolean();

                // Config kill-switch: a top-level "enabled":false (injected by the
                // power_overrides system) collapses the power to a no-op — the periodic
                // action never runs and it never claims a keybind slot. Used by the
                // Warden dark-vision config toggles (issue #101).
                boolean enabled = com.cyberday1.neoorigins.power.util.EnabledGate.isEnabled(obj);
                if (!enabled) {
                    return DataResult.success(Pair.of(
                        new Config(interval, EntityCondition.alwaysTrue(),
                            EntityAction.noop(), EntityAction.noop(), false, false, false, t, "", false),
                        ops.empty()));
                }

                // Optional HUD icon: lets toggleable condition_passives surface on the
                // ability cluster like any other active power (bright/dim toggle pip).
                String cooldownIcon = obj.has("cooldown_icon") && obj.get("cooldown_icon").isJsonPrimitive()
                    ? obj.get("cooldown_icon").getAsString() : "";
                boolean alwaysShowIcon = obj.has("always_show_icon") && obj.get("always_show_icon").getAsBoolean();

                EntityCondition cond = ConditionParser.parseField(obj, "condition", t);
                EntityAction action = ActionParser.parseField(obj, "entity_action", t);
                EntityAction elseAction = ActionParser.parseField(obj, "else_action", t);

                return DataResult.success(Pair.of(
                    new Config(interval, cond, action, elseAction, toggleable, defaultOff, true, t, cooldownIcon, alwaysShowIcon),
                    ops.empty()));
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

    /** Per-instance toggle key so multiple toggleable condition_passive powers
     *  on one player don't share a single flag. */
    private String toggleKey(Config config) {
        return getClass().getName() + ':' + config.type() + ':' + config.interval();
    }

    /** Current toggle state for HUD sync: true when toggleable and switched off. */
    public boolean isToggledOff(ServerPlayer player, Config config) {
        if (!config.toggleable()) return false;
        return player.getData(OriginAttachments.originData()).isPowerToggledOff(toggleKey(config));
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Seed the off-state on first grant when authored default_off:true, so the
        // power starts disabled and the player opts in via the keybind.
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
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setPowerToggledOff(toggleKey(config), false);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.interval() != 0) return;
        if (config.toggleable()) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            if (data.isPowerToggledOff(toggleKey(config))) return;
        }
        if (config.condition().test(player)) {
            config.action().execute(player);
        } else {
            config.elseAction().execute(player);
        }
    }
}
