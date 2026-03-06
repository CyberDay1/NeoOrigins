package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ActiveDashPower extends PowerType<ActiveDashPower.Config> {

    public static final String TYPE_ID = "active_dash";

    public record Config(
        float power,
        int cooldownTicks,
        boolean allowVertical,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("power", 1.5f).forGetter(Config::power),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Config::cooldownTicks),
            Codec.BOOL.optionalFieldOf("allow_vertical", false).forGetter(Config::allowVertical),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public boolean isActive() { return true; }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (data.isOnCooldown(TYPE_ID, player.tickCount)) return;

        Vec3 look = player.getLookAngle();
        Vec3 dash = config.allowVertical()
            ? look.scale(config.power())
            : new Vec3(look.x, 0.2, look.z).normalize().scale(config.power());

        player.setDeltaMovement(player.getDeltaMovement().add(dash));
        player.hurtMarked = true;

        data.setCooldown(TYPE_ID, player.tickCount, config.cooldownTicks());
    }
}
