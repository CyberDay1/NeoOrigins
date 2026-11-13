package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.power.EmbeddedPower;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Supplier;

/** Transaction-local child definitions. Editor codec probes never publish powers. */
public final class EmbeddedPowerLoading {
    private EmbeddedPowerLoading() {}
    public static final String SEGMENT = "/_embedded/";
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<PowerDataManager.Definitions> GENERATION = new ThreadLocal<>();

    public static final class Scope {
        public final Map<ResourceLocation, PowerHolder<?>> holders = new LinkedHashMap<>();
        public final Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        public final Map<ResourceLocation, List<ResourceLocation>> links = new LinkedHashMap<>();
        private ResourceLocation owner;
        private int depth;
        private final Set<String> slots = new HashSet<>();
        Scope(ResourceLocation owner) { this.owner = owner; }
        public void rollback() {
            raw.keySet().forEach(id -> {
                com.cyberday1.neoorigins.power.util.PowerUpkeep.register(id, null);
                com.cyberday1.neoorigins.power.util.PowerUpkeep.registerToggleMessages(id, true);
            });
        }
    }

    public record Captured<T>(T value, Scope scope) {}

    public static <T> Captured<T> capture(ResourceLocation owner, Supplier<T> body) {
        Scope previous = CURRENT.get();
        Scope scope = new Scope(owner);
        CURRENT.set(scope);
        try { return new Captured<>(body.get(), scope); }
        catch (RuntimeException e) { scope.rollback(); throw e; }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    public static PowerHolder<?> lookup(ResourceLocation id) {
        var generation = GENERATION.get();
        return generation == null ? PowerDataManager.INSTANCE.getPower(id) : generation.holder(id);
    }

    public static PowerDataManager.Definitions generation() { return GENERATION.get(); }

    public static void withGeneration(PowerDataManager.Definitions definitions, Runnable body) {
        var previous = GENERATION.get();
        GENERATION.set(definitions);
        try { body.run(); }
        finally { if (previous == null) GENERATION.remove(); else GENERATION.set(previous); }
    }

    /** Slot is an identity key, unique within its parent codec; do not use this codec in a list. */
    public static Codec<EmbeddedPower> codec(String slot) {
        if (slot == null || !slot.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Embedded power slot must match [a-z0-9_.-]+");
        }
        return new Codec<>() {
            @Override public <T> DataResult<Pair<EmbeddedPower, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    ResourceLocation reference = null;
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                        reference = ResourceLocation.parse(json.getAsString());
                    } else if (!json.isJsonObject() || !json.getAsJsonObject().has("type")) {
                        return DataResult.error(() -> "Embedded power needs an id string or an object with type");
                    }
                    Scope scope = CURRENT.get();
                    if (scope == null) {
                        // Forms and validation decode configs before there is a parent ID.
                        return DataResult.success(Pair.of(new EmbeddedPower(reference, json), ops.empty()));
                    }
                    ResourceLocation owner = scope.owner;
                    if (!scope.slots.add(owner + ":" + slot)) {
                        throw new IllegalArgumentException("Duplicate embedded slot " + slot + " in " + owner);
                    }
                    ResourceLocation id = reference != null ? reference : ResourceLocation.fromNamespaceAndPath(
                        owner.getNamespace(), owner.getPath() + SEGMENT + slot);
                    scope.links.computeIfAbsent(owner, k -> new ArrayList<>()).add(id);
                    if (reference == null) {
                        if (scope.depth >= 32) throw new IllegalArgumentException("Embedded power depth exceeds 32 at " + id);
                        scope.owner = id;
                        scope.depth++;
                        try {
                            JsonObject body = json.getAsJsonObject().deepCopy();
                            scope.raw.put(id, body);
                            scope.raw.put(id, PowerDataManager.INSTANCE.decodeEmbedded(id, body, scope.holders));
                            if (!scope.holders.containsKey(id)) throw new IllegalArgumentException("Invalid embedded power " + id);
                        } finally { scope.owner = owner; scope.depth--; }
                    }
                    return DataResult.success(Pair.of(new EmbeddedPower(id, json), ops.empty()));
                } catch (RuntimeException e) {
                    return DataResult.error(() -> "Embedded power " + slot + ": " + e.getMessage());
                }
            }
            @Override public <T> DataResult<T> encode(EmbeddedPower input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, input.definition()));
            }
        };
    }

    /** Missing references and cycles are rejected before any player can execute the graph. */
    public static Set<ResourceLocation> invalid(Map<ResourceLocation, PowerHolder<?>> available,
            Map<ResourceLocation, List<ResourceLocation>> links) {
        Map<ResourceLocation, Integer> remaining = new HashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> parents = new HashMap<>();
        Deque<ResourceLocation> ready = new ArrayDeque<>();
        for (ResourceLocation id : available.keySet()) {
            Set<ResourceLocation> children = new HashSet<>(links.getOrDefault(id, List.of()));
            remaining.put(id, children.size());
            if (children.isEmpty()) ready.add(id);
            for (ResourceLocation child : children) parents.computeIfAbsent(child, k -> new HashSet<>()).add(id);
        }
        Set<ResourceLocation> bad = new HashSet<>(available.keySet());
        while (!ready.isEmpty()) {
            ResourceLocation child = ready.removeFirst();
            bad.remove(child);
            for (ResourceLocation parent : parents.getOrDefault(child, Set.of())) {
                if (remaining.compute(parent, (k, n) -> n - 1) == 0) ready.add(parent);
            }
        }
        // Missing targets never become ready; cycles and their dependents remain too.
        return bad;
    }
}
