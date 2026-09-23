package com.cyberday1.neoorigins.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of the local player's currently-granted powers and the union
 * of their capability tags.
 *
 * Populated by {@code SyncActivePowersPayload} on login, respawn, origin change,
 * and toggle change. Queried by client-predicted movement mixins (wall-climb,
 * flight, climbing) and the 2.0 origin editor / power tester GUI.
 *
 * Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientActivePowers {

    private static Map<Identifier, Boolean> powers = Map.of();
    private static Set<String> capabilities = Set.of();
    private static com.cyberday1.neoorigins.power.builtin.PhaseBlacklist phaseBlacklist =
        com.cyberday1.neoorigins.power.builtin.PhaseBlacklist.EMPTY;

    public static void set(Map<Identifier, Boolean> powersData, Set<String> capData) {
        powers = Map.copyOf(powersData);
        capabilities = Set.copyOf(capData);
        // Pre-parse the phase blacklist carried as "phase_blocked:<entry>"
        // capability tags (see WraithPhasePower.capabilities) so the per-frame
        // movement mixin doesn't string-parse on the hot path. Parsing is the
        // server's own, so the two ends cannot disagree about the blacklist.
        phaseBlacklist = com.cyberday1.neoorigins.power.builtin.PhaseBlacklist
            .fromCapabilities(capabilities);
    }

    public static void clear() {
        powers = Map.of();
        capabilities = Set.of();
        phaseBlacklist = com.cyberday1.neoorigins.power.builtin.PhaseBlacklist.EMPTY;
    }

    /**
     * The local player's phase blacklist, parsed by the server's own parser.
     * Used by the client movement clamp and by the {@code noPhysics} mirror in
     * {@code PlayerPhaseOverrideMixin}.
     */
    public static com.cyberday1.neoorigins.power.builtin.PhaseBlacklist phaseBlacklist() {
        return phaseBlacklist;
    }

    /**
     * Block ids the active wall-phase power may NOT pass through
     * ({@code blocked_blocks} on wraith_phase), synced as
     * {@code phase_blocked:} capability tags. Empty when no phase power is
     * active or its blacklist is empty.
     */
    public static Set<Identifier> phaseBlockedBlocks() {
        return phaseBlacklist.ids();
    }

    /**
     * Block tags the active wall-phase power may NOT pass through
     * ({@code blocked_blocks} entries written as {@code #namespace:tag}), synced
     * as {@code phase_blocked:#tag} capability tags. Empty when no phase power is
     * active or its blacklist carries no tags.
     */
    public static List<TagKey<Block>> phaseBlockedTags() {
        return phaseBlacklist.tags();
    }

    /** True if the local player has power {@code id} granted, regardless of toggle state. */
    public static boolean hasPower(Identifier id) {
        return powers.containsKey(id);
    }

    /**
     * True if the local player has power {@code id} granted AND it's either non-toggleable
     * or toggled on. This is the query client-predicted behavior should use.
     */
    public static boolean isActive(Identifier id) {
        return Boolean.TRUE.equals(powers.get(id));
    }

    /**
     * True if any active power on the local player grants the given capability tag.
     * This is the preferred query for client-predicted mixins — they should ask
     * "do I have wall_climb?" rather than "do I have power X?".
     */
    public static boolean hasCapability(String tag) {
        return capabilities.contains(tag);
    }

    /** Unmodifiable view of the full map — for debug HUDs and the power-tester screen. */
    public static Map<Identifier, Boolean> all() {
        return Collections.unmodifiableMap(powers);
    }

    /** Unmodifiable view of active capability tags. */
    public static Set<String> activeCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    /**
     * True if {@code player} is the client's local player. This check lives in
     * this client-only class on purpose: common-side mixins (applied on both the
     * client and the dedicated server) must NOT reference the client-only
     * {@link net.minecraft.client.player.LocalPlayer} type directly — naming it
     * in a local, cast, or {@code instanceof} makes Mixin load the class during
     * frame computation when weaving the method, which throws "invalid dist
     * DEDICATED_SERVER" and fails the whole mixin apply. Routing the check
     * through an {@code invokestatic} call here keeps the client type off the
     * server's verification path (only loaded when the client branch runs).
     */
    public static boolean isLocalPlayer(net.minecraft.world.entity.player.Player player) {
        return player == net.minecraft.client.Minecraft.getInstance().player;
    }

    private ClientActivePowers() {}
}
