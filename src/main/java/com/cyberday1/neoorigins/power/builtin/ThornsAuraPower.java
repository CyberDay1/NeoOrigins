package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reflects a fraction of incoming melee damage back to the attacker as direct damage.
 * return_ratio=0.25 means 25% of the damage taken is dealt back.
 * Handled via LivingIncomingDamageEvent in OriginEventHandler.
 */
public class ThornsAuraPower extends PowerType<ThornsAuraPower.Config> {

    public record Config(float returnRatio, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("return_ratio", 0.25f).forGetter(Config::returnRatio),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
