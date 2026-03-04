package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.network.payload.OpenOriginScreenPayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginsPayload;
import com.cyberday1.neoorigins.api.event.OriginChangedEvent;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class NeoOriginsNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NeoOrigins.MOD_ID).versioned(PROTOCOL_VERSION);

        registrar.playToClient(
            SyncOriginsPayload.TYPE,
            SyncOriginsPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncOrigins
        );

        registrar.playToClient(
            OpenOriginScreenPayload.TYPE,
            OpenOriginScreenPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleOpenScreen
        );

        registrar.playToServer(
            ChooseOriginPayload.TYPE,
            ChooseOriginPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleChooseOrigin
        );
    }

    // ---------- Client-side handlers ----------

    private static void handleSyncOrigins(SyncOriginsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Store on client-side — use a client-only state holder
            com.cyberday1.neoorigins.client.ClientOriginState.setOrigins(
                payload.origins(), payload.hadAllOrigins());
        });
    }

    private static void handleOpenScreen(OpenOriginScreenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.setScreen(new com.cyberday1.neoorigins.screen.OriginSelectionScreen(payload.isOrb()));
        });
    }

    // ---------- Server-side handlers ----------

    private static void handleChooseOrigin(ChooseOriginPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            ResourceLocation layerId = payload.layer();
            ResourceLocation originId = payload.origin();

            // Validate layer exists
            OriginLayer layer = LayerDataManager.INSTANCE.getLayer(layerId);
            if (layer == null) {
                NeoOrigins.LOGGER.warn("Player {} tried to choose origin for unknown layer {}", sp.getName().getString(), layerId);
                return;
            }

            // Validate origin is in this layer
            if (!layer.getAvailableOriginIds().contains(originId)) {
                NeoOrigins.LOGGER.warn("Player {} tried to choose origin {} not in layer {}", sp.getName().getString(), originId, layerId);
                return;
            }

            // Validate origin exists
            if (!OriginDataManager.INSTANCE.hasOrigin(originId)) {
                NeoOrigins.LOGGER.warn("Player {} tried to choose non-existent origin {}", sp.getName().getString(), originId);
                return;
            }

            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            ResourceLocation oldOrigin = data.getOrigin(layerId);

            // Fire event (cancellable)
            OriginChangedEvent event = new OriginChangedEvent(sp, layerId, oldOrigin, originId);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) return;

            // Set origin, sync to client
            data.setOrigin(layerId, event.getNewOrigin());
            com.cyberday1.neoorigins.event.OriginEventHandler.applyOriginPowers(sp, layerId, oldOrigin, event.getNewOrigin());
            syncToPlayer(sp);
        });
    }

    /** Send the player's full origin data to themselves. */
    public static void syncToPlayer(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(player,
            new SyncOriginsPayload(data.getOrigins(), data.isHadAllOrigins()));
    }

    /** Open the origin selection screen on the client. */
    public static void openSelectionScreen(ServerPlayer player, boolean isOrb) {
        PacketDistributor.sendToPlayer(player, new OpenOriginScreenPayload(isOrb));
    }
}
