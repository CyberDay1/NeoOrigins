package com.cyberday1.neoorigins.power.capability;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-only dispatch target for {@link PowerCapabilities#hasActive}. Only invoked
 * when the level is client-side — never loaded on a dedicated server.
 */
final class ClientPowerCapabilitiesBridge {

    private ClientPowerCapabilitiesBridge() {}

    static boolean hasActive(LivingEntity entity, String tag) {
        Minecraft mc = Minecraft.getInstance();
        return mc != null
            && entity == mc.player
            && ClientActivePowers.hasCapability(tag);
    }
}
