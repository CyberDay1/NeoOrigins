package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
 * While inside a solid block, vertical control comes from VELOCITY nudges
 * (jump = up, sneak = down, neither = slow settle) — NOT creative flight.
 * On the surface the player walks on the ground normally.
 *
 * <p>Certain blocks (obsidian, bedrock by default) cannot be phased through.
 * Phasing drains hunger.
 *
 * <p>History: this power used to grant {@code Abilities.mayfly}/{@code flying}
 * for vertical control while phasing. That let players spam jump to ascend
 * freely through terrain, stuttered movement (the server zeroed Y velocity
 * while the client flew), and desynced server vs client positions enough to
 * make survival block placement raytrace to the wrong spot ("ghost blocks").
 * Vertical phasing is now driven entirely by mirrored velocity nudges on both
 * the server here and {@code LocalPlayerNoPhysicsMixin} on the client, so the
 * two agree on position and no creative flight is ever armed.
 */
public class WraithPhasePower extends AbstractTogglePower<WraithPhasePower.Config> {

    /**
     * Vertical velocity (blocks/tick) applied while phasing in the noclip
     * state. Mirrored verbatim in {@code LocalPlayerNoPhysicsMixin} so the
     * client prediction and the server agree on Y movement. No creative
     * flight is involved — these are the ONLY source of vertical motion while
     * noclipping.
     */
    public static final double PHASE_UP_VELOCITY = 0.18;
    public static final double PHASE_DOWN_VELOCITY = -0.18;
    /** Slow neutral sink when neither jump nor sneak is held (not a hard zero). */
    public static final double PHASE_SETTLE_VELOCITY = -0.04;

    /**
     * Resolves the intended phasing Y velocity from jump/sneak intent. Shared
     * by the server tick and (conceptually) the client mixin so both ends pick
     * the same vertical speed. {@code jump} beats {@code sneak} if somehow both
     * are held.
     */
    public static double phaseVerticalVelocity(boolean jump, boolean sneak) {
        if (jump) return PHASE_UP_VELOCITY;
        if (sneak) return PHASE_DOWN_VELOCITY;
        return PHASE_SETTLE_VELOCITY;
    }

    /**
     * Cached parsed blocked-block matchers, keyed by the string list to avoid
     * per-tick allocation and per-tick registry lookups.
     *
     * <p>Each entry in {@code blocked_blocks} may be either a plain block id
     * ({@code minecraft:obsidian}) or a tag reference ({@code #seer:anchor_protected}).
     * Apoli phasing block_conditions are frequently tag-based (the Seer origin's
     * {@code seer:anchor_protected}), and the compat translator carries those over
     * as {@code #tag} entries. Plain {@link Identifier#parse} chokes on the
     * leading {@code #}, so the entries are split here into a literal-id set and a
     * tag-key set; parsing is wrapped so one malformed entry never aborts the whole
     * tick (which previously left {@code noPhysics} unset and rubber-banded the
     * player back out of the wall on dedicated servers).
     */
    private static final Map<List<String>, BlockedMatcher> BLOCKED_CACHE = new ConcurrentHashMap<>();

    /** Resolved blocked-block matcher: literal block ids plus block tags. */
    private record BlockedMatcher(Set<Identifier> ids, List<TagKey<Block>> tags) {
        boolean matches(BlockState state) {
            if (ids.isEmpty() && tags.isEmpty()) return false;
            if (!ids.isEmpty()) {
                Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (ids.contains(key)) return true;
            }
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return false;
        }
    }

