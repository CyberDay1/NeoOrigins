package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

/**
 * Which entities a {@link MobOrigin} may apply to. Exactly one of the three
 * forms should be set; {@link #isValid()} enforces that and the server-side
 * validator rejects an empty/over-specified spec.
 *
 * <ul>
 *   <li>{@code entity_type} — a single exact type, e.g. {@code minecraft:zombie}</li>
 *   <li>{@code entity_tag}  — an entity-type tag, e.g. {@code minecraft:undead}</li>
 *   <li>{@code entity_types}— an explicit set of exact types</li>
 * </ul>
 */
public record EntityTargetSpec(
    Optional<Identifier> entityType,
    Optional<Identifier> entityTag,
    List<Identifier> entityTypes
) {
    public static final Codec<EntityTargetSpec> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.optionalFieldOf("entity_type").forGetter(EntityTargetSpec::entityType),
        Identifier.CODEC.optionalFieldOf("entity_tag").forGetter(EntityTargetSpec::entityTag),
        Identifier.CODEC.listOf().optionalFieldOf("entity_types", List.of())
            .forGetter(EntityTargetSpec::entityTypes)
    ).apply(inst, EntityTargetSpec::new));

    /** Exactly one of the three forms must be non-empty. */
    public boolean isValid() {
        int set = (entityType.isPresent() ? 1 : 0)
                + (entityTag.isPresent() ? 1 : 0)
                + (entityTypes.isEmpty() ? 0 : 1);
        return set == 1;
    }

    /** True if {@code type} is targeted by this spec. */
    public boolean matches(EntityType<?> type) {
        if (entityType.isPresent()) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return entityType.get().equals(key);
        }
        if (entityTag.isPresent()) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, entityTag.get());
            return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag);
        }
        if (!entityTypes.isEmpty()) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return key != null && entityTypes.contains(key);
        }
        return false;
    }
}
