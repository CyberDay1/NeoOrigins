package com.cyberday1.neoorigins.config;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The selection rule {@code classic_mode} adds on top of the per-origin and
 * per-class toggles, tested apart from the loaded config.
 */
class ClassicModeToggleRuleTest {

    private static final Predicate<String> NONE_OFF = p -> false;

    private static boolean disabled(String path, boolean classic) {
        return ContentTogglesConfig.isBuiltInDisabled(path, classic, NONE_OFF);
    }

    @Test
    void classicModeHidesEveryNonClassicBuiltIn() {
        assertTrue(disabled("sword_immortal", true));
        assertTrue(disabled("caveborn", true));
        assertTrue(disabled("golden_body", true));
        assertTrue(disabled("class_paladin", true));
        assertTrue(disabled("class_herbalist", true));
    }

    /** The dragons have no toggle of their own, so they need classic mode's own list. */
    @Test
    void classicModeHidesTheUntoggledDragons() {
        assertTrue(disabled("cave_dragon", true));
        assertTrue(disabled("sea_dragon", true));
        assertFalse(disabled("cave_dragon", false));
    }

    @Test
    void classicModeKeepsTheOriginalTenAndFourteen() {
        for (String path : ContentTogglesConfig.CLASSIC_ORIGINS) assertFalse(disabled(path, true), path);
        for (String path : ContentTogglesConfig.CLASSIC_CLASSES) assertFalse(disabled(path, true), path);
        assertEquals(10, ContentTogglesConfig.CLASSIC_ORIGINS.size());
        assertEquals(14, ContentTogglesConfig.CLASSIC_CLASSES.size());
    }

    @Test
    void classicOnlyClassesAreHiddenOutsideClassicMode() {
        assertTrue(disabled("class_farmer", false));
        assertTrue(disabled("class_rancher", false));
        assertFalse(disabled("class_farmer", true));
    }

    @Test
    void normalModeChangesNothingForTheExistingBuiltIns() {
        assertFalse(disabled("sword_immortal", false));
        assertFalse(disabled("class_paladin", false));
        assertFalse(disabled("avian", false));
    }

    /** A classic origin the server owner switched off stays off in classic mode. */
    @Test
    void anOffToggleWinsInBothModes() {
        Predicate<String> phantomOff = Set.of("phantom")::contains;
        assertTrue(ContentTogglesConfig.isBuiltInDisabled("phantom", true, phantomOff));
        assertTrue(ContentTogglesConfig.isBuiltInDisabled("phantom", false, phantomOff));
    }

    /** A pack that adds its own origin to our namespace is not ours to hide. */
    @Test
    void nonBuiltInPathsAreLeftAlone() {
        assertFalse(disabled("my_pack_origin", true));
        assertFalse(disabled("my_pack_origin", false));
    }

    @Test
    void everyClassicPathIsABuiltIn() {
        for (String path : ContentTogglesConfig.CLASSIC_ORIGINS) assertTrue(ContentTogglesConfig.isBuiltIn(path), path);
        for (String path : ContentTogglesConfig.CLASSIC_CLASSES) assertTrue(ContentTogglesConfig.isBuiltIn(path), path);
    }
}