    private static BlockedMatcher resolveBlocked(List<String> list) {
        java.util.Set<Identifier> ids = new java.util.HashSet<>();
        java.util.List<TagKey<Block>> tags = new java.util.ArrayList<>();
        for (String raw : list) {
            if (raw == null || raw.isEmpty()) continue;
            try {
                if (raw.charAt(0) == '#') {
                    tags.add(TagKey.create(Registries.BLOCK, Identifier.parse(raw.substring(1))));
                } else {
                    ids.add(Identifier.parse(raw));
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
        // Sneak only counts as "phase down" when there is solid ground
        // directly beneath the feet to sink INTO. Plain isShiftKeyDown() let a
        // phasing player hold shift in OPEN AIR and previously armed creative
        // flight, which let them "jump off air" and descend faster than gravity
        // — the exact symptom the Seer tester reported. Gating on a solid block
        // below restores the intended "phase down into the ground" behaviour
        // (seerfix4 guard) without arming anything midair.
        boolean sneakPhaseDown = player.isShiftKeyDown() && hasSolidBelow(player);

        // Drop any creative flight a previous (flight-based) version of this
        // power may have latched on — both server and client side — so an
        // in-progress test session recovers without relogging. We never grant
        // mayfly/flying anymore; vertical control is pure velocity. Respect the
        // legitimate flight owners: creative/spectator and an active
        // PhantomFormPower (which re-grants flight every tick).
        if (!player.isCreative() && !player.isSpectator()
                && !PhantomFormPower.isActive(player)) {
            var abilities = player.getAbilities();
            if (abilities.flying || abilities.mayfly) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
                if (com.cyberday1.neoorigins.config.AdminConfig.isDebugHud()) {
                    com.cyberday1.neoorigins.NeoOrigins.LOGGER.info(
                        "[debug_hud] wraith_phase CLEAR stale flight for {}",
                        player.getName().getString());
                }
            } else if (player.tickCount % 20 == 0) {
                // Belt-and-braces resync: the CLIENT's mayfly can stay latched
                // true from an old in-block grant that raced a double-tap-space
                // abilities packet. The server sees nothing to change and never
                // re-sends, leaving the client able to enter vanilla flight by
                // spamming jump. Re-push the (clear) abilities once a second
                // while phased to stomp any such client-side latch.
                player.onUpdateAbilities();
            }
        }

        // Vertical movement is now driven entirely by the CLIENT
        // (LocalPlayerNoPhysicsMixin's velocity nudges: jump = up, sneak = down,
        // neither = slow settle). On 1.21.1 the server cannot read an on-foot
        // player's jump intent — ServerPlayer.setPlayerInput only records the
        // jump flag while riding a vehicle — so the server must NOT try to
        // recompute the vertical velocity itself; doing so would diverge from
        // the client and re-introduce the desync that caused ghost blocks.
        //
        // Under noPhysics the server snaps to the client's reported position
        // (ServerGamePacketListenerImpl.handleMovePlayer -> absMoveTo), so the
        // client position IS authoritative here. All the server has to do is
        // stop its own Entity.move() (run from travel() each tick) from
        // accumulating a runaway Y velocity between move packets — there is no
        // collision under noPhysics to arrest it. Damping the server-side Y to
        // zero each tick leaves the per-tick client delta to carry the player;
        // absMoveTo then reconciles the exact position. This keeps server and
        // client agreeing on where the player is, which is what makes survival
        // block placement raytrace correctly (no ghost blocks) without granting
        // any flight.
        Vec3 vel = player.getDeltaMovement();
        if (vel.y != 0) {
            player.setDeltaMovement(vel.x, 0, vel.z);
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

    /**
     * Returns true if there is a solid (collidable) block directly beneath the
     * player's feet — i.e. ground they could phase down INTO. Used to gate the
     * sneak-to-phase-down velocity nudge so it never engages in open air
     * (seerfix4). Scans a thin slab just below the feet across the full
     * horizontal footprint, so it also catches the player standing on the
     * lip/edge of a block.
     */
    private static boolean hasSolidBelow(ServerPlayer player) {
        AABB box = player.getBoundingBox().deflate(0.05);
        double feetY = box.minY;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, feetY - 0.5, box.minZ),
                BlockPos.containing(box.maxX, feetY - 0.01, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(player.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
