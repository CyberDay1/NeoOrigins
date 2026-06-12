package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.network.payload.SyncAbilitySlotsPayload;

import java.util.List;

/**
 * Client-side mirror of the local player's keybind-slot ability roster
 * (skill slots 0–5 + class active -1), as last pushed by
 * {@code SyncAbilitySlotsPayload}.
 *
 * <p>Read by {@code CooldownHudOverlay} (toggle slots, always-shown icons,
 * the ALL_ACTIVE_ABILITIES display mode) and by {@code ResourceHudEditorScreen}
 * (real cluster preview + hover tooltips). Toggle on/off state comes from
 * {@link ClientActivePowers}; live cooldowns from {@link ClientCooldownState}.
 */
public final class ClientAbilitySlots {

    private static List<SyncAbilitySlotsPayload.Entry> slots = List.of();

    public static void set(List<SyncAbilitySlotsPayload.Entry> data) {
        slots = List.copyOf(data);
    }

    public static void clear() {
        slots = List.of();
    }

    /** Ordered, unmodifiable slot roster (skill slots first, class active last). */
    public static List<SyncAbilitySlotsPayload.Entry> get() {
        return slots;
    }

    private ClientAbilitySlots() {}
}
