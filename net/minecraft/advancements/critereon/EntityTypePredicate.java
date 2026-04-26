package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public record EntityTypePredicate(HolderSet<EntityType<?>> types) {
    public static final Codec<EntityTypePredicate> CODEC = RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE)
        .xmap(EntityTypePredicate::new, EntityTypePredicate::types);

    public static EntityTypePredicate of(EntityType<?> p_37648_) {
        return new EntityTypePredicate(HolderSet.direct(p_37648_.builtInRegistryHolder()));
    }

    public static EntityTypePredicate of(TagKey<EntityType<?>> p_204082_) {
        return new EntityTypePredicate(BuiltInRegistries.ENTITY_TYPE.getOrCreateTag(p_204082_));
    }

    public boolean matches(EntityType<?> p_37642_) {
        return p_37642_.is(this.types);
    }
}
