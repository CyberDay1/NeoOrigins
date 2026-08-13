package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants free, creative-style flight (mayfly + flying) as a toggle, without the
 * extra phantom/spectator behaviour — the player keeps solid block collision,
 * normal visibility and gravity when not flying. Double-tap jump to take off,
 * jump/sneak to climb and descend, exactly like creative mode.
 *
 * <p>Unlike {@code neoorigins:flight} and {@code neoorigins:natural_glide}
 * (both elytra/fall-flying mechanics), this is true hover-flight — intended for
 * "ride the sword" / levitating-cultivator fantasies. The abilities are pushed
 * to the client each tick via {@code onUpdateAbilities()} to survive sync
 * races; {@link #removeEffect} restores survival defaults but never clears the
 * flags for a creative/spectator player (that would lock them out of their mode).
 *
 * <p>An optional {@code condition} gates the flight and is re-tested every tick,
 * so a requirement like "sword in the main hand" holds the player in the air only
 * while it is met — sheathing the blade strips the ability mid-flight and drops
 * them. Absent, the flight is unconditional.
 */
public class CreativeFlightPower extends AbstractTogglePower<CreativeFlightPower.Config> {

    public record Config(
        boolean enabled,
        EntityCondition condition,
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, HudIconConfig {
        // Hand-rolled rather than a RecordCodecBuilder group because `condition`
        // compiles through ConditionParser, which reads raw JSON.
        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "creative_flight: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "creative_flight: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:creative_flight";

                // A malformed field must come back as a DataResult error the loader
                // logs and skips, the way the RecordCodecBuilder group this replaced
                // did — an escaping RuntimeException would take the whole power
                // reload down with it.
                try {
                    // Config kill-switch: a top-level "enabled":false (injected by the
                    // power_overrides system) turns the flight off — the ability is
                    // stripped each tick and never re-granted.
                    boolean enabled = com.cyberday1.neoorigins.power.util.EnabledGate.isEnabled(obj);
                    String cooldownIcon = obj.has("cooldown_icon") && obj.get("cooldown_icon").isJsonPrimitive()
                        ? obj.get("cooldown_icon").getAsString() : "";
                    boolean alwaysShowIcon = obj.has("always_show_icon") && obj.get("always_show_icon").getAsBoolean();

                    // Absent condition parses to alwaysTrue(), i.e. unconditional flight.
                    EntityCondition cond = ConditionParser.parseField(obj, "condition", t);

                    return DataResult.success(Pair.of(
                        new Config(enabled, cond, t, cooldownIcon, alwaysShowIcon), ops.empty()));
                } catch (RuntimeException e) {
                    return DataResult.error(() -> "creative_flight: malformed field: " + e.getMessage());
                }
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        if (!config.enabled()) {
            // Disabled via power_overrides — strip any flight the player may still
            // hold and never re-grant it.
            removeEffect(player, config);
            return;
        }
        if (!config.condition().test(player)) {
            // Condition no longer holds — take the flight away mid-air rather than
            // waiting for the next toggle. Dropping the player is the point: it is
            // what makes a requirement like "sword in hand" a real cost.
            removeEffect(player, config);
            return;
        }
        var abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) { abilities.mayfly = true; changed = true; }
        if (changed) player.onUpdateAbilities();
    }

    @Override
    protected void onToggledOn(ServerPlayer player, Config config) {
        // Keybind flipped the power on: actually take off, don't just arm mayfly.
        // Without this the player would still have to double-tap jump to lift —
        // which reads as "the toggle did nothing".
        if (!config.enabled()) return;
        // Toggling on while the condition is false leaves the power armed but
        // grounded; the tick grants the flight the moment the condition is met.
        if (!config.condition().test(player)) return;
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        player.onUpdateAbilities();
        // The client cancels `flying` while on the ground (LocalPlayer.aiStep),
        // so nudge the player up a hair and force-sync the velocity — that puts
        // them airborne for the tick, letting the flying flag stick.
        if (player.onGround()) {
            var m = player.getDeltaMovement();
            player.setDeltaMovement(m.x, 0.42, m.z);
            player.hurtMarked = true;
        }
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        if (player.isCreative() || player.isSpectator()) return;
        var abilities = player.getAbilities();
        // No-op when there is nothing to strip. Load bearing now that the tick
        // path calls this on every tick the condition is false: without it we
        // would resend the abilities packet every tick, and — far worse — zero
        // fallDistance every tick, quietly making the player fall-damage-proof.
        if (!abilities.mayfly && !abilities.flying) return;
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }
}
