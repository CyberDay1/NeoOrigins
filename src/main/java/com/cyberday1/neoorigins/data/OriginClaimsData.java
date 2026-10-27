package com.cyberday1.neoorigins.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide ledger of claimed origins for layers configured as unique
 * (see {@code unique_origin_layers}). Maps each (layer, origin) pair to the
 * UUID of the player who owns it. Persisted on the overworld's data storage so
 * claims survive restarts.
 *
 * <p>Claims are sticky: nothing here auto-expires. They are released only by
 * the admin {@code /neoorigins unlock} command, an Orb of Origin re-pick,
 * {@code /neoorigins reset} or an {@code every_death} random reroll.
 */
public class OriginClaimsData extends SavedData {

    private static final Codec<Map<Identifier, Map<Identifier, UUID>>> LAYERS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Identifier.CODEC, UUIDUtil.CODEC));

    public static final Codec<OriginClaimsData> CODEC = RecordCodecBuilder.create(i -> i.group(
        LAYERS_CODEC.optionalFieldOf("layers", Map.of()).forGetter(OriginClaimsData::view)
    ).apply(i, OriginClaimsData::new));

    public static final SavedDataType<OriginClaimsData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("neoorigins", "origin_claims"), OriginClaimsData::new, CODEC);

    /** layer id → (origin id → owner UUID). */
    private final Map<Identifier, Map<Identifier, UUID>> claims = new HashMap<>();

    public OriginClaimsData() {}

    private OriginClaimsData(Map<Identifier, Map<Identifier, UUID>> loaded) {
        loaded.forEach((layer, m) -> {
            if (!m.isEmpty()) claims.put(layer, new HashMap<>(m));
        });
    }

    public static OriginClaimsData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Owner of an origin in a layer, or {@code null} if unclaimed. */
    public UUID getOwner(Identifier layer, Identifier origin) {
        Map<Identifier, UUID> m = claims.get(layer);
        return m == null ? null : m.get(origin);
    }

    /** True if the origin is claimed by a player other than {@code who}. */
    public boolean isClaimedByOther(Identifier layer, Identifier origin, UUID who) {
        UUID owner = getOwner(layer, origin);
        return owner != null && !owner.equals(who);
    }

    /** Record (or overwrite) the owner of an origin in a layer. */
    public void claim(Identifier layer, Identifier origin, UUID who) {
        UUID prior = claims.computeIfAbsent(layer, k -> new HashMap<>()).put(origin, who);
        if (!who.equals(prior)) setDirty();
    }

    /** Release a claim outright. Returns true if a claim was removed. */
    public boolean release(Identifier layer, Identifier origin) {
        Map<Identifier, UUID> m = claims.get(layer);
        if (m != null && m.remove(origin) != null) {
            if (m.isEmpty()) claims.remove(layer);
            setDirty();
            return true;
        }
        return false;
    }

    /** Release a claim only if {@code who} owns it (Orb / reset self-release). */
    public boolean releaseIfOwner(Identifier layer, Identifier origin, UUID who) {
        UUID owner = getOwner(layer, origin);
        return owner != null && owner.equals(who) && release(layer, origin);
    }

    /** Read-only snapshot of all claims, for listing and sync. */
    public Map<Identifier, Map<Identifier, UUID>> view() {
        Map<Identifier, Map<Identifier, UUID>> copy = new HashMap<>();
        claims.forEach((layer, m) -> copy.put(layer, new HashMap<>(m)));
        return copy;
    }
}
