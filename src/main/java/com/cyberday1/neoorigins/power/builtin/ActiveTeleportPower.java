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
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class ActiveTeleportPower extends AbstractActivePower<ActiveTeleportPower.Config> {

    private static final Random RANDOM = new Random();

    public record Config(
        double range,
        int cooldownTicks,
        String mode,
        int hungerCost,
        String type,
        String cooldownIcon,
        boolean cooldownCountdown,
        boolean alwaysShowIcon
    ) implements AbstractActivePower.Config {
        @Override public int cooldownTicks() { return cooldownTicks; }
        @Override public int hungerCost() { return hungerCost; }

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 32.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("mode", "target").forGetter(Config::mode),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("cooldown_countdown", true).forGetter(Config::cooldownCountdown),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
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
        if (!(player.level() instanceof ServerLevel level)) return false;
        // player.pick() casts from the player's WORLD eye, but Sable's clip_overwrite
        // resolves a hit on the ship against the ship's blocks in STAGING space and
        // returns that staging-space hit location (~10000,10000). The solidity check
        // below reads the ship's blocks at that staging loc (correct, since ship blocks
        // live in the host level at staging coords), and toWorld() then lifts the hit to
        // the equivalent visible-ship WORLD point so the player lands on the ship instead
        // of falling into the empty sub-level region. A hit on real terrain is a
        // world-space point and toWorld() leaves it unchanged.
        Vec3 loc = hit.getLocation();
        // For a block hit the ray stops at the face of a solid block; offset one
        // step away from the hit face so the destination is in open air rather
        // than inside the block surface.
        if (hit instanceof BlockHitResult blockHit) {
            Vec3 faceNorm = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            loc = loc.add(faceNorm.scale(0.5));
        }
        // Verify the two blocks the player will occupy (feet + head) are not solid.
        // Read at the (staging-space) loc so ship blocks are correctly seen.
        BlockPos feet = BlockPos.containing(loc.x, loc.y, loc.z);
        BlockPos head = feet.above();
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        if (feetState.isSolid() || headState.isSolid()) return false;
        Vec3 dest = com.cyberday1.neoorigins.compat.sable.SableTeleportCompat.toWorld(player, loc);
        com.cyberday1.neoorigins.compat.sable.SableTeleportCompat.detachFromDeck(player);
        TeleportEffects.teleportWithEffects(player, dest.x, dest.y, dest.z);
        return true;
    }

    private boolean randomTeleport(ServerPlayer player, double range) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        // player.position() is already the visible WORLD position (Sable re-derives a
        // rider's position from sable$plotPosition each tick), so the random scatter
        // is anchored correctly in world space; we only detach from the deck below so
        // the move isn't snapped back next tick.
        Vec3 pos = player.position();
        for (int i = 0; i < 16; i++) {
            double tx = pos.x + (RANDOM.nextDouble() - 0.5) * range * 2;
            double tz = pos.z + (RANDOM.nextDouble() - 0.5) * range * 2;
            double ty = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 2,
                pos.y + (RANDOM.nextDouble() - 0.5) * (range / 4)));
            BlockPos target = new BlockPos((int) tx, (int) ty, (int) tz);
            if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                com.cyberday1.neoorigins.compat.sable.SableTeleportCompat.detachFromDeck(player);
                TeleportEffects.teleportWithEffects(player, tx, ty, tz);
                return true;
            }
        }
        return false;
    }
}
