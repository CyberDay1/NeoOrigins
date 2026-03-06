package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.PowerUtils;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Swaps positions with the entity the player is looking at. */
public class ActiveSwapPower extends AbstractActivePower<ActiveSwapPower.Config> {

    public record Config(double range, int cooldownTicks, String type) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 20.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 80).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        Optional<LivingEntity> target = PowerUtils.findEntityInLookDirection(player, config.range());
        if (target.isEmpty()) return false;
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.get().position();
        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.get().teleportTo(playerPos.x, playerPos.y, playerPos.z);
        return true;
    }
}
