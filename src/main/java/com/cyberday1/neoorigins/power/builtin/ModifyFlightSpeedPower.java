package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.NumericModifierRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Native power type that scales creative / hover flight speed — the vanilla
 * {@code Abilities.flyingSpeed} field (default {@code 0.05}) that
 * {@code Player.getFlyingSpeed()} returns while the player is flying.
 *
 * <p>This is the in-mod equivalent of Pehkui's {@code pehkui:flight} scale,
 * so packs no longer need a Pehkui dependency to retune flight speed. It
 * composes with ANY flight source (our {@code creative_flight}/{@code flight}
 * powers, vanilla creative, or another mod's mayfly) because it edits the
 * shared {@code flyingSpeed} input rather than a specific flight mechanic.
 *
 * <p>It does NOT affect elytra / fall-flying gliding — that is physics-driven
 * and never reads {@code flyingSpeed}, matching Pehkui's flight scale.
 *
 * <p>JSON shape (identical to {@code modify_lava_speed}):
 * <pre>{@code
 * {
 *   "type": "neoorigins:modify_flight_speed",
 *   "operation": "multiply_base",
 *   "value": 1.0
 * }
 * }</pre>
 *
 * <p>Numeric semantics via {@link com.cyberday1.neoorigins.compat.OriginsModifierMath}
 * over the {@code 0.05} base: {@code multiply_base 1.0} → {@code 0.10} (2×),
 * {@code multiply_base -0.5} → {@code 0.025} (½×), {@code multiply_base -0.25}
 * → {@code 0.0375} (the equivalent of Pehkui flight scale {@code 0.75}).
 * Multiple holders of this power stack the Apoli way (additions sum, then the
 * multiplier deltas sum), exactly like {@code modify_lava_speed}.
 *
 * <p>Application is push-based: the combined value is written to the player's
 * abilities on grant/revoke and synced to the client through the abilities
 * packet. Vanilla never resets {@code flyingSpeed} during normal play (game-mode
 * changes leave it untouched); a respawn rebuilds {@code Abilities} from scratch,
 * which the default {@link #onRespawn} re-applies via {@link #onGranted}.
 */
public class ModifyFlightSpeedPower extends PowerType<ModifyFlightSpeedPower.Config> {

    /** Vanilla default {@code Abilities.flyingSpeed} — the base every modifier scales. */
    private static final float BASE_FLYING_SPEED = 0.05f;

    public record Config(String operation, double value, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("operation", "multiply_base").forGetter(Config::operation),
            Codec.DOUBLE.fieldOf("value").forGetter(Config::value),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    private static String idOf() {
        ResourceLocation powerId = PowerHolder.currentDispatchId();
        return powerId != null ? powerId.toString() : "neoorigins:unknown_flight_speed";
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        String id = idOf();
        // Idempotent: drop any prior entry for this power before (re)adding, so
        // re-runs through onLogin/onRespawn (both default to onGranted) never
        // stack duplicate modifiers for the same power id.
        NumericModifierRegistry.unregister(player, NumericModifierRegistry.Kind.FLYING_SPEED, id);
        NumericModifierRegistry.register(player, NumericModifierRegistry.Kind.FLYING_SPEED,
            id, config.operation(), config.value());
        apply(player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        NumericModifierRegistry.unregister(player, NumericModifierRegistry.Kind.FLYING_SPEED, idOf());
        apply(player);
    }

    /**
     * Recompute the combined flight speed from all registered modifiers and push
     * it onto the player's abilities (synced to the client via the abilities
     * packet). With no entries left, {@link NumericModifierRegistry#apply} returns
     * the base unchanged, restoring vanilla {@code 0.05}. Only sends the packet
     * when the value actually changes.
     */
    private static void apply(ServerPlayer player) {
        float target = (float) NumericModifierRegistry.apply(
            player, NumericModifierRegistry.Kind.FLYING_SPEED, BASE_FLYING_SPEED);
        var abilities = player.getAbilities();
        if (abilities.getFlyingSpeed() != target) {
            abilities.setFlyingSpeed(target);
            player.onUpdateAbilities();
        }
    }
}
