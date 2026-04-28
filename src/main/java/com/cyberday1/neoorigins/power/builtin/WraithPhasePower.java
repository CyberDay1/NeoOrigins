package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraith Phase — the player passively phases through most blocks.
 * Configurable blocklist prevents phasing through certain blocks (obsidian
 * by default). Drains hunger while the player is inside solid blocks.
 *
 * <p>Unlike {@code PhantomFormPower}, this is a passive (always-on) power
 * with no flight, no invisibility, and no toggle. The player walks on the
 * ground normally but can walk through walls.
 */
public class WraithPhasePower extends PowerType<WraithPhasePower.Config> {

    public record Config(
        List<String> blockedBlocks,
        float exhaustionPerTick,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("blocked_blocks", List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"))
                .forGetter(Config::blockedBlocks),
            Codec.FLOAT.optionalFieldOf("exhaustion_per_tick", 0.15F).forGetter(Config::exhaustionPerTick),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        // Resolve the blocked block set (cheap — small list, string compare)
        Set<Identifier> blocked = new HashSet<>();
        for (String id : config.blockedBlocks()) {
            blocked.add(Identifier.parse(id));
        }

        // Check if the player is currently inside a blocked block
        AABB box = player.getBoundingBox().deflate(0.05);
        boolean inBlockedBlock = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir()) {
                Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (blocked.contains(blockId)) {
                    inBlockedBlock = true;
                    break;
                }
            }
        }

        if (inBlockedBlock) {
            // Can't phase through blocked blocks — disable noclip so
            // vanilla collision pushes the player out
            player.noPhysics = false;
        } else {
            player.noPhysics = true;
        }

        // Drain hunger while inside solid blocks (phasing)
        if (isInsideSolid(player)) {
            player.causeFoodExhaustion(config.exhaustionPerTick());
        }

        // Prevent fall damage accumulation while phasing
        player.fallDistance = 0.0F;
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        player.noPhysics = false;
    }

    /**
     * Returns true if any block overlapping the player's hitbox is solid
     * (non-air, non-fluid-only). Used to detect active phasing for hunger drain.
     */
    private static boolean isInsideSolid(ServerPlayer player) {
        AABB box = player.getBoundingBox().deflate(0.1);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY + 0.1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY - 0.1, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(player.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
