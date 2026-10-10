package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bar_fx colour of the wrong length is a malformed preset, the same as non-hex. */
class BarFxColorTest {

    @Test
    void validColoursParse() {
        assertEquals(0xFF102030, BarFxManager.parseArgb("track_color", "#102030"));
        assertEquals(0xAA000000, BarFxManager.parseArgb("track_color", "#AA000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#FFF", "#FFFF", "#FFFFF", "#FFFFFFF", "#FFFFFFFFF", "#GGGGGG"})
    void invalidColourRejectsThePreset(String bad) {
        var ex = assertThrows(IllegalArgumentException.class, () -> BarFxManager.parseArgb("level_color", bad));
        assertTrue(ex.getMessage().contains("level_color") && ex.getMessage().contains(bad), ex.getMessage());
    }
}
