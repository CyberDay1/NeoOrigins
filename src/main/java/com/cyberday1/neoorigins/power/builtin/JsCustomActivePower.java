package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.kubejs.JsPowerRegistry;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keybind-active power whose {@code onUse} (and optional grant/revoke
 * hooks) are supplied entirely by a JS handler registered via
 * {@code NeoOrigins.registerActivePower(jsId, handler)}. Cooldown and
 * hunger cost behave exactly like every other {@link AbstractActivePower}
 * — they're read from the standard JSON fields and consumed only when
 * the JS {@code onUse} returns {@code true}.
 */
public class JsCustomActivePower extends AbstractActivePower<JsCustomActivePower.Config> {

    public record Config(
        String jsId,
        int cooldownTicks,
        int hungerCost,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("js_id").forGetter(Config::jsId),
            Codec.INT.optionalFieldOf("cooldown_ticks", 20).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        return JsPowerRegistry.invokeOnUse(config.jsId(), player);
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        JsPowerRegistry.invokeActiveOnGranted(config.jsId(), player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        JsPowerRegistry.invokeActiveOnRevoked(config.jsId(), player);
    }
}
