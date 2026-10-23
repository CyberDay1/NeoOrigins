package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.network.payload.SyncGlowingEntitiesPayload;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native {@code neoorigins:entity_glow}: living entities matching
 * {@code entity_condition} glow for the holder, and only for the holder, the way
 * a Spectral Arrow outlines a target. Apoli's power of the same name; Origins'
 * Arachnid uses it to sense non-arthropods caught in cobweb.
 *
 * <p>Same shape as {@link PreventEntityRenderPower}, for the same reasons, and it
 * shares that power's condition reader and entity scan: the server evaluates the
 * condition every few ticks and syncs the matching ids to the holder alone; the
 * client answers {@code shouldEntityAppearGlowing} from that set. The target gets
 * no Glowing effect, so nobody else sees the outline.
 */
public class EntityGlowPower extends PowerType<PreventEntityRenderPower.Config> {

    private static final int EVAL_INTERVAL_TICKS = 5;

    private static final Codec<PreventEntityRenderPower.Config> CODEC =
        PreventEntityRenderPower.Config.codecFor("entity_glow", "outline");

    private static final Map<UUID, Set<Integer>> lastSent = new ConcurrentHashMap<>();

    @Override public Codec<PreventEntityRenderPower.Config> codec() { return CODEC; }

    @Override
    public void onTick(ServerPlayer player, PreventEntityRenderPower.Config config) {
        if (player.tickCount % EVAL_INTERVAL_TICKS != 0) return;
        Set<Integer> glowing = PreventEntityRenderPower.matchingEntityIds(player, config);
        if (glowing.equals(lastSent.get(player.getUUID()))) return;
        lastSent.put(player.getUUID(), glowing);
        PacketDistributor.sendToPlayer(player, new SyncGlowingEntitiesPayload(new ArrayList<>(glowing)));
    }

    @Override
    public void onRevoked(ServerPlayer player, PreventEntityRenderPower.Config config) {
        lastSent.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SyncGlowingEntitiesPayload(List.of()));
    }

    @Override
    public void onRespawn(ServerPlayer player, PreventEntityRenderPower.Config config) {
        lastSent.remove(player.getUUID());
    }

    @Override
    public void onGranted(ServerPlayer player, PreventEntityRenderPower.Config config) {
        lastSent.remove(player.getUUID());
    }
}
