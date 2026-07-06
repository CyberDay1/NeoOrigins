package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Teleports the player to their bed/respawn point (or world spawn if none set). */
public class ActiveRecallPower extends AbstractActivePower<ActiveRecallPower.Config> {

    public record Config(int cooldownTicks, String type, String cooldownIcon, boolean cooldownCountdown,
        boolean alwaysShowIcon) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("cooldown_ticks", 600).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("cooldown_countdown", true).forGetter(Config::cooldownCountdown),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        if (!(player.level() instanceof ServerLevel)) return false;
        // Resolve a SAFE standing spot at the player's respawn (bed / anchor) across
        // dimensions. Passing consumeSpawnBlock=true preserves respawn-anchor charge —
        // this is a repeatable recall, not a death respawn. When no valid respawn exists
        // this falls back to the overworld shared spawn (missingRespawnBlock) with a
        // usable newLevel/position, so we can always teleport.
        TeleportTransition transition =
            player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING);
        ServerLevel targetLevel = transition.newLevel();
        Vec3 pos = transition.position();
        TeleportEffects.teleportWithEffects(player, targetLevel, pos.x, pos.y, pos.z);
        return true;
    }
}
