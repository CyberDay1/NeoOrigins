package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A rendered player wears their own model_color tint, read from their own entry
 * in the per-player store, and loses it when their effect ends or they leave.
 * The local player's tints used to be painted onto every player on screen.
 */
class ModelColorTintRouteTest {

    private static final String RED = "model_color:1.0:0.0:0.0:1.0";
    private static final String GREEN = "model_color:0.0:1.0:0.0:0.5";

    private final UUID local = UUID.randomUUID();
    private final UUID remote = UUID.randomUUID();

    private static void caps(UUID player, Set<String> caps) {
        ClientPlayerPowers.set(player, Map.of(), Map.of(), caps, 0);
    }

    @AfterEach
    void reset() {
        ClientActivePowers.clear();
        ClientPlayerPowers.clear();
    }

    @Test
    void remotePlayerDoesNotWearTheLocalPlayersTint() {
        ClientActivePowers.set(Map.of(), Set.of(RED));
        caps(local, Set.of(RED));
        caps(remote, Set.of());

        assertNull(VisualEffectsHandler.tintFor(remote));
    }

    @Test
    void remotePlayerWearsTheirOwnTint() {
        caps(local, Set.of());
        caps(remote, Set.of(GREEN));

        assertArrayEquals(new float[] {0f, 1f, 0f, 0.5f}, VisualEffectsHandler.tintFor(remote));
        assertNull(VisualEffectsHandler.tintFor(local));
    }

    @Test
    void remoteTintClearsWhenTheirEffectEnds() {
        caps(remote, Set.of(GREEN, RED));
        assertArrayEquals(new float[] {0.5f, 0.5f, 0f, 0.75f}, VisualEffectsHandler.tintFor(remote));

        caps(remote, Set.of(RED));
        assertArrayEquals(new float[] {1f, 0f, 0f, 1f}, VisualEffectsHandler.tintFor(remote));

        caps(remote, Set.of());
        assertNull(VisualEffectsHandler.tintFor(remote));
    }

    @Test
    void remoteTintClearsWhenTheyLeaveRangeOrLogOut() {
        caps(remote, Set.of(GREEN));
        ClientPlayerPowers.remove(remote);
        assertNull(VisualEffectsHandler.tintFor(remote));

        caps(remote, Set.of(GREEN));
        ClientPlayerPowers.clear();
        assertNull(VisualEffectsHandler.tintFor(remote));
    }
}
