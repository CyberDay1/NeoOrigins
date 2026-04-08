package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.event.PowerGrantedEvent;
import com.cyberday1.neoorigins.api.event.PowerRevokedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Central service for traversing a player's active powers.
 * All iteration is deterministic: layers are visited in sorted ID order.
 */
public final class ActiveOriginService {

    private ActiveOriginService() {}

    /** Returns all power holders in deterministic (sorted layer → power) order. */
    public static List<PowerHolder<?>> allPowers(ServerPlayer player) {
        List<PowerHolder<?>> result = new ArrayList<>();
        forEach(player, result::add);
        return result;
    }

    /** Iterates all power holders in sorted layer order, respecting dimension restrictions. */
    public static void forEach(ServerPlayer player, Consumer<PowerHolder<?>> action) {
        ResourceKey<Level> dimension = player.level().dimension();
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.getOrigins().entrySet().stream()
            .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
            .forEach(entry -> {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                if (origin == null) return;
                for (Identifier powerId : origin.powers()) {
                    if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dimension)) continue;
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) action.accept(holder);
                }
            });
    }

    /** Iterates powers of a specific type, passing the typed config to the action. */
    @SuppressWarnings("unchecked")
    public static <C extends PowerConfiguration, T extends PowerType<C>>
    void forEachOfType(ServerPlayer player, Class<T> typeClass, Consumer<C> action) {
        forEach(player, holder -> {
            if (typeClass.isInstance(holder.type())) {
                action.accept((C) holder.config());
            }
        });
    }

    /** Returns true if the player has a power of the given type satisfying the predicate. */
    @SuppressWarnings("unchecked")
    public static <C extends PowerConfiguration, T extends PowerType<C>>
    boolean has(ServerPlayer player, Class<T> typeClass, Predicate<C> predicate) {
        ResourceKey<Level> dimension = player.level().dimension();
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dimension)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null && typeClass.isInstance(holder.type())) {
                    if (predicate.test((C) holder.config())) return true;
                }
            }
        }
        return false;
    }

    /** Returns active (keybind-slot) power holders in deterministic order. */
    public static List<PowerHolder<?>> activePowers(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        List<PowerHolder<?>> result = new ArrayList<>();
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.getOrigins().entrySet().stream()
            .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
            .forEach(entry -> {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                if (origin == null) return;
                for (Identifier powerId : origin.powers()) {
                    if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dimension)) continue;
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null && holder.isActive()) result.add(holder);
                }
            });
        return result;
    }

    /** Revokes all powers across all layers. Called on player reset. */
    public static void revokeAllPowers(ServerPlayer player) {
        // Revoke bypasses dimension restrictions — always clean up all powers
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.getOrigins().entrySet().stream()
            .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
            .forEach(entry -> {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                if (origin == null) return;
                for (Identifier powerId : origin.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) holder.onRevoked(player);
                }
            });
    }

    /**
     * Revokes powers from oldOrigin and grants powers from newOrigin.
     * Posts PowerRevokedEvent / PowerGrantedEvent for each power changed.
     * Grant/revoke bypasses dimension restrictions to ensure clean state transitions.
     */
    public static void applyOriginPowers(ServerPlayer player, Identifier layerId,
                                          Identifier oldOriginId, Identifier newOriginId) {
        if (oldOriginId != null) {
            Origin oldOrigin = OriginDataManager.INSTANCE.getOrigin(oldOriginId);
            if (oldOrigin != null) {
                for (Identifier powerId : oldOrigin.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) {
                        holder.onRevoked(player);
                        NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, powerId));
                    }
                }
            }
        }
        Origin newOrigin = OriginDataManager.INSTANCE.getOrigin(newOriginId);
        if (newOrigin != null) {
            for (Identifier powerId : newOrigin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) {
                    holder.onGranted(player);
                    NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, powerId));
                }
            }
        }
    }
}
