package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * What a random roll may land on, shared by the picker's Random button and
 * server-side random assignment so the two cannot drift apart.
 */
public final class RandomOriginPool {

    private RandomOriginPool() {}

    /** {@code candidates} minus the layer's {@code exclude_random} list and anything {@code blocked}. */
    public static List<ResourceLocation> of(OriginLayer layer, Collection<ResourceLocation> candidates,
                                            Predicate<ResourceLocation> blocked) {
        return candidates.stream()
            .filter(layer::isRandomCandidate)
            .filter(blocked.negate())
            .toList();
    }

    /** One uniform draw, or null for an empty pool. {@code nextInt} returns 0..n-1. */
    public static ResourceLocation pick(List<ResourceLocation> pool, IntUnaryOperator nextInt) {
        if (pool.isEmpty()) return null;
        return pool.get(nextInt.applyAsInt(pool.size()));
    }
}
