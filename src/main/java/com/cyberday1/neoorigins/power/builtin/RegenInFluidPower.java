package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

public class RegenInFluidPower extends PowerType<RegenInFluidPower.Config> {

    public record Config(String fluid, float amountPerSecond, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("fluid", "water").forGetter(Config::fluid),
            Codec.FLOAT.optionalFieldOf("amount_per_second", 1.0f).forGetter(Config::amountPerSecond),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 20 != 0) return;
        boolean inFluid = "lava".equalsIgnoreCase(config.fluid()) ? player.isInLava() : player.isInWater();
        if (inFluid && player.getHealth() < player.getMaxHealth()) {
            player.heal(config.amountPerSecond());
        }
    }
}
