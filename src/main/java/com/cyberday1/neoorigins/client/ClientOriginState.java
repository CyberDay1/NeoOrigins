package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
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
}
