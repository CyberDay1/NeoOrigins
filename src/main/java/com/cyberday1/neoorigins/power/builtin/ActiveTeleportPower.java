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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class ActiveTeleportPower extends PowerType<ActiveTeleportPower.Config> {

    public static final String TYPE_ID = "active_teleport";
    private static final Random RANDOM = new Random();

    public record Config(
        double range,
        int cooldownTicks,
        String mode,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 32.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("mode", "target").forGetter(Config::mode),
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

        boolean success = "random".equalsIgnoreCase(config.mode())
            ? randomTeleport(player, config.range())
            : targetTeleport(player, config.range());

        if (success) {
            data.setCooldown(TYPE_ID, player.tickCount, config.cooldownTicks());
        }
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
            double ty = pos.y + (RANDOM.nextDouble() - 0.5) * (range / 4);
            ty = Math.max(level.getMinY(), Math.min(level.getMaxY() - 2, ty));
            BlockPos target = new BlockPos((int) tx, (int) ty, (int) tz);
            if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                player.teleportTo(tx, ty, tz);
                return true;
            }
        }
        return false;
    }
}
