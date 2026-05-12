package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.NumericModifierRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Native power type that modifies the player's lava movement speed.
 * Delegates to {@link NumericModifierRegistry} with
 * {@link NumericModifierRegistry.Kind#LAVA_SPEED}, which is consumed by
 * {@link com.cyberday1.neoorigins.mixin.LivingEntityLavaSpeedMixin}.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:modify_lava_speed",
 *   "operation": "addition",
 *   "value": 0.4
 * }
 * }</pre>
 *
 * <p>An {@code addition} of {@code 0.4} pushes the vanilla lava-swim factor
 * from {@code 0.02} to {@code 0.42}, giving roughly walking-speed lava
 * movement — the same tuning the compat layer uses for Apoli packs.
 */
public class ModifyLavaSpeedPower extends PowerType<ModifyLavaSpeedPower.Config> {

    public record Config(String operation, double value, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("operation", "addition").forGetter(Config::operation),
            Codec.DOUBLE.fieldOf("value").forGetter(Config::value),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        Identifier powerId = PowerHolder.currentDispatchId();
        String id = powerId != null ? powerId.toString() : "neoorigins:unknown_lava_speed";
        NumericModifierRegistry.register(player, NumericModifierRegistry.Kind.LAVA_SPEED,
            id, config.operation(), config.value());
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        Identifier powerId = PowerHolder.currentDispatchId();
        String id = powerId != null ? powerId.toString() : "neoorigins:unknown_lava_speed";
        NumericModifierRegistry.unregister(player, NumericModifierRegistry.Kind.LAVA_SPEED, id);
    }
}
