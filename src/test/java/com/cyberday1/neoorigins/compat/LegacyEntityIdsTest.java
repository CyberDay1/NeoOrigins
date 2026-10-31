package com.cyberday1.neoorigins.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression for issue #110 follow-up item 2: Origins++'s shifter enderian
 * form fires an origins:fire_projectile with entity_type
 * {@code origins:enderian_pearl} — an entity the original Origins mod
 * registered in code. parseFireProjectile used to refuse to compile the power
 * (unknown entity type); it now remaps the id to the vanilla ender pearl and
 * flags the spawned pearl so CompatEventPowers restores Origins' landing
 * behaviour (no fall damage, no endermite).
 */
class LegacyEntityIdsTest {

    @Test
    void remapsEnderianPearlToVanillaEnderPearl() {
        assertEquals("minecraft:ender_pearl", LegacyEntityIds.remap("origins:enderian_pearl"));
    }

    @Test
    void legacyPotionSplitsBySplashOrLingeringItem() {
        assertEquals("minecraft:splash_potion", LegacyEntityIds.remap("minecraft:potion"));
        assertEquals("minecraft:splash_potion", LegacyEntityIds.remap("minecraft:potion", null));
        assertEquals("minecraft:splash_potion", LegacyEntityIds.remap("minecraft:potion",
            "{Item:{id:\"minecraft:splash_potion\",Count:1b}}"));
        assertEquals("minecraft:lingering_potion", LegacyEntityIds.remap("minecraft:potion",
            "{Item:{id:\"minecraft:lingering_potion\",Count:1b,tag:{Potion:\"minecraft:poison\"}}}"));
        // Modern ids are left alone whatever the tag says.
        assertEquals("minecraft:splash_potion", LegacyEntityIds.remap("minecraft:splash_potion",
            "{Item:{id:\"minecraft:lingering_potion\"}}"));
    }

    @Test
    void unmappedIdsPassThroughUnchanged() {
        assertEquals("minecraft:ender_pearl", LegacyEntityIds.remap("minecraft:ender_pearl"));
        assertEquals("minecraft:arrow", LegacyEntityIds.remap("minecraft:arrow"));
        assertEquals("origins:not_a_real_entity", LegacyEntityIds.remap("origins:not_a_real_entity"));
        assertNull(LegacyEntityIds.remap(null));
    }
}
