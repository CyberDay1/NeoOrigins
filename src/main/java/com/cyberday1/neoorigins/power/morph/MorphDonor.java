package com.cyberday1.neoorigins.power.morph;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Builds the stand-in that a morph target gets measured and listened to
 * through.
 *
 * <p>Some of what a morph needs can only be answered by the mob itself: how it
 * sounds, how big it is. Both answers arrive the same way — build one, ask it,
 * drop it — so the building lives here rather than twice over. The stand-in is
 * never added to the level, never ticked and never kept; callers cache the
 * handful of values they read off it, not the entity, so nothing here can end
 * up holding a {@link Level} past its unload.
 */
public final class MorphDonor {

    private MorphDonor() {}

    /**
     * A stand-in of {@code typeId} carrying the morph's variant nbt, or null if
     * one can't be built.
     *
     * @param onFailure handed a phrase that completes "cannot … — <em>reason</em>",
     *                  so each caller reports it in its own words and, more to
     *                  the point, remembers not to ask a second time
     */
    @Nullable
    public static Entity create(Level level, MorphSpec spec, ResourceLocation typeId,
                                Consumer<String> onFailure) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            onFailure.accept("no such entity type is registered");
            return null;
        }

        Entity donor;
        try {
            donor = type.create(level);
        } catch (Exception e) {
            onFailure.accept("creating one failed: " + e.getMessage());
            return null;
        }
        if (donor == null) {
            onFailure.accept("its entity type declined to create one");
            return null;
        }

        // The same nbt the render dummy gets, so a small slime is small in
        // every sense: looks, voice and hitbox.
        spec.nbt().ifPresent(nbt -> MorphVariants.apply(donor, nbt, typeId));
        return donor;
    }
}
