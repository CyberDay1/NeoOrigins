package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClientOriginState {

    private static Map<Identifier, Identifier> origins = new HashMap<>();
    private static boolean hadAllOrigins = false;

    public static void setOrigins(Map<Identifier, Identifier> newOrigins, boolean hadAll) {
        origins = new HashMap<>(newOrigins);
        hadAllOrigins = hadAll;
    }

    public static Map<Identifier, Identifier> getOrigins() {
        return Collections.unmodifiableMap(origins);
    }

    public static Identifier getOrigin(Identifier layerId) {
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
