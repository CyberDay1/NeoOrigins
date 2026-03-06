package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically applies bone-meal growth to nearby bonemealable blocks (crops, saplings, etc.).
 * Radius is in blocks; growthsPerInterval controls how many random crops grow per interval.
 */
public class CropGrowthAcceleratorPower extends PowerType<CropGrowthAcceleratorPower.Config> {

    public record Config(int radius, int tickInterval, int growthsPerInterval, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("radius", 4).forGetter(Config::radius),
            Codec.INT.optionalFieldOf("tick_interval", 40).forGetter(Config::tickInterval),
            Codec.INT.optionalFieldOf("growths_per_interval", 1).forGetter(Config::growthsPerInterval),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        int interval = config.tickInterval();
        if (interval <= 0 || player.tickCount % interval != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int r = config.radius();
        BlockPos center = player.blockPosition();

        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof BonemealableBlock bmb
                            && bmb.isValidBonemealTarget(level, pos, state)) {
                        candidates.add(pos.immutable());
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        int count = Math.min(config.growthsPerInterval(), candidates.size());
        // Pick random candidates using the level's random source
        for (int i = 0; i < count; i++) {
            int idx = level.random.nextInt(candidates.size());
            BlockPos pos = candidates.get(idx);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bmb
                    && bmb.isValidBonemealTarget(level, pos, state)) {
                bmb.performBonemeal(level, level.random, pos, state);
                level.levelEvent(2005, pos, 0); // bonemeal particles
            }
            candidates.remove(idx);
        }
    }
}
