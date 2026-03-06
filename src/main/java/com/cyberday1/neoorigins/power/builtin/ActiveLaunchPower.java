package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Launches the player straight upward — great paired with flight or elytra_boost for vertical takeoff. */
public class ActiveLaunchPower extends PowerType<ActiveLaunchPower.Config> {

    public static final String TYPE_ID = "active_launch";

    public record Config(float power, int cooldownTicks, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("power", 1.8f).forGetter(Config::power),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(Config::cooldownTicks),
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

        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.x, config.power(), current.z);
        player.hurtMarked = true;

        data.setCooldown(TYPE_ID, player.tickCount, config.cooldownTicks());
    }
}
