package com.cyberday1.neoorigins.screen.model;

import net.minecraft.resources.Identifier;

/** A single row in the origin selection list — either a namespace header or an origin entry. */
public record OriginListEntry(
    Identifier id,          // null for section headers
    String displayName,
    String namespace,
    boolean isSectionHeader
) {
    public static OriginListEntry header(String namespace, String displayName) {
        return new OriginListEntry(null, displayName, namespace, true);
    }

    public static OriginListEntry origin(Identifier id, String displayName, String namespace) {
        return new OriginListEntry(id, displayName, namespace, false);
    }
}
