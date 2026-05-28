package com.cyberday1.neoorigins.compat;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player registry of numeric Apoli modifiers keyed by stat kind.
 * Used by stat hooks that don't fit the vanilla attribute system
 * ({@code modify_lava_speed} via mixin, {@code modify_xp_gain} via event).
 *
 * <p>An entry consists of {@code (powerId, operation, value)}. A power
 * registers exactly one entry on grant and unregisters by powerId on
 * revoke, mirroring how {@link CompatPlayerState} tracks event-shaped
 * powers.
 */
public final class NumericModifierRegistry {

    private NumericModifierRegistry() {}

    public enum Kind {
        LAVA_SPEED,
        XP_GAIN
    }

    public record Entry(String powerId, OriginsModifierMath.Modifier modifier) {}

    private static final Map<UUID, Map<Kind, List<Entry>>> ACTIVE = new ConcurrentHashMap<>();

    public static void register(ServerPlayer player, Kind kind, String powerId, String operation, double value) {
        ACTIVE.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Kind.class))
            .computeIfAbsent(kind, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new Entry(powerId, new OriginsModifierMath.Modifier(operation, value)));
    }

    /**
     * Bulk register variant for Apoli's plural {@code modifiers} array form.
     * Each entry shares the same {@code powerId}, so a single
     * {@link #unregister(ServerPlayer, Kind, String)} call removes them all
     * — {@code removeIf} already matches by powerId equality.
     */
    public static void register(ServerPlayer player, Kind kind, String powerId,
                                 List<OriginsModifierMath.Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return;
        var list = ACTIVE.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Kind.class))
            .computeIfAbsent(kind, k -> Collections.synchronizedList(new ArrayList<>()));
        for (OriginsModifierMath.Modifier m : modifiers) {
            list.add(new Entry(powerId, m));
        }
    }

    public static void unregister(ServerPlayer player, Kind kind, String powerId) {
        var byKind = ACTIVE.get(player.getUUID());
        if (byKind == null) return;
        var list = byKind.get(kind);
        if (list == null) return;
        list.removeIf(e -> e.powerId().equals(powerId));
        if (list.isEmpty()) byKind.remove(kind);
        if (byKind.isEmpty()) ACTIVE.remove(player.getUUID());
    }

    /**
     * Apply all registered modifiers of {@code kind} to {@code base}.
     * Returns {@code base} unchanged if the player has no active entries.
     */
    public static double apply(ServerPlayer player, Kind kind, double base) {
        var byKind = ACTIVE.get(player.getUUID());
        if (byKind == null) return base;
        var list = byKind.get(kind);
        if (list == null) return base;
        synchronized (list) {
            List<OriginsModifierMath.Modifier> mods = new ArrayList<>(list.size());
            for (Entry e : list) mods.add(e.modifier());
            return OriginsModifierMath.apply(base, mods);
        }
    }

    /**
     * Lookup variant for the LavaSpeed mixin, which can't easily hold a
     * {@link ServerPlayer} reference because the mixin target is
     * {@link net.minecraft.world.entity.LivingEntity}. The mixin passes
     * the entity's UUID; we no-op for non-players (no entries registered).
     */
    public static double applyByUuid(UUID uuid, Kind kind, double base) {
        if (uuid == null) return base;
        var byKind = ACTIVE.get(uuid);
        if (byKind == null) return base;
        var list = byKind.get(kind);
        if (list == null) return base;
        synchronized (list) {
            List<OriginsModifierMath.Modifier> mods = new ArrayList<>(list.size());
            for (Entry e : list) mods.add(e.modifier());
            return OriginsModifierMath.apply(base, mods);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
