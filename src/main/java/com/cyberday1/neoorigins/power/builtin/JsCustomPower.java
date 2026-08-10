package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.kubejs.JsPowerRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passive power whose lifecycle hooks are supplied entirely by a JS handler
 * registered via {@code NeoOrigins.registerPower(jsId, handler)}. The JSON
 * power declares which handler to invoke via the {@code js_id} field; if no
 * handler is registered (e.g., KubeJS absent), every hook is a silent no-op.
 *
 * <p>This wrapper is what makes new power types authorable from JS without
 * dynamic registry mutation — one stable PowerType slot dispatches to any
 * number of JS-defined behaviors keyed by {@code js_id}.
 */
public class JsCustomPower extends PowerType<JsCustomPower.Config> {

    public record Config(String jsId, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("js_id").forGetter(Config::jsId),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        JsPowerRegistry.invokeOnGranted(config.jsId(), player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        JsPowerRegistry.invokeOnRevoked(config.jsId(), player);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        JsPowerRegistry.invokeOnTick(config.jsId(), player);
    }
}
