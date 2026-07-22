package com.cyberday1.neoorigins.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared well-known origin-layer identifiers.
 *
 * <p>Hoisted here so the class-layer id has a single definition instead of being
 * re-declared per consumer.
 */
public final class PowerLayers {

    private PowerLayers() {}

    /**
     * The {@code neoorigins:class} origin layer. The class layer never evolves,
     * so its powers are read tier-flat.
     */
    public static final ResourceLocation CLASS_LAYER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class");
}
