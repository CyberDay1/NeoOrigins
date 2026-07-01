package com.cyberday1.neoorigins.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Maps renamed built-in origin IDs to their current IDs so that datapacks,
 * originpacks, and saved player selections authored before the rename keep
 * resolving to the right origin.
 *
 * <p>Renames:
 * <ul>
 *   <li>{@code neoorigins:jianxian} → {@code neoorigins:sword_immortal}</li>
 *   <li>{@code neoorigins:golden_bell} → {@code neoorigins:golden_body}</li>
 * </ul>
 *
 * <p>Applied at the origin lookup ({@link OriginDataManager#getOrigin}), at
 * saved-selection load ({@code PlayerOriginData} codec), and when normalizing
 * layer JSON ({@code LayerDataManager}). Only the origin ID changed; the powers
 * these origins grant keep their existing IDs.
 */
public final class LegacyOriginIds {

    private LegacyOriginIds() {}

    private static final Map<ResourceLocation, ResourceLocation> RENAMES = Map.of(
        ResourceLocation.fromNamespaceAndPath("neoorigins", "jianxian"),
        ResourceLocation.fromNamespaceAndPath("neoorigins", "sword_immortal"),
        ResourceLocation.fromNamespaceAndPath("neoorigins", "golden_bell"),
        ResourceLocation.fromNamespaceAndPath("neoorigins", "golden_body")
    );

    /** Returns the current ID for a possibly-legacy origin ID (identity if not renamed). */
    public static ResourceLocation remap(ResourceLocation id) {
        return RENAMES.getOrDefault(id, id);
    }

    /**
     * String overload for JSON-level remapping. Returns the input unchanged if it
     * is not a parseable ID or was not renamed.
     */
    public static String remap(String id) {
        if (id == null) return null;
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) return id;
        ResourceLocation mapped = RENAMES.get(parsed);
        return mapped != null ? mapped.toString() : id;
    }
}
