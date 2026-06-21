package com.cyberday1.neoorigins.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class NeoOriginsKeybindings {

    public static final KeyMapping.Category NEOORIGINS_CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath("neoorigins", "neoorigins"));

    /** Separate category so a pool of 32+ "Hotkey N" rows doesn't crowd out
     *  the main Skill 1..6 / View Info / Class Skill entries in Controls. */
    public static final KeyMapping.Category HOTKEYS_CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath("neoorigins", "hotkeys"));

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
        event.registerCategory(NEOORIGINS_CATEGORY);
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
        event.registerCategory(HOTKEYS_CATEGORY);
        int poolSize = NeoOriginsClientConfig.hotkeyPoolSize();
        HOTKEY_POOL = new KeyMapping[poolSize];
        for (int i = 0; i < poolSize; i++) {
            HOTKEY_POOL[i] = new KeyMapping(
                "key.neoorigins.hotkey." + (i + 1),
                GLFW.GLFW_KEY_UNKNOWN,
                HOTKEYS_CATEGORY
            );
            event.register(HOTKEY_POOL[i]);
        }
    }
}
