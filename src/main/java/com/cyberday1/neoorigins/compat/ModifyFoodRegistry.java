package com.cyberday1.neoorigins.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Per-player registry of {@code origins:modify_food} entries.
 * Distinct from {@link CompatPlayerState} because modify_food carries
 * modifier data and an item predicate that don't fit the EventPowerData
 * shape.
 *
 * <p>Populated by {@code OriginsCompatPowerLoader.parseModifyFood}
 * via {@code onGranted}/{@code onRevoked} callbacks. Queried by
 * {@code CompatEventPowers.onFoodEaten} to apply nutrition/saturation
 * modifiers when food is consumed.
 */
public final class ModifyFoodRegistry {

    private ModifyFoodRegistry() {}

    /**
     * One modify_food power's payload.
     *
     * @param powerId            source power id (used for unregister)
     * @param itemPredicate      optional item filter — null means all food
     * @param foodModifiers      Apoli modifier list for nutrition
     * @param saturationModifiers Apoli modifier list for saturation
     */
    public record Entry(
        String powerId,
        Predicate<ItemStack> itemPredicate,
        List<OriginsModifierMath.Modifier> foodModifiers,
        List<OriginsModifierMath.Modifier> saturationModifiers
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
