package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.network.payload.SyncResourcePayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side state for all active resource bars.
 * Updated by {@link SyncResourcePayload} from the server.
 * Read by {@link ResourceHudOverlay} each frame.
 */
public class ClientResourceState {

    public record ResourceEntry(int value, int min, int max, String label, int color,
                                int barIndex, int iconIndex, String spriteLocation,
                                String animated, int tint) {
        public float fraction() {
            int range = max - min;
            if (range <= 0) return 1.0f;
            return Math.max(0, Math.min(1, (float)(value - min) / range));
        }
    }

    private static final Map<String, ResourceEntry> resources = new HashMap<>();

    public static void apply(Map<String, SyncResourcePayload.Entry> incoming) {
        resources.clear();
        for (var e : incoming.entrySet()) {
            var v = e.getValue();
            resources.put(e.getKey(), new ResourceEntry(v.value(), v.min(), v.max(), v.label(), v.color(),
                v.barIndex(), v.iconIndex(), v.spriteLocation(), v.animated(), v.tint()));
        }
    }

    /**
     * Apply a value-only update onto existing entries. Metadata is kept from
     * the last full sync. Unknown keys are ignored — entry creation always
     * arrives via a full {@link #apply(Map)} (login/grant/origin-change/reload),
     * so an unknown key here is just a packet-ordering edge.
     */
    public static void applyValues(Map<String, Integer> incoming) {
        for (var e : incoming.entrySet()) {
            ResourceEntry old = resources.get(e.getKey());
            if (old == null) continue;
            resources.put(e.getKey(), new ResourceEntry(e.getValue(), old.min(), old.max(), old.label(),
                old.color(), old.barIndex(), old.iconIndex(), old.spriteLocation(), old.animated(), old.tint()));
        }
    }

    public static Map<String, ResourceEntry> getResources() {
        return Collections.unmodifiableMap(resources);
    }

    public static void clear() {
        resources.clear();
    }
}
