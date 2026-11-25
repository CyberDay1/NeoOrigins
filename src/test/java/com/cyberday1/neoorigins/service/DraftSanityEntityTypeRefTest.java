package com.cyberday1.neoorigins.service;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #156: the creator's Save gate accepts a well-formed #tag for entity_type without looking it up. */
class DraftSanityEntityTypeRefTest {

    private static RegistryAccess builtIn;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        builtIn = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void wellFormedTagsPassWithoutALookup() {
        assertTrue(DraftSanity.entityTypeRefOk(builtIn, "#minecraft:undead"));
        assertTrue(DraftSanity.entityTypeRefOk(builtIn, "#mypack:not_loaded_here"));
        assertFalse(DraftSanity.entityTypeRefOk(builtIn, "#Bad Tag"));
    }

    @Test
    void idsAreCheckedAgainstTheRegistry() {
        assertTrue(DraftSanity.entityTypeRefOk(builtIn, "minecraft:zombie"));
        assertTrue(DraftSanity.entityTypeRefOk(builtIn, "sheep"));
        assertFalse(DraftSanity.entityTypeRefOk(builtIn, "minecraft:not_a_mob"));
        assertFalse(DraftSanity.entityTypeRefOk(builtIn, "Bad Id"));
    }

    @Test
    void anUnavailableRegistryFlagsNothing() {
        assertTrue(DraftSanity.entityTypeRefOk(RegistryAccess.EMPTY, "minecraft:not_a_mob"));
    }
}
