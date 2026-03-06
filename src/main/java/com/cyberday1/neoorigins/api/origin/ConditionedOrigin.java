package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * An origin reference with an optional entity condition.
 * If no condition is present, the origin is always available.
 */
public record ConditionedOrigin(
    Identifier origin,
    Optional<String> condition
) {
    /**
     * Codec that accepts either:
     *  - A plain Identifier string: "neoorigins:human"
     *  - An object: { "origin": "neoorigins:human", "condition": "..." }
     */
    public static final Codec<ConditionedOrigin> CODEC = Codec.withAlternative(
        RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("origin").forGetter(ConditionedOrigin::origin),
            Codec.STRING.optionalFieldOf("condition").forGetter(ConditionedOrigin::condition)
        ).apply(inst, ConditionedOrigin::new)),
        Identifier.CODEC.xmap(
            rl -> new ConditionedOrigin(rl, Optional.empty()),
            ConditionedOrigin::origin
        )
    );

    public boolean isAvailableTo(Player player) {
        // Condition system placeholder — extend later for entity conditions
        return condition.isEmpty();
    }
}
