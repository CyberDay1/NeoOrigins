package com.cyberday1.neoorigins.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class NeoOriginsKeybindings {

    public static final KeyMapping.Category NEOORIGINS_CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath("neoorigins", "neoorigins"));

    public static final KeyMapping SECONDARY_ABILITY = new KeyMapping(
        "key.neoorigins.secondary_ability",
        GLFW.GLFW_KEY_G,
        NEOORIGINS_CATEGORY
    );

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(NEOORIGINS_CATEGORY);
        event.register(SECONDARY_ABILITY);
    }
}
