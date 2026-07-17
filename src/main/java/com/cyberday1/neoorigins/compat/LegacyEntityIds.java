package com.cyberday1.neoorigins.compat;

import java.util.Map;

/**
 * Remaps entity-type ids that the original Origins mod registered in code and
 * that legacy Apoli packs therefore reference under the {@code origins:}
 * namespace. NeoOrigins maps them to the closest vanilla equivalent; any
 * compat-layer point that resolves an entity-type id (fire_projectile,
 * summon-style actions) should run the id through {@link #remap(String)}
 * first. Sibling of {@link LegacyBlockIds}.
 */
public final class LegacyEntityIds {

    private static final Map<String, String> REMAP = Map.of(
        // Origins' EnderianPearlEntity is a ThrownEnderpearl minus the fall
        // damage and the endermite chance. CompatEventPowers restores that
        // behaviour for pearls tagged ENDERIAN_PEARL_FLAG at spawn time.
        "origins:enderian_pearl", "minecraft:ender_pearl"
    );

    /** Persistent-data flag marking a pearl spawned as an Enderian pearl. */
    public static final String ENDERIAN_PEARL_FLAG = "neoorigins:enderian_pearl";

    private LegacyEntityIds() {}

    /** Remaps a legacy entity-type id string; returns the input unchanged if unmapped. */
    public static String remap(String entityId) {
        if (entityId == null) return null;
        return REMAP.getOrDefault(entityId, entityId);
    }
}
