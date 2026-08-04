package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Size powers on different layers have to stack. The Golem origin (scale 1.3) plus
 * the Titan class (scale 1.25) must leave the player at 1.55, and must still do so
 * after either layer is re-picked.
 *
 * <p>They did not. {@code size_scaling}'s revoke hook cleared every
 * {@code neoorigins:size_*} modifier on the player rather than the ones it granted,
 * and {@code ActiveOriginService.applyOriginPowers} only re-grants the layer that
 * changed, so re-picking one layer silently dropped the other layer's scaling and
 * never restored it. The revoke is now scoped to the power's own ids and the
 * layer-change sweep is what catches orphans.
 *
 * <p>These tests drive the real id generator, the real revoke predicate and the real
 * sweeper against a real {@link AttributeInstance} for {@code minecraft:scale}, so
 * the vanilla modifier arithmetic is exercised rather than restated.
 */
class SizeScalingCrossLayerStackTest {

    /** Values taken from the shipped power JSON, not invented for the test. */
    private static final Identifier GOLEM_SIZE =
        Identifier.fromNamespaceAndPath("neoorigins", "golem_size");
    private static final double GOLEM_SCALE = 1.3;
    private static final Identifier TITAN_SIZE =
        Identifier.fromNamespaceAndPath("neoorigins", "class_titan_size");
    private static final double TITAN_SCALE = 1.25;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static AttributeInstance scaleAttribute() {
        return new AttributeInstance(Attributes.SCALE, inst -> { });
    }

