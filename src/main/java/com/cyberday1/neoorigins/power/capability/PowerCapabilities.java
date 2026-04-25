package com.cyberday1.neoorigins.power.capability;

import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Side-agnostic query for "does this entity currently have capability tag X active?".
 *
 * <p>Server-side: delegates to {@link ActiveOriginService#hasCapability}.
 * <br>Client-side: delegates to {@link ClientPowerCapabilitiesBridge}, which checks
 * that the entity is the local player and the tag is in the synced capability set.
 *
 * <p>Used by client-predicted physics mixins (wall-climb, flight, etc.) so the same
 * check runs on both logical sides without rubber-banding.
 */
public final class PowerCapabilities {

    private PowerCapabilities() {}

    public static boolean hasActive(LivingEntity entity, String tag) {
        if (entity == null || entity.isSpectator()) return false;
        if (!entity.level().isClientSide()) {
            return entity instanceof ServerPlayer sp
                && ActiveOriginService.hasCapability(sp, tag);
        }
        // Client branch is resolved through a separate class so this one can be
        // loaded on the dedicated server without triggering a ClassNotFoundException
        // for Minecraft.getInstance().
        return ClientPowerCapabilitiesBridge.hasActive(entity, tag);
    }
}
