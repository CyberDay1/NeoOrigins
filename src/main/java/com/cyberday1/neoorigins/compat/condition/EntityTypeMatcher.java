package com.cyberday1.neoorigins.compat.condition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.function.Predicate;

/** The one entity_type matcher for both condition sides: an id (bare means minecraft:) or a #tag. */
public final class EntityTypeMatcher {

    private EntityTypeMatcher() {}

    /** Parses once at load; a malformed id or tag throws here rather than on every test. */
    public static Predicate<EntityType<?>> compile(String raw) {
        if (raw.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(raw.substring(1)));
            return type -> type.is(tag);
        }
        ResourceLocation expected = ResourceLocation.parse(raw);
        return type -> expected.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }
}
