package com.cyberday1.neoorigins.power.morph;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.CustomData;

/**
 * Applies a morph's partial {@code nbt} to a throwaway entity, so a pack can
 * pick a variant — sheep colour, cat type, villager profession, slime size —
 * without any per-type code anywhere in the mod.
 *
 * <p>Common code rather than client code because two different throwaways need
 * the same answer: the render dummy the viewer sees, and the sound donor the
 * server asks for a voice. A slime morph whose size is set in {@code nbt} has to
 * both look small and squeak small, and the only way to guarantee that is for
 * both to go through here.
 */
public final class MorphVariants {

    private MorphVariants() {}

    /**
     * Load {@code nbt} into {@code target}, ignoring anything the entity doesn't
     * understand.
     *
     * <p>Two keys are stripped first. {@code Team} would have the throwaway join
     * a scoreboard team on the real scoreboard — a stand-in has no business
     * mutating shared world state. {@code Owner} in string form is a legacy
     * player-name field that the modern UUID-typed reader rejects outright, and
     * it is a plausible thing for a pack to write on a tamed-mob morph.
     *
     * <p>This runs arbitrary entity load code, so a bad value must not be allowed
     * to take the caller down: a failure leaves the entity in whatever
     * partially-loaded state it reached and uses it anyway, which is a far better
     * outcome than a crash on a cosmetic power.
     */
    public static void apply(Entity target, CompoundTag nbt, ResourceLocation typeId) {
        CompoundTag safe = nbt.copy();
        safe.remove("Team");
        if (safe.contains("Owner", Tag.TAG_STRING)) safe.remove("Owner");
        if (safe.isEmpty()) return;
        try {
            CustomData.of(safe).loadInto(target);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn(
                "entity_model: failed to apply variant nbt to '{}' ({}) — using it unmodified",
                typeId, e.getMessage());
        }
    }
}
