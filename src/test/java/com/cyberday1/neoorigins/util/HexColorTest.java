package com.cyberday1.neoorigins.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Only 6 or 8 hex digits are colours; every other length is invalid rather than a small number. */
class HexColorTest {

    @Test
    void sixDigitsAreOpaque() {
        assertEquals(0xFF55AAFF, HexColor.parse("#55AAFF"));
        assertEquals(0xFF55AAFF, HexColor.parse("55aaff"));
    }

    @Test
    void eightDigitsKeepTheirAlpha() {
        assertEquals(0x8033CC66, HexColor.parse("#8033CC66"));
        assertEquals(0x00000000, HexColor.parse("#00000000"));
        assertEquals(0xFFFFFFFF, HexColor.parse("FFFFFFFF"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#FFF", "#FFFF", "#FFFFF", "#FFFFFFF", "#FFFFFFFFF", "FFF", "", "#"})
    void wrongLengthsAreInvalid(String s) {
        assertNull(HexColor.parse(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#GGGGGG", "#12345Z", "red", "#-FFFFFF", "+FFFFFFF", "# 55AAF", "0x55AAFF"})
    void nonHexIsInvalid(String s) {
        assertNull(HexColor.parse(s));
    }

    @Test
    void nullIsInvalid() {
        assertNull(HexColor.parse(null));
    }
}
