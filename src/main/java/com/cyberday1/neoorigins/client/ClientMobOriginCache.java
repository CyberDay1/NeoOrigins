package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which mob origin each tracked entity carries, fed by
 * {@code SyncMobOriginPayload}. Phase 2 only stores it; client-visible
 * rendering (custom name / scale / particles) that would read this is a later
 * concern. Keyed by network entity id; entries are best-effort and naturally
 * go stale when an entity untracks — acceptable for purely cosmetic reads.
 */
public final class ClientMobOriginCache {

    private static final Map<Integer, Identifier> ORIGINS = new ConcurrentHashMap<>();

    private ClientMobOriginCache() {}

    public static void set(int entityId, Optional<Identifier> originId) {
        originId.ifPresentOrElse(id -> ORIGINS.put(entityId, id),
            () -> ORIGINS.remove(entityId));
    }

    public static Identifier get(int entityId) {
        return ORIGINS.get(entityId);
    }

    public static void clear() { ORIGINS.clear(); }
}
