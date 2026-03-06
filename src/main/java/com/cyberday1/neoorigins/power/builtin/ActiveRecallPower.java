package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Teleports the player to their bed/respawn point (or world spawn if none set). */
public class ActiveRecallPower extends PowerType<ActiveRecallPower.Config> {

    public static final String TYPE_ID = "active_recall";

    public record Config(int cooldownTicks, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("cooldown_ticks", 600).forGetter(Config::cooldownTicks),
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

        if (!(player.level() instanceof ServerLevel sl)) return;
        BlockPos worldSpawn = sl.getRespawnData().pos();
        player.teleportTo(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5);

        data.setCooldown(TYPE_ID, player.tickCount, config.cooldownTicks());
    }
}
