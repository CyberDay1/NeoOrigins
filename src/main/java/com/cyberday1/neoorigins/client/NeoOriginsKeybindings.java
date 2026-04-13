package com.cyberday1.neoorigins.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class NeoOriginsKeybindings {

    public static final KeyMapping.Category NEOORIGINS_CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath("neoorigins", "neoorigins"));

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

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(NEOORIGINS_CATEGORY);
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(SKILL_3);
        event.register(SKILL_4);
        event.register(CLASS_SKILL);
        event.register(VIEW_INFO);
    }
}
