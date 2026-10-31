package com.cyberday1.neoorigins.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only originpack elements are skipped; anything else keeps failing the way vanilla fails it. */
class OriginpackLoadGuardTest {

    private static final ResourceKey<?> KEY =
        ResourceKey.create(Registries.ADVANCEMENT, Identifier.parse("origins-plus-plus:deathsworn/brute"));
    private static final Exception ERROR = new IllegalStateException("Failed to parse",
        new IllegalStateException("Only advancement roots can have background"));

    @Test
    void originpackElementIsSkipped() {
        assertTrue(OriginpackLoadGuard.skip("originpacks/originspp-2.4.jar", KEY, ERROR));
    }

    @Test
    void otherPacksAreNotSkipped() {
        assertFalse(OriginpackLoadGuard.skip("vanilla", KEY, ERROR));
        assertFalse(OriginpackLoadGuard.skip("file/mypack.zip", KEY, ERROR));
        assertFalse(OriginpackLoadGuard.skip("mod_resources", KEY, ERROR));
        assertFalse(OriginpackLoadGuard.skip(null, KEY, ERROR));
    }
}
