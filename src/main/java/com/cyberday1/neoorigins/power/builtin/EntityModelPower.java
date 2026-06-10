package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Overrides the player's rendered model with another entity's model — a
 * client-side visual "morph". The server emits a capability tag
 * {@code entity_model:<namespace>:<path>} naming the target entity type;
 * the client reads it (per-player, via the morph-state sync) and renders a
 * dummy entity of that type in place of the player.
 *
 * <p>This is purely cosmetic: it does NOT change the player's hitbox or
 * eye height. Pair it with {@code neoorigins:size_scaling} (which writes the
 * vanilla {@code minecraft:scale} attribute) when you want the collision box
 * to match the morph silhouette.
 *
 * <p>v1 targets {@code minecraft:slime}. The render path is written as a
 * clean extension point so other entity types can be added later — only the
 * client-side {@code MorphRenderHandler} needs per-type tuning (held-item
 * attachment point, animation state copy).
 *
 * <pre>{@code
 * { "type": "neoorigins:entity_model", "entity_type": "minecraft:slime" }
 * }</pre>
 */
public class EntityModelPower extends PowerType<EntityModelPower.Config> {

    /** Capability-tag prefix emitted by this power. */
    public static final String CAP_PREFIX = "entity_model:";

    public record Config(ResourceLocation entityType, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(Config::entityType),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of(CAP_PREFIX + config.entityType().toString());
    }
}
