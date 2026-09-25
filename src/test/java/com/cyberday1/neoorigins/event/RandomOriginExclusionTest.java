package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side random assignment against the shipped origin layer. Every listed
 * origin is treated as loaded, which is the Dragon Survival-installed case: the
 * dragons are in the pool the roll could reach, and must never come out of it.
 */
class RandomOriginExclusionTest {

    private static final int ROLLS = 2000;
    private static final List<Identifier> DRAGONS = List.of(
        id("cave_dragon"), id("forest_dragon"), id("sea_dragon"));

    private static OriginLayer origin;

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        origin = RealDatapack.layers().getLayer(id("origin"));
    }

    @Test
    void aRandomRollNeverLandsOnADragon() {
        Map<Identifier, Integer> hits = roll(origin);
        for (Identifier dragon : DRAGONS) {
            assertFalse(hits.containsKey(dragon),
                dragon + " came out of " + ROLLS + " random rolls " + hits.get(dragon) + " times");
        }
        // A roll that only ever returned one origin would pass the check above too.
        List<Identifier> rollable = origin.getAvailableOriginIds().stream()
            .filter(o -> !DRAGONS.contains(o)).toList();
        assertEquals(rollable.size(), hits.size(), "every non-dragon origin should come up in " + ROLLS + " rolls");
    }

    @Test
    void aDirectPickCanStillBeADragon() {
        // The server's direct-pick gate is the layer's available list.
        List<Identifier> offered = origin.getAvailableOriginIds(Map.of());
        for (Identifier dragon : DRAGONS) {
            assertTrue(offered.contains(dragon), dragon + " must stay pickable");
            assertFalse(origin.isRandomCandidate(dragon), dragon + " must be excluded from random");
        }
    }

    @Test
    void theExclusionIsTheOnlyThingKeepingDragonsOut() {
        // Same layer without exclude_random: the dragons do come up, so the rig reaches them.
        OriginLayer open = new OriginLayer(origin.id(), origin.order(), origin.origins(), origin.enabled(),
            origin.name(), origin.allowRandom(), origin.defaultOrigin(), origin.autoChoose(), origin.hidden(),
            List.of());
        Map<Identifier, Integer> hits = roll(open);
        for (Identifier dragon : DRAGONS) {
            assertTrue(hits.containsKey(dragon), dragon + " never came up with no exclusion");
        }
    }

    @Test
    void aBlockedOriginIsNeverRolled() {
        Identifier human = id("human");
        Random rng = new Random(7);
        for (int i = 0; i < ROLLS; i++) {
            Identifier picked = PlayerLifecycleEvents.pickRandomOrigin(origin, Map.of(),
                o -> !o.equals(human), rng::nextInt);
            assertNotNull(picked);
            assertFalse(picked.equals(human), "an ineligible origin was rolled");
        }
    }

    private static Map<Identifier, Integer> roll(OriginLayer layer) {
        Random rng = new Random(42);
        Map<Identifier, Integer> hits = new HashMap<>();
        for (int i = 0; i < ROLLS; i++) {
            Identifier picked = PlayerLifecycleEvents.pickRandomOrigin(layer, Map.of(), o -> true, rng::nextInt);
            assertNotNull(picked, "the origin layer rolled nothing");
            hits.merge(picked, 1, Integer::sum);
        }
        return hits;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }
}
