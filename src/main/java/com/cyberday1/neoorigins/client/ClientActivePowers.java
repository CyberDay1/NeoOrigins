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
    private static Set<Identifier> phaseBlockedBlocks = Set.of();
    private static List<TagKey<Block>> phaseBlockedTags = List.of();

    public static void set(Map<Identifier, Boolean> powersData, Set<String> capData) {
        powers = Map.copyOf(powersData);
        capabilities = Set.copyOf(capData);
        // Pre-parse the phase blacklist carried as "phase_blocked:<entry>"
        // capability tags (see WraithPhasePower.capabilities) so the per-frame
        // movement mixin doesn't string-parse on the hot path. An entry is
        // either a plain block id (minecraft:obsidian) or a tag reference
        // (#seer:anchor_protected) — Apoli phasing block_conditions are often
        // tag-based, so both must clamp client-side to keep the blacklist
        // symmetric with the server.
        java.util.Set<Identifier> blocked = new java.util.HashSet<>();
        java.util.List<TagKey<Block>> blockedTags = new java.util.ArrayList<>();
        for (String cap : capabilities) {
            if (cap.startsWith("phase_blocked:")) {
                String entry = cap.substring("phase_blocked:".length());
                if (entry.startsWith("#")) {
                    Identifier tagId = Identifier.tryParse(entry.substring(1));
                    if (tagId != null) blockedTags.add(TagKey.create(Registries.BLOCK, tagId));
                } else {
                    Identifier id = Identifier.tryParse(entry);
                    if (id != null) blocked.add(id);
                }
            }
        }
        phaseBlockedBlocks = Set.copyOf(blocked);
        phaseBlockedTags = List.copyOf(blockedTags);
    }

    public static void clear() {
        powers = Map.of();
        capabilities = Set.of();
        phaseBlockedBlocks = Set.of();
        phaseBlockedTags = List.of();
    }

    /**
     * Block ids the active wall-phase power may NOT pass through
     * ({@code blocked_blocks} on wraith_phase), synced as
     * {@code phase_blocked:} capability tags. Empty when no phase power is
     * active or its blacklist is empty.
     */
    public static Set<Identifier> phaseBlockedBlocks() {
        return phaseBlockedBlocks;
    }

    /**
     * Block tags the active wall-phase power may NOT pass through
     * ({@code blocked_blocks} entries written as {@code #namespace:tag}), synced
     * as {@code phase_blocked:#tag} capability tags. Empty when no phase power is
     * active or its blacklist carries no tags.
     */
    public static List<TagKey<Block>> phaseBlockedTags() {
        return phaseBlockedTags;
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
