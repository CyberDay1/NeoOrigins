package com.cyberday1.neoorigins.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class NeoOriginsKeybindings {

    private static final String NEOORIGINS_CATEGORY = "key.category.neoorigins.neoorigins";

    /** Separate category so a pool of 32+ "Hotkey N" rows doesn't crowd out
     *  the main Skill 1..6 / View Info / Class Skill entries in Controls. */
    private static final String HOTKEYS_CATEGORY = "key.category.neoorigins.hotkeys";

    /**
     * Anonymous KeyMappings backing pack-declared {@code "key": "..."} bindings.
     * Sized at {@link #onRegisterKeyMappings} from {@link NeoOriginsClientConfig#hotkeyPoolSize()}
     * — config is loaded by the time {@code RegisterKeyMappingsEvent} fires, so
     * the read is stable for the lifetime of the client JVM.
     *
     * <p>Each entry is unbound by default; players assign them in Controls.
     * {@link HotkeyAssignments} maps server-declared translation keys onto
     * these slots at login (and on {@code /reload}).
     */
    public static KeyMapping[] HOTKEY_POOL = new KeyMapping[0];

    public static final KeyMapping SKILL_1 = new KeyMapping(
        "key.neoorigins.skill_1",
        GLFW.GLFW_KEY_V,
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping SKILL_2 = new KeyMapping(
        "key.neoorigins.skill_2",
        GLFW.GLFW_KEY_G,
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping SKILL_3 = new KeyMapping(
        "key.neoorigins.skill_3",
        GLFW.GLFW_KEY_N,
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping SKILL_4 = new KeyMapping(
        "key.neoorigins.skill_4",
        GLFW.GLFW_KEY_B,
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping SKILL_5 = new KeyMapping(
        "key.neoorigins.skill_5",
        GLFW.GLFW_KEY_UNKNOWN,  // unbound by default
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping SKILL_6 = new KeyMapping(
        "key.neoorigins.skill_6",
        GLFW.GLFW_KEY_UNKNOWN,  // unbound by default
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping CLASS_SKILL = new KeyMapping(
        "key.neoorigins.class_skill",
        GLFW.GLFW_KEY_H,
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping VIEW_INFO = new KeyMapping(
        "key.neoorigins.view_info",
        GLFW.GLFW_KEY_O,
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping EDIT_HUD = new KeyMapping(
        "key.neoorigins.edit_hud",
        GLFW.GLFW_KEY_UNKNOWN,  // unbound by default
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping OPEN_CREATOR = new KeyMapping(
        "key.neoorigins.open_creator",
        GLFW.GLFW_KEY_UNKNOWN,  // unbound by default — server gates access anyway
        NEOORIGINS_CATEGORY
    );

    public static final KeyMapping OPEN_MOB_CREATOR = new KeyMapping(
        "key.neoorigins.open_mob_creator",
        GLFW.GLFW_KEY_UNKNOWN,  // unbound by default — server gates access anyway
        NEOORIGINS_CATEGORY
    );

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(SKILL_3);
        event.register(SKILL_4);
        event.register(SKILL_5);
        event.register(SKILL_6);
        event.register(CLASS_SKILL);
        event.register(VIEW_INFO);
        event.register(EDIT_HUD);
        event.register(OPEN_CREATOR);
        event.register(OPEN_MOB_CREATOR);

        // Build the named-hotkey pool from config. Each slot has translation key
        // "key.neoorigins.hotkey.<n>" (1-indexed for player friendliness) — lang
        // files supply human-readable names up to slot 64; beyond that the raw
        // key shows in Controls but the slot still works.
        int poolSize = NeoOriginsClientConfig.hotkeyPoolSize();
        // Optional modpack-supplied default bindings, "N=key.keyboard.X" (1-indexed).
        // Applied here at registration time — the only point MC lets us set a
        // KeyMapping's default. Players can still rebind afterward.
        int[] slotDefaults = resolveSlotDefaults(poolSize);
        HOTKEY_POOL = new KeyMapping[poolSize];
        for (int i = 0; i < poolSize; i++) {
            HOTKEY_POOL[i] = new KeyMapping(
                "key.neoorigins.hotkey." + (i + 1),
                slotDefaults[i],
                HOTKEYS_CATEGORY
            );
            event.register(HOTKEY_POOL[i]);
        }
    }

    /**
     * Parse the {@code [hotkeys] slot_defaults} config into a GLFW-code array sized
     * to the pool. Each entry is {@code "N=key.keyboard.X"} (N 1-indexed). Unlisted
     * or unparseable slots default to {@code GLFW_KEY_UNKNOWN} (unbound). A bad entry
     * is logged and skipped so one typo can't break the whole pool.
     */
    private static int[] resolveSlotDefaults(int poolSize) {
        int[] out = new int[poolSize];
        java.util.Arrays.fill(out, GLFW.GLFW_KEY_UNKNOWN);
        for (String entry : NeoOriginsClientConfig.hotkeySlotDefaults()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[Hotkeys] Ignoring malformed slot_defaults entry '{}' (expected \"N=key.keyboard.X\")", entry);
                continue;
            }
            String slotStr = entry.substring(0, eq).trim();
            String keyName = entry.substring(eq + 1).trim();
            int slot;
            try {
                slot = Integer.parseInt(slotStr);
            } catch (NumberFormatException e) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[Hotkeys] Ignoring slot_defaults entry '{}' — slot '{}' is not a number", entry, slotStr);
                continue;
            }
            if (slot < 1 || slot > poolSize) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[Hotkeys] Ignoring slot_defaults entry '{}' — slot {} outside pool 1..{}", entry, slot, poolSize);
                continue;
            }
            try {
                out[slot - 1] = com.mojang.blaze3d.platform.InputConstants.getKey(keyName).getValue();
            } catch (RuntimeException e) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[Hotkeys] Ignoring slot_defaults entry '{}' — unknown key id '{}'", entry, keyName);
            }
        }
        return out;
    }
}
