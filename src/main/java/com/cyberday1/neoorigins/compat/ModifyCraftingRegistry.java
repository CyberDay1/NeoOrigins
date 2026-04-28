package com.cyberday1.neoorigins.compat;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player registry of {@code origins:modify_crafting} entries.
 * Distinct from {@link CompatPlayerState} because modify_crafting carries
 * a stack template rather than a predicate — it doesn't fit the
 * EventPowerData shape.
 *
 * <p>Populated by {@code OriginsCompatPowerLoader.parseModifyCrafting}
 * via {@code onGranted}/{@code onRevoked} callbacks. Queried by
 * {@code CompatEventPowers.onItemCrafted} to determine whether the
 * crafting result should be swapped.
 */
public final class ModifyCraftingRegistry {

    private ModifyCraftingRegistry() {}

    public record Entry(
        String powerId,
        Identifier recipeId,
        Identifier replacementItem,
        int replacementCount,
        String replacementTag
    ) {}

    private static final Map<UUID, List<Entry>> ACTIVE = new ConcurrentHashMap<>();

    public static void register(ServerPlayer player, Entry entry) {
        ACTIVE.computeIfAbsent(player.getUUID(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(entry);
    }

    public static void unregister(ServerPlayer player, String powerId) {
        var list = ACTIVE.get(player.getUUID());
        if (list != null) {
            list.removeIf(e -> e.powerId().equals(powerId));
            if (list.isEmpty()) ACTIVE.remove(player.getUUID());
        }
    }

    public static List<Entry> getEntries(ServerPlayer player) {
        var list = ACTIVE.get(player.getUUID());
        if (list == null) return List.of();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
