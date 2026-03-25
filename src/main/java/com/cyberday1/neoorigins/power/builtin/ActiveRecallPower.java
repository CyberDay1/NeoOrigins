package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Teleports the player to their bed/respawn point (or world spawn if none set). */
public class ActiveRecallPower extends AbstractActivePower<ActiveRecallPower.Config> {

    public record Config(int cooldownTicks, String type) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("cooldown_ticks", 600).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        if (!(player.level() instanceof ServerLevel sl)) return false;
        ServerPlayer.RespawnConfig respawnCfg = player.getRespawnConfig();
        BlockPos target;
        if (respawnCfg != null && respawnCfg.respawnData().dimension().equals(sl.dimension())) {
            target = respawnCfg.respawnData().pos();
        } else {
            target = sl.getRespawnData().pos();
        }
        player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        return true;
    }
}
