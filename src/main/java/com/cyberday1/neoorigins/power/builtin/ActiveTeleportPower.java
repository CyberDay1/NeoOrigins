package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class ActiveTeleportPower extends AbstractActivePower<ActiveTeleportPower.Config> {

    private static final Random RANDOM = new Random();

    public record Config(
        double range,
        int cooldownTicks,
        String mode,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 32.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("mode", "target").forGetter(Config::mode),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // teleport_range_modifier moved to action_on_event (MOD_TELEPORT_RANGE).
        double range = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_TELEPORT_RANGE,
            null, (float) config.range());
        return "random".equalsIgnoreCase(config.mode())
            ? randomTeleport(player, range)
            : targetTeleport(player, range);
    }

    private boolean targetTeleport(ServerPlayer player, double range) {
        HitResult hit = player.pick(range, 1.0f, false);
        if (hit.getType() == HitResult.Type.MISS) return false;
        Vec3 loc = hit.getLocation();
        player.teleportTo(loc.x, loc.y, loc.z);
        return true;
    }

    private boolean randomTeleport(ServerPlayer player, double range) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        Vec3 pos = player.position();
        for (int i = 0; i < 16; i++) {
            double tx = pos.x + (RANDOM.nextDouble() - 0.5) * range * 2;
            double tz = pos.z + (RANDOM.nextDouble() - 0.5) * range * 2;
            double ty = Math.max(level.getMinY(), Math.min(level.getMaxY() - 2,
                pos.y + (RANDOM.nextDouble() - 0.5) * (range / 4)));
            BlockPos target = new BlockPos((int) tx, (int) ty, (int) tz);
            if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                player.teleportTo(tx, ty, tz);
                return true;
            }
        }
        return false;
    }
}
