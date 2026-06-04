package com.cyberday1.neoorigins.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

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
 * the admin {@code /neoorigins unlock} command, an Orb of Origin re-pick, or
 * {@code /neoorigins reset} — all of which call {@link #release} explicitly.
 */
public class OriginClaimsData extends SavedData {

    private static final String DATA_NAME = "neoorigins_origin_claims";

    /** layer id → (origin id → owner UUID). */
    private final Map<ResourceLocation, Map<ResourceLocation, UUID>> claims = new HashMap<>();

    public static OriginClaimsData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(OriginClaimsData::new, OriginClaimsData::load),
            DATA_NAME);
    }

    /** Owner of an origin in a layer, or {@code null} if unclaimed. */
    public UUID getOwner(ResourceLocation layer, ResourceLocation origin) {
        Map<ResourceLocation, UUID> m = claims.get(layer);
        return m == null ? null : m.get(origin);
    }

    /** True if the origin is claimed by a player other than {@code who}. */
    public boolean isClaimedByOther(ResourceLocation layer, ResourceLocation origin, UUID who) {
        UUID owner = getOwner(layer, origin);
        return owner != null && !owner.equals(who);
    }

    /** Record (or overwrite) the owner of an origin in a layer. */
    public void claim(ResourceLocation layer, ResourceLocation origin, UUID who) {
        UUID prior = claims.computeIfAbsent(layer, k -> new HashMap<>()).put(origin, who);
        if (!who.equals(prior)) setDirty();
    }

    /** Release a claim outright. Returns true if a claim was removed. */
    public boolean release(ResourceLocation layer, ResourceLocation origin) {
        Map<ResourceLocation, UUID> m = claims.get(layer);
        if (m != null && m.remove(origin) != null) {
            if (m.isEmpty()) claims.remove(layer);
            setDirty();
            return true;
        }
        return false;
    }

    /** Release a claim only if {@code who} owns it (Orb / reset self-release). */
    public boolean releaseIfOwner(ResourceLocation layer, ResourceLocation origin, UUID who) {
        UUID owner = getOwner(layer, origin);
        return owner != null && owner.equals(who) && release(layer, origin);
    }

    /** Read-only snapshot of all claims, for listing and sync. */
    public Map<ResourceLocation, Map<ResourceLocation, UUID>> view() {
        Map<ResourceLocation, Map<ResourceLocation, UUID>> copy = new HashMap<>();
        claims.forEach((layer, m) -> copy.put(layer, new HashMap<>(m)));
        return copy;
    }

    // ── Serialization ───────────────────────────────────────────────────────

    public static OriginClaimsData load(CompoundTag tag, HolderLookup.Provider provider) {
        OriginClaimsData data = new OriginClaimsData();
        CompoundTag layers = tag.getCompound("layers");
        for (String layerKey : layers.getAllKeys()) {
            ResourceLocation layer = ResourceLocation.tryParse(layerKey);
            if (layer == null) continue;
            CompoundTag originMap = layers.getCompound(layerKey);
            Map<ResourceLocation, UUID> m = new HashMap<>();
            for (String originKey : originMap.getAllKeys()) {
                ResourceLocation origin = ResourceLocation.tryParse(originKey);
                if (origin != null) m.put(origin, originMap.getUUID(originKey));
            }
            if (!m.isEmpty()) data.claims.put(layer, m);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag layers = new CompoundTag();
        claims.forEach((layer, m) -> {
            CompoundTag originMap = new CompoundTag();
            m.forEach((origin, uuid) -> originMap.putUUID(origin.toString(), uuid));
            layers.put(layer.toString(), originMap);
        });
        tag.put("layers", layers);
        return tag;
    }
}
