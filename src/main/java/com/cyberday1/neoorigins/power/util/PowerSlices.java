package com.cyberday1.neoorigins.power.util;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One client-side set fed by several powers. Each power id owns a slice per
 * player; the client only ever gets the merge of all slices, and only when the
 * merge changes. Server thread only.
 */
public final class PowerSlices<V> {

    private static final ResourceLocation UNKEYED = ResourceLocation.fromNamespaceAndPath("neoorigins", "unkeyed");

    private final Map<UUID, Map<ResourceLocation, V>> slices = new ConcurrentHashMap<>();
    private final Map<UUID, V> lastSent = new ConcurrentHashMap<>();
    private final Function<Collection<V>, V> merge;

    /** {@code merge} folds slices in power-id order, so ties resolve the same way every tick. */
    public PowerSlices(Function<Collection<V>, V> merge) {
        this.merge = merge;
    }

    /** The slice key for the power being dispatched right now. */
    public static ResourceLocation currentKey() {
        ResourceLocation id = PowerHolder.currentDispatchId();
        return id != null ? id : UNKEYED;
    }

    /** Sets this power's slice. Returns the merged set to send, or null when the client already has it. */
    public V put(UUID player, ResourceLocation power, V slice) {
        slices.computeIfAbsent(player, k -> new TreeMap<>()).put(power, slice);
        return publish(player);
    }

    /** Drops this power's slice. Returns the merged set to send, or null when nothing changed. */
    public V remove(UUID player, ResourceLocation power) {
        Map<ResourceLocation, V> own = slices.get(player);
        if (own == null || !own.containsKey(power)) return null;
        own.remove(power);
        return publish(player);
    }

    public boolean has(UUID player, ResourceLocation power) {
        Map<ResourceLocation, V> own = slices.get(player);
        return own != null && own.containsKey(power);
    }

    /** Drops every slice {@code keep} rejects. Same return as {@link #remove}. */
    public V retain(UUID player, Predicate<ResourceLocation> keep) {
        Map<ResourceLocation, V> own = slices.get(player);
        if (own == null || !own.keySet().removeIf(keep.negate())) return null;
        return publish(player);
    }

    public void clearPlayer(UUID player) {
        slices.remove(player);
        lastSent.remove(player);
    }

    private V publish(UUID player) {
        Map<ResourceLocation, V> own = slices.get(player);
        V merged = merge.apply(own == null ? List.of() : own.values());
        if (own != null && own.isEmpty()) slices.remove(player);
        if (merged.equals(lastSent.get(player))) return null;
        lastSent.put(player, merged);
        return merged;
    }
}
