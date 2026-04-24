package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Inverse of {@link BreathInFluidPower}: drains the player's air supply while
 * they are <b>not</b> submerged in the specified fluid. Useful for aquatic
 * origins that must stay wet — like a fish out of water, they gradually
 * suffocate on land.
 *
 * <p>Once the air supply reaches 0 the game applies vanilla drown damage,
 * exactly as with normal underwater suffocation.
 */
public class BreathOutOfFluidPower extends PowerType<BreathOutOfFluidPower.Config> {

    public record Config(String fluid, int drainRate, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("fluid", "water").forGetter(Config::fluid),
            Codec.INT.optionalFieldOf("drain_rate", 40).forGetter(Config::drainRate),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (!NeoOriginsConfig.isOceanOriginsDriesOutEnabled()) return;
        boolean inFluid = "lava".equalsIgnoreCase(config.fluid()) ? player.isInLava() : player.isInWater();
        if (inFluid) return;
        if (player.tickCount % config.drainRate() != 0) return;
        int air = player.getAirSupply();
        if (air > -20) {
            player.setAirSupply(air - 1);
        }
    }
}
