package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@code blocked_blocks} blacklist a phase power may not pass through,
 * parsed once and shared by both ends (#109).
 *
 * <p>The server reads the raw {@code blocked_blocks} list; the client rebuilds it
 * from {@code phase_blocked:} capability tags. Those were two separate parsers,
 * so any difference between them was a client/server disagreement about where a
 * wraith may stand. {@link #parse} and {@link #overlaps} are now the single
 * implementation of both the matching rule and the hitbox geometry.
 */
// hub: neoorigins/phase-blacklist-parity.md
public final class PhaseBlacklist {

    /** Inset applied to the hitbox before testing overlap, on both ends. */
    private static final double OVERLAP_INSET = 0.05;

    public static final PhaseBlacklist EMPTY = new PhaseBlacklist(Set.of(), List.of());

    private final Set<ResourceLocation> ids;
    private final List<TagKey<Block>> tags;

    private PhaseBlacklist(Set<ResourceLocation> ids, List<TagKey<Block>> tags) {
        this.ids = ids;
        this.tags = tags;
    }

    /**
     * Splits entries into literal block ids and block tags. An entry is a tag
     * reference when it starts with {@code #} ({@code #seer:anchor_protected});
     * Apoli phasing block_conditions are frequently tag-based. Unparseable and
     * blank entries are dropped with a warning rather than aborting — a throw
     * here once left {@code noPhysics} unset for the whole tick.
     */
    public static PhaseBlacklist parse(Collection<String> entries) {
        Set<ResourceLocation> ids = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) continue;
            String entry = raw.trim();
            boolean isTag = entry.charAt(0) == '#';
            String body = isTag ? entry.substring(1) : entry;
            ResourceLocation id = ResourceLocation.tryParse(body);
            if (id == null || id.getPath().isEmpty()) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[wraith_phase] ignoring unparseable blocked_blocks entry '{}'", raw);
                continue;
            }
            if (isTag) {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
                if (!tags.contains(tag)) tags.add(tag);
            } else {
                ids.add(id);
            }
        }
        return ids.isEmpty() && tags.isEmpty() ? EMPTY
            : new PhaseBlacklist(Set.copyOf(ids), List.copyOf(tags));
    }

    /** Parses the {@code phase_blocked:<entry>} capability tags synced to the client. */
    public static PhaseBlacklist fromCapabilities(Collection<String> capabilities) {
        List<String> entries = new ArrayList<>();
        for (String cap : capabilities) {
            if (cap != null && cap.startsWith(CAPABILITY_PREFIX)) {
                entries.add(cap.substring(CAPABILITY_PREFIX.length()));
            }
        }
        return parse(entries);
    }

    /** Capability-tag prefix carrying one blacklist entry to the client. */
    public static final String CAPABILITY_PREFIX = "phase_blocked:";

    public boolean isEmpty() {
        return ids.isEmpty() && tags.isEmpty();
    }

    public Set<ResourceLocation> ids() {
        return ids;
    }

    public List<TagKey<Block>> tags() {
        return tags;
    }

    /**
     * Whether {@code Player.tick()}'s spectator reset of {@code noPhysics} should
     * be undone for a phasing player — the single rule behind both branches of
     * {@code PlayerPhaseOverrideMixin}, which is where the two ends used to differ.
     *
     * <p>Full noclip ({@code no_physics}, PhantomForm) ignores the blacklist by
     * design. {@code wall_phase} does not: inside a blacklisted block the flag
     * must stay off so ordinary collision pushes the player out (#109).
     */
    public static boolean restoresNoPhysics(
            boolean fullNoclip, boolean wallPhase, boolean inBlockedBlock) {
        return fullNoclip || (wallPhase && !inBlockedBlock);
    }

    /** True if {@code state} is blacklisted by id or by tag. */
    public boolean matches(BlockState state) {
        if (isEmpty()) return false;
        if (!ids.isEmpty() && ids.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            return true;
        }
        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    /**
     * True if {@code entity}'s hitbox overlaps a blacklisted block. Shared so the
     * server's "don't restore noPhysics here" test and the client's mirror of it
     * cannot disagree on the inset or the scan bounds.
     */
    public boolean overlaps(Entity entity) {
        if (isEmpty()) return false;
        AABB box = entity.getBoundingBox().deflate(OVERLAP_INSET);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.isAir() && matches(state)) return true;
        }
        return false;
    }
}
