package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.network.payload.SyncResourcePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Stacked bars used to follow {@code HashMap} order, which no pack controls.
 * A full sync now stores them sorted by key, and value-only updates keep that
 * order.
 */
// hub: neoorigins/resource-bar-first-spend.md
class ResourceBarOrderTest {

    private static final List<String> KEYS = List.of(
        "zpack:mana", "apack:rage", "mpack:stamina", "neoorigins:qi_resource", "bpack:heat", "origins:charge");

    private static SyncResourcePayload.Entry entry(int value) {
        return new SyncResourcePayload.Entry(value, 0, 100, "bar", 0xFFFFFFFF, -1, -1, "", "", 0, 0, false);
    }

    private static Map<String, SyncResourcePayload.Entry> payload(List<String> keys) {
        Map<String, SyncResourcePayload.Entry> m = new HashMap<>();
        for (String k : keys) m.put(k, entry(50));
        return m;
    }

    private static List<String> order() {
        return new ArrayList<>(ClientResourceState.getResources().keySet());
    }

    @AfterEach
    void reset() {
        ClientResourceState.clear();
    }

    @Test
    void fullSyncStacksBarsByKey() {
        List<String> sorted = new ArrayList<>(KEYS);
        sorted.sort(null);
        // Precondition: hash order differs from key order, or this would pass on a HashMap.
        assertNotEquals(sorted, new ArrayList<>(payload(KEYS).keySet()));

        ClientResourceState.apply(payload(KEYS));
        assertEquals(sorted, order());
    }

    @Test
    void valueUpdatesKeepTheOrder() {
        ClientResourceState.apply(payload(KEYS));
        List<String> before = order();

        Map<String, Integer> values = new HashMap<>();
        for (String k : KEYS) values.put(k, 10);
        ClientResourceState.applyValues(values);

        assertEquals(before, order());
        assertEquals(10, ClientResourceState.getResources().get("apack:rage").value());
    }
}