    /** Mirrors {@code SizeScalingPower.applyModifiers} for the scale attribute. */
    private static void grant(AttributeInstance inst, Identifier powerId, double scale) {
        Identifier modId = SizeScalingPower.modIdFor(powerId, "scale");
        if (inst.getModifier(modId) == null) {
            inst.addPermanentModifier(
                new AttributeModifier(modId, scale - 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Mirrors {@code SizeScalingPower.onRevoked} for the scale attribute. */
    private static void revoke(AttributeInstance inst, Identifier powerId) {
        SizeScalingPower.clearPrefixed(inst, SizeScalingPower.ownIdPrefix(powerId));
    }

    /** Mirrors the layer-change sweep in {@code ActiveOriginService.applyOriginPowers}. */
    private static void sweep(AttributeInstance inst, Set<Identifier> activePowerIds) {
        AttributeModifierPower.purgeInstanceExcept(
            inst, AttributeModifierPower.keepPrefixesFor(activePowerIds));
    }

    private static boolean hasModifier(AttributeInstance inst, Identifier powerId) {
        return inst.getModifier(SizeScalingPower.modIdFor(powerId, "scale")) != null;
    }

    @Test
    void twoLayersStackOnFirstGrant() {
        AttributeInstance scale = scaleAttribute();
        grant(scale, GOLEM_SIZE, GOLEM_SCALE);
        grant(scale, TITAN_SIZE, TITAN_SCALE);

        assertEquals(1.55, scale.getValue(), 1.0e-9,
            "golem 1.3 plus titan 1.25 should apply as +0.3 and +0.25 on a base of 1.0");
    }

    @Test
    void rePickingOneLayerKeepsTheOtherLayersScaling() {
        AttributeInstance scale = scaleAttribute();
        grant(scale, GOLEM_SIZE, GOLEM_SCALE);
        grant(scale, TITAN_SIZE, TITAN_SCALE);

        // applyOriginPowers(originLayer, golem, golem): revoke the old origin's
        // powers, sweep, re-grant. The class layer is untouched and stays active.
        Set<Identifier> stillActive = Set.of(GOLEM_SIZE, TITAN_SIZE);
        revoke(scale, GOLEM_SIZE);
        assertTrue(hasModifier(scale, TITAN_SIZE),
            "revoking the origin layer's size power must not touch the class layer's");
        sweep(scale, stillActive);
        grant(scale, GOLEM_SIZE, GOLEM_SCALE);

        assertTrue(hasModifier(scale, GOLEM_SIZE));
        assertTrue(hasModifier(scale, TITAN_SIZE));
        assertEquals(1.55, scale.getValue(), 1.0e-9,
            "re-picking the origin layer must leave the player at 1.55, not the re-granted layer alone");
    }

    @Test
    void swappingAwayFromOneLayerLeavesOnlyTheOther() {
        AttributeInstance scale = scaleAttribute();
        grant(scale, GOLEM_SIZE, GOLEM_SCALE);
        grant(scale, TITAN_SIZE, TITAN_SCALE);

        // applyOriginPowers(originLayer, golem, null): the orb-commit clear path.
        revoke(scale, GOLEM_SIZE);
        sweep(scale, Set.of(TITAN_SIZE));

        assertFalse(hasModifier(scale, GOLEM_SIZE));
        assertEquals(1.25, scale.getValue(), 1.0e-9);
    }

    /**
     * GitHub #90: a size modifier whose owning power no longer resolves never gets an
     * {@code onRevoked} call, so the sweep has to remove it or the player is left
     * permanently rescaled. Scoping the revoke hook must not weaken that.
     */
    @Test
    void sweepStillRemovesModifiersWhoseOwningPowerIsGone() {
        AttributeInstance scale = scaleAttribute();
        grant(scale, GOLEM_SIZE, GOLEM_SCALE);
        grant(scale, TITAN_SIZE, TITAN_SCALE);

        // Golem's power JSON was deleted or renamed: no holder, so no revoke ran.
        sweep(scale, Set.of(TITAN_SIZE));

        assertFalse(hasModifier(scale, GOLEM_SIZE),
            "an orphaned size modifier must still be swept");
        assertTrue(hasModifier(scale, TITAN_SIZE));
        assertEquals(1.25, scale.getValue(), 1.0e-9);
    }

    /**
     * The sweeper builds its keep-prefixes from {@code powerKeyFor} while the ids are
     * built by {@code ownIdPrefix}. If those two ever disagree the sweeper would drop
     * live modifiers, which is a far worse failure than the bug this fixes, so assert
     * the derivations agree across the id shapes that reach them.
     */
    @Test
    void keepPrefixMatchesTheEmittedIdForEveryPowerIdShape() {
        for (Identifier powerId : new Identifier[] {
                GOLEM_SIZE,
                TITAN_SIZE,
                Identifier.fromNamespaceAndPath("neoorigins", "nested/folder/size"),
                Identifier.fromNamespaceAndPath("some_pack", "a.b-c_1"),
        }) {
            String prefix = SizeScalingPower.ownIdPrefix(powerId);
            Set<String> keep = AttributeModifierPower.keepPrefixesFor(Set.of(powerId));

            assertTrue(keep.contains(prefix),
                "sweeper keep-set is missing the size prefix for " + powerId + ": " + keep);
            assertTrue(keep.contains(AttributeModifierPower.MOD_ID_PREFIX
                    + AttributeModifierPower.powerKeyFor(powerId) + "_"),
                "sweeper keep-set is missing the attribute_modifier prefix for " + powerId);

            for (String suffix : new String[] { "scale", "reach_block", "reach_entity",
                    "reach_bonus_block", "reach_bonus_entity" }) {
                Identifier modId = SizeScalingPower.modIdFor(powerId, suffix);
                assertTrue(modId.getPath().startsWith(prefix),
                    modId + " does not start with its own prefix " + prefix);
                assertTrue(AttributeModifierPower.isSweptId(modId),
                    modId + " is not recognised as a swept id");
                assertFalse(AttributeModifierPower.isOrphanId(modId, keep),
                    modId + " would be swept while its power is still active");
                assertTrue(AttributeModifierPower.isOrphanId(modId, Set.of()),
                    modId + " would survive with no active powers");
            }
        }
    }

    /** Modifiers from other mods, and from NeoOrigins powers outside the swept families, are left alone. */
    @Test
    void sweepIgnoresForeignModifiers() {
        Set<String> keep = AttributeModifierPower.keepPrefixesFor(Set.of(TITAN_SIZE));
        assertFalse(AttributeModifierPower.isOrphanId(
            Identifier.fromNamespaceAndPath("somemod", "size_thing"), keep));
        assertFalse(AttributeModifierPower.isOrphanId(
            Identifier.fromNamespaceAndPath("neoorigins", "tamed_animal_boost"), keep));

        AttributeInstance scale = scaleAttribute();
        Identifier foreign = Identifier.fromNamespaceAndPath("somemod", "size_thing");
        scale.addPermanentModifier(
            new AttributeModifier(foreign, 0.5, AttributeModifier.Operation.ADD_VALUE));
        sweep(scale, Set.of());
        assertNotNull(scale.getModifier(foreign));
    }
}
