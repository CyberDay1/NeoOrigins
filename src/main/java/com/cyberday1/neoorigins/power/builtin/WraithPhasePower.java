package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Cached parsed blocked-block matchers, keyed by the string list to avoid
     * per-tick allocation and per-tick registry lookups.
     *
     * <p>Each entry in {@code blocked_blocks} may be either a plain block id
     * ({@code minecraft:obsidian}) or a tag reference ({@code #seer:anchor_protected}).
     * Apoli phasing block_conditions are frequently tag-based (the Seer origin's
     * {@code seer:anchor_protected}), and the compat translator carries those over
     * as {@code #tag} entries. Plain {@link ResourceLocation#parse} chokes on the
     * leading {@code #}, so the entries are split here into a literal-id set and a
     * tag-key set; parsing is wrapped so one malformed entry never aborts the whole
     * tick (which previously left {@code noPhysics} unset and rubber-banded the
     * player back out of the wall on dedicated servers).
     */
    private static final Map<List<String>, BlockedMatcher> BLOCKED_CACHE = new ConcurrentHashMap<>();

    /** Resolved blocked-block matcher: literal block ids plus block tags. */
    private record BlockedMatcher(Set<ResourceLocation> ids, List<TagKey<Block>> tags) {
        boolean matches(BlockState state) {
            if (ids.isEmpty() && tags.isEmpty()) return false;
            if (!ids.isEmpty()) {
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (ids.contains(key)) return true;
            }
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return false;
        }
    }

    private static BlockedMatcher resolveBlocked(List<String> list) {
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        java.util.List<TagKey<Block>> tags = new java.util.ArrayList<>();
        for (String raw : list) {
            if (raw == null || raw.isEmpty()) continue;
            try {
                if (raw.charAt(0) == '#') {
                    tags.add(TagKey.create(Registries.BLOCK, ResourceLocation.parse(raw.substring(1))));
                } else {
                    ids.add(ResourceLocation.parse(raw));
                }
            } catch (RuntimeException e) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[wraith_phase] ignoring unparseable blocked_blocks entry '{}': {}", raw, e.getMessage());
            }
        }
        return new BlockedMatcher(Set.copyOf(ids), List.copyOf(tags));
    }

    /**
     * Cached capability sets per blocked-block list. Besides {@code wall_phase}
     * the set carries one {@code phase_blocked:<block id>} tag per blacklisted
     * block — that's how the client-predicted movement mixin
     * ({@code LocalPlayerNoPhysicsMixin}) learns the blacklist, since power
     * bodies are never synced to the client but capability tags are.
     */
    private static final Map<List<String>, Set<String>> CAPS_CACHE = new ConcurrentHashMap<>();

    /**
     * Players whose hitbox currently overlaps a blacklisted block, refreshed
     * every {@link #tickEffect}. Consulted by {@code PlayerPhaseOverrideMixin}
     * so it does NOT force {@code noPhysics} back on while the player is inside
     * a blocked block — leaving vanilla collision free to push them out, which
     * is the server-side half of the blacklist.
     */
    private static final Set<java.util.UUID> IN_BLOCKED_BLOCK = ConcurrentHashMap.newKeySet();

    /** True if {@code player}'s last phase tick found them overlapping a blacklisted block. */
    public static boolean isInBlockedBlock(net.minecraft.world.entity.player.Player player) {
        return IN_BLOCKED_BLOCK.contains(player.getUUID());
    }

    @Override
    public Set<String> capabilities(Config config) {
        return CAPS_CACHE.computeIfAbsent(config.blockedBlocks(), list -> {
            java.util.Set<String> caps = new java.util.HashSet<>();
            caps.add("wall_phase");
            for (String block : list) caps.add("phase_blocked:" + block);
            return Set.copyOf(caps);
        });
    }

    public record Config(
        List<String> blockedBlocks,
        float exhaustionPerTick,
        boolean alwaysOn,
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, HudIconConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("blocked_blocks", List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"))
                .forGetter(Config::blockedBlocks),
            Codec.FLOAT.optionalFieldOf("exhaustion_per_tick", 0.15F).forGetter(Config::exhaustionPerTick),
            Codec.BOOL.optionalFieldOf("always_on", false).forGetter(Config::alwaysOn),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
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
        // --- blocked-block check (cached to avoid per-tick allocation) ---
        BlockedMatcher blocked = BLOCKED_CACHE.computeIfAbsent(config.blockedBlocks(),
            WraithPhasePower::resolveBlocked);

        AABB box = player.getBoundingBox().deflate(0.05);
        boolean inBlockedBlock = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir() && blocked.matches(state)) {
                inBlockedBlock = true;
                break;
            }
        }

        // Noclip -- always on so the server accepts client-predicted phased
        // positions. Disabled when inside a blocked block so vanilla collision
        // pushes the player out. PlayerPhaseOverrideMixin consults the
        // IN_BLOCKED_BLOCK flag so its post-tick restore doesn't undo this.
        if (inBlockedBlock) {
            IN_BLOCKED_BLOCK.add(player.getUUID());
        } else {
            IN_BLOCKED_BLOCK.remove(player.getUUID());
        }
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
            if (changed) {
                player.onUpdateAbilities();
                if (com.cyberday1.neoorigins.config.AdminConfig.isDebugHud()) {
                    com.cyberday1.neoorigins.NeoOrigins.LOGGER.info(
                        "[debug_hud] wraith_phase GRANT flight for {} (insideSolid={}, crouching={})",
                        player.getName().getString(), insideSolid, crouching);
                }
            }
        } else {
            // On surface, not crouching -- disable flight, walk normally.
            // Check mayfly as well as flying: the client can toggle flying off
            // (double-tap space) between our ticks, and if the player exits the
            // block in that same window flying arrives false while mayfly is
            // still true. Clearing only on flying left mayfly latched on, so
            // spamming jump (= double-taps) on the surface re-entered vanilla
            // flight. Clear whenever either flag is set.
            var abilities = player.getAbilities();
            // Don't fight other legitimate mayfly grantors: creative/spectator
            // own their flight, and an active PhantomFormPower re-grants
            // mayfly/flying every tick — clearing here would ping-pong
            // abilities packets with it every tick.
            if (!player.isCreative() && !player.isSpectator()
                    && !PhantomFormPower.isActive(player)) {
                if (abilities.flying || abilities.mayfly) {
                    abilities.mayfly = false;
                    abilities.flying = false;
                    player.onUpdateAbilities();
                    if (com.cyberday1.neoorigins.config.AdminConfig.isDebugHud()) {
                        com.cyberday1.neoorigins.NeoOrigins.LOGGER.info(
                            "[debug_hud] wraith_phase CLEAR flight for {} (surface, not crouching)",
                            player.getName().getString());
                    }
                } else if (player.tickCount % 20 == 0) {
                    // Belt-and-braces resync: even when the server-side flags
                    // are already clear, the CLIENT's mayfly can be latched
                    // true from an earlier in-block sync that raced a
                    // double-tap-space abilities packet. The server then sees
                    // nothing to change and never re-sends, leaving the client
                    // free to enter vanilla flight by spamming jump. Pushing
                    // the (clear) abilities once a second while phased on the
                    // surface stomps any such client-side latch.
                    player.onUpdateAbilities();
                }
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
        IN_BLOCKED_BLOCK.remove(player.getUUID());
        player.noPhysics = false;
        var abilities = player.getAbilities();
        if (!player.isCreative() && !player.isSpectator()
                && !PhantomFormPower.isActive(player)) {
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
