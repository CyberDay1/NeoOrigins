package com.cyberday1.neoorigins.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class NeoOriginsKeybindings {

    private static final String NEOORIGINS_CATEGORY = "key.category.neoorigins.neoorigins";

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

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(SKILL_3);
        event.register(SKILL_4);
    }
}
