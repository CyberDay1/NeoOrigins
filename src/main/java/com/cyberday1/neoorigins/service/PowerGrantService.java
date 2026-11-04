package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.event.PowerGrantedEvent;
import com.cyberday1.neoorigins.api.event.PowerRevokedEvent;
import com.cyberday1.neoorigins.api.power.GrantPowerResult;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.attachment.PlayerOriginData.PowerGrant;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Powers granted to a player through the Java API, kept per source so two addons never
 * clobber each other. A power is granted ({@code onGranted}) when the first holder of any kind
 * appears and revoked when the last one goes; origins, dynamic grants and every API source count.
 */
public final class PowerGrantService {

    private PowerGrantService() {}

    public static GrantPowerResult grant(ServerPlayer player, Identifier power, Identifier source,
                                         @Nullable Identifier layer) {
        if (expand(power).stream().noneMatch(id -> PowerDataManager.INSTANCE.getPower(id) != null)) {
            return GrantPowerResult.UNKNOWN_POWER;
        }
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        var existing = data.getPowerGrant(power, source);
        if (existing.isPresent() && java.util.Objects.equals(existing.get().layer(), layer)) {
            return GrantPowerResult.ALREADY_GRANTED;
        }
        Set<Identifier> before = ActiveOriginService.activePowerIds(player);
        data.addPowerGrant(new PowerGrant(power, source, layer));
        float maxHealthBefore = player.getMaxHealth();
        for (Identifier id : expand(power)) {
            if (!before.contains(id)) fireGranted(player, id);
        }
        finish(player, maxHealthBefore);
        return existing.isPresent() ? GrantPowerResult.UPDATED : GrantPowerResult.GRANTED;
    }

    public static boolean revoke(ServerPlayer player, Identifier power, Identifier source) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        var grant = data.getPowerGrant(power, source);
        if (grant.isEmpty()) return false;
        removeAndRevoke(player, List.of(grant.get()));
        return true;
    }

    /** Revokes {@code source}'s grants whose power matches {@code filter}. @return how many were removed. */
    public static int revokeMatching(ServerPlayer player, Identifier source, Predicate<PowerHolder<?>> filter) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        List<PowerGrant> matching = new ArrayList<>();
        for (PowerGrant g : data.getPowerGrants()) {
            if (!g.source().equals(source)) continue;
            for (Identifier id : expand(g.power())) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id);
                if (holder != null && filter.test(holder)) {
                    matching.add(g);
                    break;
                }
            }
        }
        if (!matching.isEmpty()) removeAndRevoke(player, matching);
        return matching.size();
    }

    public static Set<Identifier> granted(ServerPlayer player, Identifier source) {
        Set<Identifier> ids = new LinkedHashSet<>();
        for (PowerGrant g : player.getData(OriginAttachments.originData()).getPowerGrants()) {
            if (g.source().equals(source)) ids.add(g.power());
        }
        return ids;
    }

    private static void removeAndRevoke(ServerPlayer player, Collection<PowerGrant> grants) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        Set<Identifier> before = ActiveOriginService.activePowerIds(player);
        data.removePowerGrants(grants::contains);
        Set<Identifier> after = ActiveOriginService.activePowerIds(player);
        float maxHealthBefore = player.getMaxHealth();
        Set<Identifier> done = new HashSet<>();
        for (PowerGrant g : grants) {
            for (Identifier id : expand(g.power())) {
                if (before.contains(id) && !after.contains(id) && done.add(id)) fireRevoked(player, id);
            }
        }
        finish(player, maxHealthBefore);
    }

    /** The registered holder ids behind a power id: itself, or a {@code multiple}'s leaf sub-powers. */
    public static List<Identifier> expand(Identifier power) {
        var map = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP;
        if (!map.containsKey(power)) return List.of(power);
        List<Identifier> leaves = new ArrayList<>();
        Set<Identifier> visited = new HashSet<>();
        ArrayDeque<Identifier> pending = new ArrayDeque<>(map.get(power));
        while (!pending.isEmpty()) {
            Identifier id = pending.pop();
            if (!visited.add(id)) continue;
            List<Identifier> children = map.get(id);
            if (children != null) pending.addAll(children);
            else leaves.add(id);
        }
        return leaves;
    }

    /** The leaf power ids the player's API grants hold right now. */
    public static Set<Identifier> grantedLeafIds(PlayerOriginData data) {
        Set<Identifier> ids = new LinkedHashSet<>();
        for (Identifier power : data.getPowerGrantIds()) ids.addAll(expand(power));
        return ids;
    }

    /** True when an API grant holds {@code powerId} (a leaf id). */
    public static boolean isGranted(PlayerOriginData data, Identifier powerId) {
        return grantedLeafIds(data).contains(powerId);
    }

    static void fireGranted(ServerPlayer player, Identifier id) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id);
        if (holder == null) return;
        holder.onGranted(player);
        NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, holder));
        EventPowerIndex.dispatch(player, EventPowerIndex.Event.GAINED, id);
    }

    static void fireRevoked(ServerPlayer player, Identifier id) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id);
        if (holder == null) return;
        holder.onRevoked(player);
        NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, holder));
        EventPowerIndex.dispatch(player, EventPowerIndex.Event.LOST, id);
    }

    /** Fills gained hearts, clamps lost ones, and syncs. */
    private static void finish(ServerPlayer player, float maxHealthBefore) {
        float after = player.getMaxHealth();
        if (after > maxHealthBefore) player.setHealth(Math.min(player.getHealth() + (after - maxHealthBefore), after));
        else if (player.getHealth() > after) player.setHealth(after);
        NeoOriginsNetwork.syncToPlayer(player);
    }
}
