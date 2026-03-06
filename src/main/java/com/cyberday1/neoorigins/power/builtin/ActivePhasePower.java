package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Active ability: phases the player through a solid wall in their look direction.
 * Scans forward until it finds air past a wall, then teleports there.
 * Optional hunger cost per use.
 */
public class ActivePhasePower extends AbstractActivePower<ActivePhasePower.Config> {

    public record Config(
        int maxDepth,
        int cooldownTicks,
        int hungerCost,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("max_depth", 8).forGetter(Config::maxDepth),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 start = player.position().add(0, player.getEyeHeight() * 0.5, 0);

        boolean inWall = false;
        BlockPos target = null;
        for (int i = 1; i <= config.maxDepth(); i++) {
            Vec3 check = start.add(look.scale(i));
            BlockPos bp = new BlockPos((int) Math.floor(check.x), (int) Math.floor(check.y), (int) Math.floor(check.z));
            BlockState state = level.getBlockState(bp);
            if (!inWall) {
                if (!state.isAir()) inWall = true;
            } else if (state.isAir() && level.getBlockState(bp.above()).isAir()) {
                target = bp;
                break;
            }
        }

        if (target == null) return false;

        if (config.hungerCost() > 0) {
            var food = player.getFoodData();
            food.setFoodLevel(Math.max(0, food.getFoodLevel() - config.hungerCost()));
        }
        player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        return true;
    }
}
