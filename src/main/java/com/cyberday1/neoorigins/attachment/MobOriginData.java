package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-{@code LivingEntity} mob-origin state. The mob-side analogue of
 * {@link PlayerOriginData}; attached to <em>every</em> non-player living
 * entity (harmless if an origin is never assigned).
 *
 * <p>Registered with NO {@code copyOnDeath} so a mob that dies and respawns
 * (spawner, breeding) re-rolls — mob death is final by design.
 */
public final class MobOriginData {

    private Optional<ResourceLocation> originId = Optional.empty();
    /** Spawn-rule mutex groups already rolled for this mob (Phase 2). */
    private final Set<String> mutexGroupsApplied = new HashSet<>();
    /** Per-tick scratch numeric state for mob-applicable powers (parity with
     *  {@link PlayerOriginData}). Does NOT bump {@link #version} — it is
     *  scratch state, not part of the resolved power set (see the player-side
     *  M1 fix for the same reasoning). */
    private final Map<String, Float> customFloats = new HashMap<>();
    /** Session-only — bumped when the assigned origin changes, for a future
     *  per-entity power cache to invalidate on. Not serialized. */
    private transient int version = 0;

    // MAP_CODEC is the primary form: NeoForge's attachment serializer takes a
    // MapCodec on 26.1 (.serialize(MobOriginData.MAP_CODEC)), while 1.21.1
    // takes the plain Codec. Exposing both (CODEC = MAP_CODEC.codec()) keeps
    // this file identical across branches — only EntityAttachments differs.
    public static final MapCodec<MobOriginData> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        ResourceLocation.CODEC.optionalFieldOf("origin")
            .forGetter(d -> d.originId),
        Codec.STRING.listOf().optionalFieldOf("mutex_groups_applied", java.util.List.of())
            .forGetter(d -> java.util.List.copyOf(d.mutexGroupsApplied)),
        Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("custom_floats", Map.of())
            .forGetter(d -> Map.copyOf(d.customFloats))
    ).apply(inst, (origin, mutex, floats) -> {
        MobOriginData data = new MobOriginData();
        data.originId = origin;
        data.mutexGroupsApplied.addAll(mutex);
        data.customFloats.putAll(floats);
        return data;
    }));
    public static final Codec<MobOriginData> CODEC = MAP_CODEC.codec();

    public MobOriginData() {}

    public Optional<ResourceLocation> getOriginId() { return originId; }

    public boolean hasOrigin() { return originId.isPresent(); }

    public void setOriginId(ResourceLocation id) {
        this.originId = Optional.ofNullable(id);
        version++;
    }

    public void clear() {
        originId = Optional.empty();
        mutexGroupsApplied.clear();
        customFloats.clear();
        version++;
    }

    public boolean hasMutexGroup(String group) { return mutexGroupsApplied.contains(group); }

    public void markMutexGroup(String group) { mutexGroupsApplied.add(group); }

    public float getCustomFloat(String key, float fallback) {
        return customFloats.getOrDefault(key, fallback);
    }

    public void setCustomFloat(String key, float value) {
        customFloats.put(key, value);
        // Intentionally NOT bumping version — scratch state only (see M1).
    }

    public int version() { return version; }
}
