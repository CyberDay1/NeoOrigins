package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Active ability: places a specific block at the targeted surface on a cooldown.
 * Defaults to placing glowstone; block_id can be overridden in config.
 */
public class ActivePlaceBlockPower extends AbstractActivePower<ActivePlaceBlockPower.Config> {

    public record Config(
        String blockId,
        double maxDistance,
        int cooldownTicks,
        int hungerCost,
        String type
    ) implements AbstractActivePower.Config {
        @Override public int cooldownTicks() { return cooldownTicks; }
        @Override public int hungerCost() { return hungerCost; }

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("block_id", "minecraft:glowstone").forGetter(Config::blockId),
            Codec.DOUBLE.optionalFieldOf("max_distance", 5.0).forGetter(Config::maxDistance),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        var blockOpt = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(config.blockId()));
        if (blockOpt.isEmpty()) return false;
        var block = blockOpt.get();
        if (block == Blocks.AIR) return false;

        HitResult hit = player.pick(config.maxDistance(), 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return false;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos placePos = blockHit.getBlockPos().relative(blockHit.getDirection());
        ServerLevel level = (ServerLevel) player.level();
        if (!level.getBlockState(placePos).isAir()) return false;

        level.setBlock(placePos, block.defaultBlockState(), 3);
        return true;
    }
}
