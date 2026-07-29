package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out which bone of a morph's model is its arm, so
 * {@link MorphRenderHandler} can draw that bone in first person instead of the
 * player's own — the {@code "first_person": "arm"} mode of
 * {@code neoorigins:entity_model}.
 *
 * <p>Resolution runs in three steps, most specific first:
 * <ol>
 *   <li>the {@code arm} field of the {@link MorphSpec}, when the pack named a
 *       bone explicitly;
 *   <li>{@link HumanoidModel}'s fixed arm fields, which is the answer for every
 *       humanoid mob and needs no searching;
 *   <li>a search of the model's bone tree for the conventional names.
 * </ol>
 *
 * <p>A morph whose model has no arm at all — a slime, a bat — resolves to
 * nothing, and the caller falls back to drawing just the held item. That is a
 * normal outcome, not an error, so it is not logged. A bone name the pack
 * <em>did</em> ask for and that does not exist is a typo worth surfacing, so it
 * warns once and then degrades to auto-detection rather than silently showing
 * the wrong thing or nothing.
 *
 * <p>Not every model can be searched. On this version there is no universal
 * bone root: {@link HierarchicalModel} and {@link ListModel} expose their parts,
 * but {@code AgeableListModel} keeps them behind protected accessors, so a
 * non-humanoid model of that shape only resolves through an explicit
 * {@code HumanoidModel} field or not at all. Widening that is not worth an
 * access transformer for a cosmetic fallback.
 */
public final class MorphArms {

    private MorphArms() {}

    /**
     * Bone names to try, in order, when the pack did not name one. Covers the
     * vanilla convention ({@code right_arm}) and the camelCase and
     * noun-first spellings that modded and Blockbench-exported models use.
     * The bare {@code arm} is last so a model with both a specific and a
     * generic bone picks the specific one.
     */
    private static final List<String> RIGHT_NAMES =
        List.of("right_arm", "rightArm", "arm_right", "armRight", "right_hand", "rightHand", "arm");
    private static final List<String> LEFT_NAMES =
        List.of("left_arm", "leftArm", "arm_left", "armLeft", "left_hand", "leftHand", "arm");

    /**
     * Resolved bone per model, then per side + requested name. A model is a
     * singleton owned by its renderer and its bone tree never changes, so the
     * search runs once rather than every frame. Keyed by the model object
     * itself: {@link EntityModel} does not override {@code equals}, so this is
     * an identity map in practice.
     */
    private static final Map<Object, Map<String, Optional<ModelPart>>> CACHE = new ConcurrentHashMap<>();

    /** One-time WARN de-dup for bone names a pack asked for that don't exist. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * The bone to draw as this morph's arm, or {@code null} when the model has
     * none and the caller should fall back to drawing the item alone.
     */
    @Nullable
    public static ModelPart resolve(EntityModel<?> model, MorphSpec spec, HumanoidArm side) {
        String requested = spec.arm().filter(s -> !s.isBlank()).orElse("");
        return CACHE.computeIfAbsent(model, m -> new ConcurrentHashMap<>())
            .computeIfAbsent(side.name() + "|" + requested, k -> find(model, requested, side))
            .orElse(null);
    }

    /** Drop the resolved bones; models are rebuilt on a resource reload. */
    public static void clearCache() {
        CACHE.clear();
        WARNED.clear();
    }

    private static Optional<ModelPart> find(EntityModel<?> model, String requested, HumanoidArm side) {
        if (!requested.isEmpty()) {
            Optional<ModelPart> named = byName(model, requested);
            if (named.isPresent()) return named;
            warnMissing(model, requested);
            // Fall through rather than give up: a mistyped bone name should
            // still leave the player with a visible arm, just not the one asked
            // for. The WARN above is what tells the author to fix it.
        }

        // A humanoid model always knows its own arms, and its bones are not
        // reachable by search on this version, so ask it directly.
        if (model instanceof HumanoidModel<?> humanoid) {
            return Optional.of(side == HumanoidArm.LEFT ? humanoid.leftArm : humanoid.rightArm);
        }

        for (String name : side == HumanoidArm.LEFT ? LEFT_NAMES : RIGHT_NAMES) {
            Optional<ModelPart> found = byName(model, name);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** Find a bone by name anywhere in whatever part of the model we can reach. */
    private static Optional<ModelPart> byName(EntityModel<?> model, String name) {
        if (model instanceof HumanoidModel<?> humanoid) {
            Optional<ModelPart> field = humanoidPart(humanoid, name);
            if (field.isPresent()) return field;
        }
        try {
            if (model instanceof HierarchicalModel<?> hierarchical) {
                return hierarchical.getAnyDescendantWithName(name);
            }
            if (model instanceof ListModel<?> list) {
                for (ModelPart part : list.parts()) {
                    Optional<ModelPart> found = descendant(part, name);
                    if (found.isPresent()) return found;
                }
            }
        } catch (Exception e) {
            // A third-party model that throws while walking its own bones is
            // not worth taking the render down for.
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<ModelPart> descendant(ModelPart part, String name) {
        // getAllParts() includes the part itself, so this covers direct
        // children as well as anything deeper.
        return part.getAllParts().filter(p -> p.hasChild(name)).findFirst().map(p -> p.getChild(name));
    }

    /**
     * The named bones of a humanoid model. Any of them may legitimately be
     * chosen — a morph might want a wing or the head drawn in first person, not
     * only an arm.
     */
    private static Optional<ModelPart> humanoidPart(HumanoidModel<?> model, String name) {
        return switch (name) {
            case "right_arm", "rightArm" -> Optional.of(model.rightArm);
            case "left_arm", "leftArm" -> Optional.of(model.leftArm);
            case "head" -> Optional.of(model.head);
            case "hat" -> Optional.of(model.hat);
            case "body" -> Optional.of(model.body);
            case "right_leg", "rightLeg" -> Optional.of(model.rightLeg);
            case "left_leg", "leftLeg" -> Optional.of(model.leftLeg);
            default -> Optional.empty();
        };
    }

    private static void warnMissing(EntityModel<?> model, String name) {
        if (WARNED.add(model.getClass().getName() + "|" + name)) {
            NeoOrigins.LOGGER.warn(
                "entity_model: the morph model {} has no bone named '{}' — falling back to "
                + "auto-detection. Check the 'arm' field against the model's own bone names.",
                model.getClass().getSimpleName(), name);
        }
    }
}
