package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClientOriginState {

    private static Map<ResourceLocation, ResourceLocation> origins = new HashMap<>();
    private static boolean hadAllOrigins = false;

    public static void setOrigins(Map<ResourceLocation, ResourceLocation> newOrigins, boolean hadAll) {
        origins = new HashMap<>(newOrigins);
        hadAllOrigins = hadAll;
    }

    public static Map<ResourceLocation, ResourceLocation> getOrigins() {
        return Collections.unmodifiableMap(origins);
    }

    public static ResourceLocation getOrigin(ResourceLocation layerId) {
        return origins.get(layerId);
    }

    public static boolean isHadAllOrigins() { return hadAllOrigins; }

    public static void openSelectionScreen(boolean isOrb, boolean forceReselect) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new com.cyberday1.neoorigins.screen.OriginSelectionScreen(isOrb, forceReselect));
    }

    public static void openInfoScreen() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new com.cyberday1.neoorigins.screen.OriginInfoScreen());
    }
}
