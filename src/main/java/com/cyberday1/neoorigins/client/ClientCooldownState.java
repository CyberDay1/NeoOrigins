package com.cyberday1.neoorigins.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClientCooldownState {

    public record CooldownEntry(int totalTicks, int remainingTicks) {}

    private static final Map<Integer, CooldownEntry> cooldowns = new HashMap<>();

    public static void set(int slot, int totalTicks, int remainingTicks) {
        cooldowns.put(slot, new CooldownEntry(totalTicks, remainingTicks));
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, CooldownEntry>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, CooldownEntry> e = it.next();
            int newRemaining = e.getValue().remainingTicks() - 1;
            if (newRemaining <= 0) {
                it.remove();
            } else {
                e.setValue(new CooldownEntry(e.getValue().totalTicks(), newRemaining));
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
