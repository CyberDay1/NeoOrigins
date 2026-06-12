package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the power → keybind-slot mapping on the client.
 *
 * <p>The server assigns skill slots by walking an origin's {@code powers} list
 * in order and giving each active (keybind-occupying) power the next slot,
 * capped at 6 ({@code NeoOriginsNetwork.syncAbilitySlotsToPlayer}); the class
 * layer's first active power gets the class-skill slot (-1). This class
 * mirrors that rule for UI that has to show slot assignments <i>before</i>
 * the server roster exists for the viewing player (origin selection screen)
 * or per-origin (origin info screen), and resolves a slot to the key actually
 * bound to it — the same {@link NeoOriginsKeybindings} mappings the HUD
 * cluster labels read.
 *
 * <p>Used by {@code CooldownHudOverlay} (HUD key labels) and
 * {@code OriginDetailViewModel} (selection / info screen "[R]" tags), so the
 * three surfaces can never disagree about which key fires which power.
 */
public final class AbilitySlotKeys {

    private AbilitySlotKeys() {}

    /** Skill slots the roster supports (mirrors the server-side cap). */
    public static final int MAX_SKILL_SLOTS = 6;

    /** Class-active pseudo-slot index, as used by the slot roster payload. */
    public static final int CLASS_SLOT = -1;

    private static final String[] SLOT_LABELS = {"S1", "S2", "S3", "S4", "S5", "S6"};

    /**
     * Mirror of the server's slot assignment for a single origin: actives in
     * {@code powers} order get skill slots 0–5; on the class layer only the
     * first active power gets the {@link #CLASS_SLOT}. Returns an insertion-
     * ordered (slot-ordered) map of power id → slot index.
     *
     * <p>Note: with multiple non-class origin layers the server concatenates
     * actives across layers, so per-origin numbering can shift; with the stock
     * one-origin-plus-class setup this matches the live roster exactly.
     */
    public static Map<Identifier, Integer> assignSlots(Origin origin, boolean classLayer) {
        Map<Identifier, Integer> out = new LinkedHashMap<>();
        if (origin == null) return out;
        int slot = 0;
        for (Identifier powerId : origin.powers()) {
            if (!isPowerActive(powerId)) continue;
            if (classLayer) {
                out.put(powerId, CLASS_SLOT);
                break;                      // server only wires the first class active
            }
            out.put(powerId, slot++);
            if (slot >= MAX_SKILL_SLOTS) break;
        }
        return out;
    }

    /** The {@link KeyMapping} wired to a slot, or {@code null} for unknown slots. */
    public static KeyMapping mappingFor(int slot) {
        return switch (slot) {
            case 0 -> NeoOriginsKeybindings.SKILL_1;
            case 1 -> NeoOriginsKeybindings.SKILL_2;
            case 2 -> NeoOriginsKeybindings.SKILL_3;
            case 3 -> NeoOriginsKeybindings.SKILL_4;
            case 4 -> NeoOriginsKeybindings.SKILL_5;
            case 5 -> NeoOriginsKeybindings.SKILL_6;
            case CLASS_SLOT -> NeoOriginsKeybindings.CLASS_SKILL;
            default -> null;
        };
    }

    /**
     * Display name of the key actually bound to a slot ("R", "V", mouse
     * buttons included), untruncated. {@code null} when the slot is unknown
     * or its mapping is unbound — callers fall back to {@link #legacyLabel}.
     */
    public static String keyName(int slot) {
        KeyMapping km = mappingFor(slot);
        if (km == null || km.isUnbound()) return null;
        String s = km.getTranslatedKeyMessage().getString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Legacy slot label: S1–S6 for skill slots, C for the class active. */
    public static String legacyLabel(int slot) {
        return (slot >= 0 && slot < SLOT_LABELS.length) ? SLOT_LABELS[slot] : "C";
    }

    /** Bound-key name, falling back to the legacy slot label when unbound. */
    public static String keyNameOrLabel(int slot) {
        String key = keyName(slot);
        return key != null ? key : legacyLabel(slot);
    }

    /**
     * True if the power occupies a keybind slot. Resolves through the local
     * {@link PowerDataManager} (integrated server / singleplayer) and falls
     * back to the login-synced {@link ClientPowerCache} on dedicated servers.
     */
    public static boolean isPowerActive(Identifier powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.isActive();
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null && entry.active();
    }
}
