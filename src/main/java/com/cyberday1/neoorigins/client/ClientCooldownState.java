package com.cyberday1.neoorigins.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClientCooldownState {

    /**
     * @param icon      optional cooldown-HUD icon (item id or {@code .png} texture
     *                  path); empty = render the plain bar.
     * @param countdown true if remaining seconds should be drawn on the icon.
     */
    public record CooldownEntry(int totalTicks, int remainingTicks, String icon, boolean countdown) {}

    private static final Map<Integer, CooldownEntry> cooldowns = new HashMap<>();

    public static void set(int slot, int totalTicks, int remainingTicks, String icon, boolean countdown) {
        // Clamp at the source so HUD sweep/bar math (remaining / total) can
        // never divide by zero or exceed 1.0. totalTicks <= 0 (or < remaining)
        // is reachable: a power whose datapack declares cooldown_ticks: 0 can
        // share a cooldown KEY (via a getCooldownKey override) with a power
        // that set a longer duration, so the server syncs remaining > 0 with
        // a smaller config total.
        int total = Math.max(Math.max(1, totalTicks), remainingTicks);
        cooldowns.put(slot, new CooldownEntry(total, remainingTicks,
            icon == null ? "" : icon, countdown));
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, CooldownEntry>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, CooldownEntry> e = it.next();
            CooldownEntry old = e.getValue();
            int newRemaining = old.remainingTicks() - 1;
            if (newRemaining <= 0) {
                it.remove();
            } else {
                e.setValue(new CooldownEntry(old.totalTicks(), newRemaining, old.icon(), old.countdown()));
            }
        }
    }

    public static Map<Integer, CooldownEntry> getCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    public static void clear() {
        cooldowns.clear();
    }
}
