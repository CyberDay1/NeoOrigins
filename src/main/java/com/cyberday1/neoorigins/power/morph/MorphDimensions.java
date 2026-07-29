package com.cyberday1.neoorigins.power.morph;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How big a morphed player actually is.
 *
 * <p>The size is read off a stand-in of the morph target rather than guessed
 * from the entity type, because a good deal of it is variant-dependent: a slime
 * is four times the width at size 4 that it is at size 1, and it says so only
 * once the nbt has been applied to it. That makes the cache key the type
 * <em>and</em> the variant nbt, exactly as the voice cache is keyed.
 *
 * <p>What's cached is the {@link EntityDimensions} record, not the stand-in it
 * came from — the same reasoning as {@link MorphSoundResolver}: keeping the
 * entity would keep a {@link Level} alive long after the world unloaded, and
 * the record is an immutable set of floats that belongs to nobody.
 *
 * <p>The morph's {@code scale} is applied on the way out rather than baked into
 * the cache, so two morphs of one mob at different scales share the one
 * measurement. Scale is deliberately part of the answer: a morph drawn at twice
 * the size that collides at half of it is worse than either extreme.
 */
public final class MorphDimensions {

    /** Keyed by entity type plus variant nbt, before the morph's own scale. */
    private static final Map<String, EntityDimensions> SIZES = new ConcurrentHashMap<>();

    /** Types that couldn't be measured; logged once, then never retried. */
    private static final Set<ResourceLocation> UNMEASURABLE = ConcurrentHashMap.newKeySet();

    private MorphDimensions() {}

    /**
     * The size a player under {@code spec} should collide at, or null to leave
     * the player's own size alone — which is the answer for a morph that opted
     * out of hitbox changes, one that only restyles the player's skin, and one
     * whose target turned out not to be measurable.
     */
    @Nullable
    public static EntityDimensions of(Entity player, MorphSpec spec) {
        if (!spec.hitbox()) return null;
        ResourceLocation typeId = spec.entityType().orElse(null);
        if (typeId == null || UNMEASURABLE.contains(typeId)) return null;

        Level level = player.level();
        if (level == null) return null;

        String key = typeId + "|" + spec.nbt().map(CompoundTag::hashCode).orElse(0);
        EntityDimensions base = SIZES.get(key);
        if (base == null) {
            base = measure(level, spec, typeId);
            if (base == null) return null;
            SIZES.put(key, base);
        }
        return spec.scale() == 1.0f ? base : base.scale(spec.scale());
    }

    /**
     * Ask a throwaway of the morph target how big it is. Failure is permanent
     * for that type: a target that can't be built or won't give a usable size
     * has no hitbox to lend, and retrying would put that cost on every tick of
     * every morphed player.
     */
    @Nullable
    private static EntityDimensions measure(Level level, MorphSpec spec, ResourceLocation typeId) {
        Entity donor = MorphDonor.create(level, spec, typeId, reason -> markUnmeasurable(typeId, reason));
        if (donor == null) return null;

        try {
            EntityDimensions dimensions = donor.getDimensions(Pose.STANDING);
            if (dimensions.width() <= 0.0f || dimensions.height() <= 0.0f) {
                markUnmeasurable(typeId, "it has no size to speak of");
                return null;
            }
            return dimensions;
        } catch (Exception e) {
            // A modded mob is free to assume it is in a world when asked. One
            // that trips over a stand-in keeps the player's own hitbox.
            markUnmeasurable(typeId, "asking it for its size failed: " + e.getMessage());
            return null;
        }
    }

    private static void markUnmeasurable(ResourceLocation typeId, String reason) {
        if (UNMEASURABLE.add(typeId)) {
            NeoOrigins.LOGGER.warn(
                "entity_model: cannot take a hitbox from '{}' — {}. Keeping the player's own hitbox.",
                typeId, reason);
        }
        SIZES.keySet().removeIf(k -> k.startsWith(typeId + "|"));
    }
}
