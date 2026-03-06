package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ActiveDashPower extends AbstractActivePower<ActiveDashPower.Config> {

    public record Config(
        float power,
        int cooldownTicks,
        boolean allowVertical,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("power", 1.5f).forGetter(Config::power),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Config::cooldownTicks),
            Codec.BOOL.optionalFieldOf("allow_vertical", false).forGetter(Config::allowVertical),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        Vec3 look = player.getLookAngle();
        Vec3 dash = config.allowVertical()
            ? look.scale(config.power())
            : new Vec3(look.x, 0.2, look.z).normalize().scale(config.power());
        player.setDeltaMovement(player.getDeltaMovement().add(dash));
        player.hurtMarked = true;
        return true;
    }
}
