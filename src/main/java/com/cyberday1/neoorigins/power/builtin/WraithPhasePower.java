package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraith Phase -- toggleable spectral phasing.
 *
 * <p>When active the player walks through solid blocks horizontally.
 * While inside a solid block, flight kicks in (jump = up, shift = down).
 * On the surface the player walks on the ground normally.
 *
 * <p>Certain blocks (obsidian, bedrock by default) cannot be phased through.
 * Phasing drains hunger.
 */
public class WraithPhasePower extends AbstractTogglePower<WraithPhasePower.Config> {

    private static final Set<String> CAPS = Set.of("wall_phase");

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }

    public record Config(
        List<String> blockedBlocks,
        float exhaustionPerTick,
        boolean alwaysOn,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("blocked_blocks", List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"))
                .forGetter(Config::blockedBlocks),
            Codec.FLOAT.optionalFieldOf("exhaustion_per_tick", 0.15F).forGetter(Config::exhaustionPerTick),
            Codec.BOOL.optionalFieldOf("always_on", false).forGetter(Config::alwaysOn),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    /** When always_on, the power is passive — no skill key slot. */
    @Override
    public boolean isActivePower(Config config) { return !config.alwaysOn(); }

    /** When always_on, never report as toggled off. */
    @Override
    public boolean isToggledOff(ServerPlayer player, Config config) {
        if (config.alwaysOn()) return false;
        return super.isToggledOff(player, config);
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        // --- blocked-block check ---
        Set<ResourceLocation> blocked = new HashSet<>();
        for (String id : config.blockedBlocks()) {
            blocked.add(ResourceLocation.parse(id));
        }

        AABB box = player.getBoundingBox().deflate(0.05);
        boolean inBlockedBlock = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir()) {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (blocked.contains(blockId)) {
                    inBlockedBlock = true;
                    break;
                }
            }
        }

        // Noclip -- always on so the server accepts client-predicted phased
        // positions. Disabled when inside a blocked block so vanilla collision
        // pushes the player out.
        player.noPhysics = !inBlockedBlock;

        boolean insideSolid = isInsideSolid(player);
        boolean crouching = player.isShiftKeyDown();

        if (insideSolid || crouching) {
            // Inside a block OR holding shift on the surface -- enable flight
            // for vertical control (jump = up, shift = down). Holding shift
            // on the surface lets the player phase downward into the ground.
            var abilities = player.getAbilities();
            boolean changed = false;
            if (!abilities.mayfly)  { abilities.mayfly  = true;  changed = true; }
            if (!abilities.flying)  { abilities.flying  = true;  changed = true; }
            if (changed) player.onUpdateAbilities();
        } else {
            // On surface, not crouching -- disable flight, walk normally.
            var abilities = player.getAbilities();
            if (abilities.flying && !player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
            }
            // Zero server-side vertical velocity while noPhysics is true.
            // The client handles actual movement via the mixin; the server
            // just needs to accept client positions. Without this, the
            // server's Entity.move() accumulates velocity (both up from
            // jumps and down from gravity) with no collision to stop it,
            // causing the player to float or sink on the server side.
            Vec3 vel = player.getDeltaMovement();
            if (vel.y != 0) {
                player.setDeltaMovement(vel.x, 0, vel.z);
            }
        }

        // Hunger drain while phasing through solid blocks
        if (insideSolid) {
            player.causeFoodExhaustion(config.exhaustionPerTick());
        }

        player.fallDistance = 0.0F;
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        player.noPhysics = false;
        var abilities = player.getAbilities();
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        // Always sync — even if flying was already false, mayfly may still
        // be true on the client from a previous in-block sync. Without this
        // the client keeps mayfly=true and can double-tap jump to fly.
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }

    /**
     * Returns true if any block overlapping the player's hitbox is solid
     * (non-air with a collision shape). Used to detect active phasing.
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
