package com.cyberday1.neoorigins.api.global_power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * A datapack-defined "global power set": a bundle of powers granted to entities
 * <em>without</em> an origin assignment. Apoli calls this {@code apoli:global}
 * (the "Global Power Set" feature); NeoOrigins authors it at
 * {@code data/<ns>/global_powers/<id>.json}.
 *
 * <p>Fields (Apoli-authoritative):
 * <ul>
 *   <li>{@code entity_types} — OPTIONAL. A mixed list of literal entity ids
 *       ({@code "minecraft:creeper"}) and tag refs ({@code "#minecraft:skeletons"}).
 *       When ABSENT/empty the set applies to ALL entities (players AND mobs).
 *       Players are matched by the literal id {@code minecraft:player}.</li>
 *   <li>{@code powers} — required array of power ids.</li>
 *   <li>{@code order} — OPTIONAL int, default 0. Lower applies first.</li>
 * </ul>
 *
 * <p>{@code id} is injected from the file path by
 * {@link com.cyberday1.neoorigins.data.GlobalPowerSetDataManager} (mirrors the
 * origin / mob-origin loaders), so authored JSON omits it.
 */
public record GlobalPowerSet(
    Identifier id,
    List<String> entityTypes,
    List<Identifier> powers,
    int order
) {
    public static final Codec<GlobalPowerSet> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("id").forGetter(GlobalPowerSet::id),
        // Raw strings — a single entry may be a literal id OR a "#tag" ref, so we
        // can't decode straight to Identifier here (the '#' would fail).
        Codec.STRING.listOf().optionalFieldOf("entity_types", List.of())
            .forGetter(GlobalPowerSet::entityTypes),
        Identifier.CODEC.listOf().optionalFieldOf("powers", List.of())
            .forGetter(GlobalPowerSet::powers),
        Codec.INT.optionalFieldOf("order", 0).forGetter(GlobalPowerSet::order)
    ).apply(inst, GlobalPowerSet::new));

    /** True when no {@code entity_types} were declared — the set targets everything. */
    public boolean appliesToAll() {
        return entityTypes.isEmpty();
    }

    /**
     * True if {@code type} is targeted by this set. The Apoli {@code entity_types}
     * array mixes literal entity ids and {@code #tag} refs in ONE list, so each
     * entry is parsed individually:
     * <ul>
     *   <li>an empty/absent list matches everything;</li>
     *   <li>an entry beginning with {@code #} is an entity-type tag — matched via
     *       {@code wrapAsHolder(type).is(tag)};</li>
     *   <li>any other entry is a literal entity id — matched against the type's key.</li>
     * </ul>
     */
    public boolean matchesEntityType(EntityType<?> type) {
        if (entityTypes.isEmpty()) return true;
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        for (String entry : entityTypes) {
            if (entry == null || entry.isBlank()) continue;
            if (entry.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(entry.substring(1));
                if (tagId == null) continue;
                TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
                if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag)) return true;
            } else {
                Identifier litId = Identifier.tryParse(entry);
                if (litId != null && litId.equals(key)) return true;
            }
        }
        return false;
    }

    /**
     * True if this set applies to the player entity ({@code minecraft:player}).
     * Players have no {@link EntityType} instance handy at login, so this is a
     * dedicated check: matches when {@code entity_types} is absent, or contains
     * the literal {@code minecraft:player}, or a tag the player type belongs to.
     */
    public boolean matchesPlayer() {
        if (entityTypes.isEmpty()) return true;
        return matchesEntityType(EntityType.PLAYER);
    }
}
