package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.event.OriginChangedEvent;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The one path for changing a player's origin on a layer: fires {@link OriginChangedEvent},
 * updates the data and unique-origin claims, swaps that layer's powers only, and syncs.
 * Callers keep their own extras (the picker's validation, the reset's selection screen).
 */
public final class OriginChangeService {

    private OriginChangeService() {}

    public enum Mode {
        /** Listeners may cancel or redirect the change. */
        NORMAL,
        /** Admin and system changes: the event still fires, but cancel and redirect are ignored. */
        ADMIN
    }

    /** Validated set for the API and commands. Rejects a unique origin another player holds unless ADMIN. */
    public static SetOriginResult set(ServerPlayer player, ResourceLocation layer, ResourceLocation origin, Mode mode) {
        if (!LayerDataManager.INSTANCE.hasLayer(layer)) return SetOriginResult.UNKNOWN_LAYER;
        if (!OriginDataManager.INSTANCE.hasOrigin(origin)) return SetOriginResult.UNKNOWN_ORIGIN;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (origin.equals(data.getOrigin(layer))) return SetOriginResult.UNCHANGED;
        if (mode == Mode.NORMAL && AdminConfig.isUniqueLayer(layer)
                && OriginClaimsData.get(player.getServer()).isClaimedByOther(layer, origin, player.getUUID())) {
            return SetOriginResult.CLAIMED;
        }
        Outcome outcome = change(player, layer, origin, mode, true);
        return outcome.result();
    }

    /** The result of {@link #change}, plus the origin that was actually set (a listener may redirect it). */
    public record Outcome(SetOriginResult result, @Nullable ResourceLocation origin) {}

    /**
     * Changes the layer without the layer/origin checks, for callers that have already validated.
     * {@code sync} false leaves {@code syncToPlayer} to the caller (the picker syncs once at the end).
     */
    public static Outcome change(ServerPlayer player, ResourceLocation layer, ResourceLocation origin,
                                 Mode mode, boolean sync) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        ResourceLocation old = data.getOrigin(layer);
        OriginChangedEvent event = new OriginChangedEvent(player, layer, old, origin);
        boolean cancelled = NeoForge.EVENT_BUS.post(event).isCanceled();
        ResourceLocation target = origin;
        if (mode == Mode.NORMAL) {
            if (cancelled) return new Outcome(SetOriginResult.CANCELLED, null);
            target = event.getNewOrigin();
            if (target == null || !OriginDataManager.INSTANCE.hasOrigin(target)) {
                return new Outcome(SetOriginResult.UNKNOWN_ORIGIN, null);
            }
        }

        data.setOrigin(layer, target);
        if (AdminConfig.isUniqueLayer(layer)) {
            var claims = OriginClaimsData.get(player.getServer());
            if (old != null && !old.equals(target)) claims.releaseIfOwner(layer, old, player.getUUID());
            claims.claim(layer, target, player.getUUID());
            NeoOriginsNetwork.syncClaimsToAll(player.getServer());
        }
        ActiveOriginService.applyOriginPowers(player, layer, old, target);
        if (sync) NeoOriginsNetwork.syncToPlayer(player);
        return new Outcome(SetOriginResult.SUCCESS, target);
    }

    /**
     * Empties one layer: revokes only that layer's powers and keeps every other layer and all
     * dynamic grants. Returns false if the layer held nothing. No event: it has no "cleared" form.
     */
    public static boolean clear(ServerPlayer player, ResourceLocation layer) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        ResourceLocation old = data.getOrigin(layer);
        if (old == null) return false;
        ActiveOriginService.applyOriginPowers(player, layer, old, null);
        data.removeOrigin(layer);
        if (AdminConfig.isUniqueLayer(layer)) {
            OriginClaimsData.get(player.getServer()).releaseIfOwner(layer, old, player.getUUID());
            NeoOriginsNetwork.syncClaimsToAll(player.getServer());
        }
        NeoOriginsNetwork.syncToPlayer(player);
        return true;
    }

    /**
     * Empties every layer: revokes all powers, including dynamic grants, clears the data and
     * releases the player's claims, then re-grants matching global power sets.
     */
    public static void clearAll(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        Map<ResourceLocation, ResourceLocation> releasing = new HashMap<>(data.getOrigins());
        ActiveOriginService.revokeAllPowers(player);
        data.clear();
        var claims = OriginClaimsData.get(player.getServer());
        releasing.forEach((l, o) -> {
            if (o != null && AdminConfig.isUniqueLayer(l)) claims.releaseIfOwner(l, o, player.getUUID());
        });
        NeoOriginsNetwork.syncClaimsToAll(player.getServer());
        // revokeAllPowers cleared the global-power ledger; re-grant matching global sets.
        GlobalPowerService.reconcilePlayer(player);
        NeoOriginsNetwork.syncToPlayer(player);
    }
}
