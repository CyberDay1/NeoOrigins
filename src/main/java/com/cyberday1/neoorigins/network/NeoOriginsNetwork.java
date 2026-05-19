package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.OriginChangedEvent;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.network.payload.ActivateClassPowerPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerPayload;
import com.cyberday1.neoorigins.network.payload.AirJumpPayload;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.network.payload.EditorTogglePowerPayload;
import com.cyberday1.neoorigins.network.payload.OpenEditorScreenPayload;
import com.cyberday1.neoorigins.network.payload.OpenOriginScreenPayload;
import com.cyberday1.neoorigins.network.payload.SyncActivePowersPayload;
import com.cyberday1.neoorigins.network.payload.SyncCooldownPayload;
import com.cyberday1.neoorigins.network.payload.SyncEvolutionConfigPayload;
import com.cyberday1.neoorigins.network.payload.SyncMoisturePayload;
import com.cyberday1.neoorigins.network.payload.SyncResourcePayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginRegistryPayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginsPayload;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.power.builtin.FlightPower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NeoOriginsNetwork {

    private static final String PROTOCOL_VERSION = "1";
    /** Minimum ticks between two activations of the same slot from the same player (anti-spam). */
    private static final int SLOT_DEBOUNCE_TICKS = 5;
    /** Key: "uuid:slot" → last tick that slot was activated. */
    private static final Map<String, Integer> LAST_ACTIVATE_TICK = new ConcurrentHashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NeoOrigins.MOD_ID).versioned(PROTOCOL_VERSION);

        registrar.playToClient(
            SyncOriginRegistryPayload.TYPE,
            SyncOriginRegistryPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncRegistry
        );

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

        registrar.playToClient(
            SyncCooldownPayload.TYPE,
            SyncCooldownPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncCooldown
        );

        registrar.playToClient(
            SyncMoisturePayload.TYPE,
            SyncMoisturePayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncMoisture
        );

        registrar.playToClient(
            SyncResourcePayload.TYPE,
            SyncResourcePayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncResource
        );

        registrar.playToClient(
            SyncEvolutionConfigPayload.TYPE,
            SyncEvolutionConfigPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncEvolutionConfig
        );

        registrar.playToClient(
            SyncActivePowersPayload.TYPE,
            SyncActivePowersPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncActivePowers
        );

        registrar.playToClient(
            OpenEditorScreenPayload.TYPE,
            OpenEditorScreenPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleOpenEditorScreen
        );

        registrar.playToClient(
            com.cyberday1.neoorigins.network.payload.CreatorResultPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.CreatorResultPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleCreatorResult
        );

        registrar.playToServer(
            ChooseOriginPayload.TYPE,
            ChooseOriginPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleChooseOrigin
        );

        registrar.playToServer(
            ActivatePowerPayload.TYPE,
            ActivatePowerPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleActivatePower
        );

        registrar.playToServer(
            AirJumpPayload.TYPE,
            AirJumpPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleAirJump
        );

        registrar.playToServer(
            ActivateClassPowerPayload.TYPE,
            ActivateClassPowerPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleActivateClassPower
        );

        registrar.playToServer(
            EditorTogglePowerPayload.TYPE,
            EditorTogglePowerPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleEditorTogglePower
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.CancelOrbPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.CancelOrbPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleCancelOrb
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.PickerAbandonedPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.PickerAbandonedPayload.STREAM_CODEC,
            NeoOriginsNetwork::handlePickerAbandoned
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.RequestOpenCreatorPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.RequestOpenCreatorPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleRequestOpenCreator
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.SaveCustomOriginPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.SaveCustomOriginPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSaveCustomOrigin
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.ApplyCustomPackPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.ApplyCustomPackPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleApplyCustomPack
        );
    }

    private static void handleCancelOrb(com.cyberday1.neoorigins.network.payload.CancelOrbPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.setPendingOrbCommit(false);
        });
    }

    private static void handlePickerAbandoned(com.cyberday1.neoorigins.network.payload.PickerAbandonedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.setPickerAbandoned(true);
        });
    }

    // ---------- Client-side handlers ----------

    private static void handleSyncRegistry(SyncOriginRegistryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            OriginDataManager.INSTANCE.setClientData(payload.origins());

            java.util.Map<Identifier, com.cyberday1.neoorigins.api.origin.OriginLayer> layerMap = new java.util.HashMap<>();
            for (var layer : payload.sortedLayers()) layerMap.put(layer.id(), layer);
            LayerDataManager.INSTANCE.setClientData(layerMap, payload.sortedLayers());

            com.cyberday1.neoorigins.client.ClientPowerCache.set(payload.powers());
            com.cyberday1.neoorigins.compat.OriginsMultipleExpander.setClientData(
                payload.multipleExpansionMap(), payload.multipleDisplayMap());
        });
    }

    private static void handleSyncOrigins(SyncOriginsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.cyberday1.neoorigins.client.ClientOriginState.setOrigins(
                payload.origins(), payload.hadAllOrigins());
            // Clear stale HUD state from a previous session/server — these static
            // fields survive across disconnect/reconnect within the same client JVM.
            // The server will re-send current values for any active resource/moisture
            // powers via their normal tick-sync paths.
            com.cyberday1.neoorigins.client.ClientMoistureState.clear();
            com.cyberday1.neoorigins.client.ClientResourceState.clear();
            com.cyberday1.neoorigins.client.ClientCooldownState.clear();
        });
    }

    private static void handleOpenScreen(OpenOriginScreenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientOriginState.openSelectionScreen(payload.isOrb(), payload.forceReselect())
        );
    }

    private static void handleSyncCooldown(SyncCooldownPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientCooldownState.set(payload.slot(), payload.totalTicks(), payload.remainingTicks())
        );
    }

    private static void handleSyncMoisture(SyncMoisturePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientMoistureState.set(payload.moisture())
        );
    }

    private static void handleSyncResource(SyncResourcePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientResourceState.apply(payload.resources())
        );
    }

    private static void handleSyncEvolutionConfig(SyncEvolutionConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientEvolutionConfig.sync(
                payload.enabled(), payload.tier1Kills(), payload.tier2Kills(),
                payload.tier3Kills(), payload.messageInterval(),
                payload.currentKills(), payload.currentTier())
        );
    }

    /**
     * Sends the server's evolution config + player's current progress to the client.
     * Called on login and whenever evolution state changes.
     */
    public static void syncEvolutionToPlayer(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(sp, new SyncEvolutionConfigPayload(
            NeoOriginsConfig.isEvolutionEnabled(),
            NeoOriginsConfig.evolutionTier1Kills(),
            NeoOriginsConfig.evolutionTier2Kills(),
            NeoOriginsConfig.evolutionTier3Kills(),
            NeoOriginsConfig.evolutionMessageInterval(),
            data.getEssenceKills(),
            data.getEvolutionTier()
        ));
    }

    private static void handleSyncActivePowers(SyncActivePowersPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientActivePowers.set(payload.powers(), payload.capabilities())
        );
    }

    private static void handleOpenEditorScreen(OpenEditorScreenPayload payload, IPayloadContext ctx) {
        // Defensive: payload is registered playToClient, but a malformed routing
        // shouldn't crash a dedicated server by classloading Minecraft. The
        // actual `new OriginEditorScreen(...)` lives in ClientOriginState so
        // its constant-pool reference to a Screen subclass stays out of this
        // common-side class — RuntimeDistCleaner walks NEW opcodes during
        // dedicated-server verification and rejects Screen if it's referenced
        // here directly.
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientOriginState.openEditorScreen()
        );
    }

    private static void handleCreatorResult(
            com.cyberday1.neoorigins.network.payload.CreatorResultPayload payload, IPayloadContext ctx) {
        // Registered playToClient; same dist-safety guard as handleOpenEditorScreen.
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientCreatorState.setResult(
                payload.ok(), payload.message())
        );
    }

    /**
     * Shared open path for the 2.1 creator (command + keybind). Gate-checked by
     * the caller; syncs registry/state then asks the client to open the screen
     * via {@link OpenEditorScreenPayload} (the Screen-opcode trampoline lives in
     * {@code ClientOriginState}, see {@link #handleOpenEditorScreen}).
     */
    public static void openCreatorFor(ServerPlayer sp) {
        syncRegistryToPlayer(sp);
        syncToPlayer(sp);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new OpenEditorScreenPayload());
    }

    private static void sendCreatorResult(ServerPlayer sp, boolean ok, String message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
            new com.cyberday1.neoorigins.network.payload.CreatorResultPayload(ok, message));
    }

    private static void handleRequestOpenCreator(
            com.cyberday1.neoorigins.network.payload.RequestOpenCreatorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                NeoOrigins.LOGGER.warn("Player {} requested the creator without permission",
                    sp.getName().getString());
                sendCreatorResult(sp, false, "You don't have permission to use the origin creator.");
                return;
            }
            openCreatorFor(sp);
        });
    }

    private static void handleSaveCustomOrigin(
            com.cyberday1.neoorigins.network.payload.SaveCustomOriginPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                NeoOrigins.LOGGER.warn("Player {} tried to save a custom origin without permission",
                    sp.getName().getString());
                sendCreatorResult(sp, false, "You don't have permission to use the origin creator.");
                return;
            }
            com.cyberday1.neoorigins.screen.creator.model.OriginDraft draft;
            try {
                draft = com.cyberday1.neoorigins.service.OriginDraftJson.fromJson(payload.draftJson());
            } catch (IllegalArgumentException e) {
                sendCreatorResult(sp, false, "Invalid draft: " + e.getMessage());
                return;
            }
            var validation = com.cyberday1.neoorigins.service.CreatorValidator.validate(draft);
            if (!validation.ok()) {
                NeoOrigins.LOGGER.warn("Player {} submitted an invalid custom origin: {}",
                    sp.getName().getString(), validation.message());
                sendCreatorResult(sp, false, "Invalid: " + validation.message());
                return;
            }
            var result = com.cyberday1.neoorigins.service.CustomPackWriter.write(sp.level().getServer(), draft);
            sendCreatorResult(sp, result.ok(), result.ok()
                ? "Saved " + result.paths().size() + " file(s). Press Apply to reload."
                : "Save failed: " + result.error());
        });
    }

    private static void handleApplyCustomPack(
            com.cyberday1.neoorigins.network.payload.ApplyCustomPackPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                NeoOrigins.LOGGER.warn("Player {} tried to apply the custom pack without permission",
                    sp.getName().getString());
                sendCreatorResult(sp, false, "You don't have permission to use the origin creator.");
                return;
            }
            com.cyberday1.neoorigins.service.CustomPackReloadService.reload(sp.level().getServer())
                .whenComplete((v, err) -> sendCreatorResult(sp, err == null,
                    err == null ? "Datapack reloaded — custom origins are live."
                                 : "Reload failed: " + err.getMessage()));
        });
    }

    // ---------- Server-side handlers ----------

    private static void handleChooseOrigin(ChooseOriginPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            Identifier layerId = payload.layer();
            Identifier originId = payload.origin();

            OriginLayer layer = LayerDataManager.INSTANCE.getLayer(layerId);
            if (layer == null) {
                NeoOrigins.LOGGER.warn("Player {} tried to choose origin for unknown layer {}", sp.getName().getString(), layerId);
                return;
            }

            PlayerOriginData validationData = sp.getData(OriginAttachments.originData());
            if (!layer.getAvailableOriginIds(validationData.getOrigins()).contains(originId)) {
                NeoOrigins.LOGGER.warn("Player {} tried to choose origin {} not in layer {}", sp.getName().getString(), originId, layerId);
                return;
            }

            if (!OriginDataManager.INSTANCE.hasOrigin(originId)) {
                NeoOrigins.LOGGER.warn("Player {} tried to choose non-existent origin {}", sp.getName().getString(), originId);
                return;
            }

            PlayerOriginData data = sp.getData(OriginAttachments.originData());

            // First commit after an orb-of-origin use: perform the deferred
            // destructive work now (revoke prior powers, reset flags, deduct
            // XP, consume one orb from inventory, bump orb-use counter).
            // Deferring to first-pick means closing the picker without picking
            // anything is a free cancel — the player keeps their orb and
            // origins.
            if (data.isPendingOrbCommit()) {
                commitOrbUse(sp, data);
            }

            // Any pick re-engages the player — a previous picker-abandon no
            // longer applies.
            data.setPickerAbandoned(false);

            Identifier oldOrigin = data.getOrigin(layerId);

            // Allow re-selection only via /origin gui (forceReselect).
            // Normal first-time selection always works; re-selection is blocked unless forced.

            OriginChangedEvent event = new OriginChangedEvent(sp, layerId, oldOrigin, originId);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) return;

            data.setOrigin(layerId, event.getNewOrigin());
            ActiveOriginService.applyOriginPowers(sp, layerId, oldOrigin, event.getNewOrigin());
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.CHOSEN, event.getNewOrigin());

            // Cascade invalidation: if the player changed a parent layer,
            // sub-layer choices whose conditions no longer pass must be cleared.
            final java.util.Map<Identifier, Identifier> cascadeChoices = data.getOrigins();
            for (var otherLayer : LayerDataManager.INSTANCE.getSortedLayers()) {
                if (otherLayer.order() <= layer.order()) continue;
                if (!data.hasOriginForLayer(otherLayer.id())) continue;
                Identifier chosenInOther = data.getOrigin(otherLayer.id());
                boolean stillValid = otherLayer.origins().stream()
                    .anyMatch(co -> co.origin().equals(chosenInOther) && co.isAvailable(cascadeChoices));
                if (!stillValid) {
                    ActiveOriginService.applyOriginPowers(sp, otherLayer.id(), chosenInOther, null);
                    data.removeOrigin(otherLayer.id());
                }
            }

            // Mark complete once every layer the picker would actually show
            // has a selection. Condition-aware: skip layers where no origin
            // passes the current choices (conditioned sub-layers for a
            // different parent race, etc.). Must match
            // OriginSelectionPresenter.skipEmptyLayers logic.
            final java.util.Map<Identifier, Identifier> filledChoices = data.getOrigins();
            boolean allFilled = true;
            for (var l : LayerDataManager.INSTANCE.getSortedLayers()) {
                if (l.hidden()) continue;
                boolean hasAnyOrigin = l.origins().stream()
                    .anyMatch(co -> co.isAvailable(filledChoices)
                                 && OriginDataManager.INSTANCE.hasOrigin(co.origin()));
                if (!hasAnyOrigin) continue;
                if (!data.hasOriginForLayer(l.id())) { allFilled = false; break; }
            }
            // First-pick teleport gate: only fire spawn_location teleport on the
            // pick that *first* completes every layer. Re-picks (via /origin gui)
            // and back-button replays must not trigger another teleport — the
            // player asked to change origin, not respawn at the new origin's
            // spawn_location. Capture before setHadAllOrigins flips the flag.
            boolean firstTimeAllFilled = allFilled && !data.isHadAllOrigins();
            if (allFilled) {
                data.setHadAllOrigins(true);
                // Fire any StartingEquipmentPower grants that were deferred during
                // the picker walk-through. The power's onGranted gates on
                // hadAllOrigins to prevent back-button dupes (issue #22).
                com.cyberday1.neoorigins.power.builtin.StartingEquipmentPower.grantAllPending(sp);
            }

            syncToPlayer(sp);

            // Teleport to the origin's spawn_location, if any — but only once
            // the player has finished picking on every layer for the first time.
            // Firing after the first layer's selection would yank them out of
            // the picker mid-flow (before they've chosen a class, etc.); firing
            // on re-picks would relocate the player against their wishes.
            if (firstTimeAllFilled) {
                com.cyberday1.neoorigins.service.OriginSpawnService.teleportToPrimaryOriginSpawn(sp);
            }
        });
    }

    private static void handleActivatePower(ActivatePowerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            int slot = payload.slot();
            if (slot < 0 || slot >= 6) return;

            // Per-slot debounce — prevents key-spam without blocking adjacent slots.
            int currentTick = sp.tickCount;
            String debounceKey = sp.getUUID() + ":" + slot;
            Integer lastTick = LAST_ACTIVATE_TICK.get(debounceKey);
            if (lastTick != null && (currentTick - lastTick) < SLOT_DEBOUNCE_TICKS) return;
            LAST_ACTIVATE_TICK.put(debounceKey, currentTick);

            List<PowerHolder<?>> actives = ActiveOriginService.activePowers(sp);
            if (slot >= actives.size()) return;

            PowerHolder<?> holder = actives.get(slot);
            holder.onActivated(sp);
            syncCooldownIfStarted(sp, holder, slot);
            if (holder.type() instanceof AbstractTogglePower<?>) {
                syncActivePowersToPlayer(sp);
            }
        });
    }

    private static void handleActivateClassPower(ActivateClassPowerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            List<PowerHolder<?>> classActives = ActiveOriginService.activeClassPowers(sp);
            if (classActives.isEmpty()) return;

            // Activate the first (and typically only) class active power
            PowerHolder<?> holder = classActives.get(0);
            holder.onActivated(sp);
            syncCooldownIfStarted(sp, holder, -1);
            if (holder.type() instanceof AbstractTogglePower<?>) {
                syncActivePowersToPlayer(sp);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handleEditorTogglePower(EditorTogglePowerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            Identifier powerId = payload.powerId();

            // The player must actually have this power granted (via their current origins).
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            boolean granted = false;
            for (var entry : data.getOrigins().entrySet()) {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                if (origin != null && origin.powers().contains(powerId)) { granted = true; break; }
            }
            if (!granted) {
                NeoOrigins.LOGGER.warn("Player {} tried to editor-toggle power {} they don't have",
                    sp.getName().getString(), powerId);
                return;
            }

            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null) return;
            if (!(holder.type() instanceof AbstractTogglePower<?>)) return;

            holder.onActivated(sp);
            syncActivePowersToPlayer(sp);
        });
    }

    private static void handleAirJump(AirJumpPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.onGround() || sp.isInWater() || sp.isPassenger() || sp.isSpectator()) return;
            if (sp.isFallFlying()) return;
            if (!FlightPower.isActive(sp)) return;
            sp.startFallFlying();
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void syncCooldownIfStarted(ServerPlayer sp, PowerHolder<?> holder, int slot) {
        String key;
        int totalTicks;

        if (holder.type() instanceof AbstractActivePower) {
            AbstractActivePower ap = (AbstractActivePower) holder.type();
            AbstractActivePower.Config cfg = (AbstractActivePower.Config) holder.config();
            // getCooldownKey's default impl reads PowerHolder.currentDispatchId(), which
            // is only set inside the onActivated dispatch wrapper and has been cleared by
            // now.  Resolve the key the same way onActivated did: use holder.id() for the
            // default impl, but honour subclass overrides that don't rely on dispatch ID.
            String candidateKey = ap.getCooldownKey(cfg);
            key = candidateKey.equals(ap.getClass().getName())
                    ? holder.id().toString()   // default impl fell back — use real ID
                    : candidateKey;            // subclass override (e.g. SummonMinionPower)
            totalTicks = cfg.cooldownTicks();
        } else if (holder.type() instanceof com.cyberday1.neoorigins.compat.CompatPower
                && holder.config() instanceof com.cyberday1.neoorigins.compat.CompatPower.Config cc
                && cc.cooldownTicks() > 0) {
            // Route B compat powers store cooldowns keyed by power ID string.
            // Reverse-lookup the power ID from the data manager.
            key = null;
            for (var e : PowerDataManager.INSTANCE.getPowers().entrySet()) {
                if (e.getValue() == holder) { key = e.getKey().toString(); break; }
            }
            if (key == null) return;
            totalTicks = cc.cooldownTicks();
        } else {
            return;
        }

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        int remaining = data.remainingCooldown(key, sp.tickCount);
        if (remaining > 0) {
            PacketDistributor.sendToPlayer(sp, new SyncCooldownPayload(slot, totalTicks, remaining));
        }
    }

    /** Clean up debounce entries for a player on logout. */
    public static void clearDebounce(java.util.UUID playerUuid) {
        String prefix = playerUuid + ":";
        LAST_ACTIVATE_TICK.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Send the player's full origin state to themselves: chosen origins + the
     * resolved active-powers map (granted powers + toggle state).
     *
     * Callers that only want one or the other can use {@link #syncOriginsOnlyToPlayer}
     * or {@link #syncActivePowersToPlayer} directly.
     */
    public static void syncToPlayer(ServerPlayer player) {
        syncOriginsOnlyToPlayer(player);
        syncActivePowersToPlayer(player);
    }

    /** Origins-map sync only; does not push active-powers. */
    public static void syncOriginsOnlyToPlayer(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(player,
            new SyncOriginsPayload(data.getOrigins(), data.isHadAllOrigins()));
    }

    /**
     * Push the player's current set of granted powers + toggle state + active
     * capability tags to their client. Call after any change that affects the
     * active-powers map: origin change, toggle flip, dimension transition
     * (dimension restrictions filter the map).
     */
    public static void syncActivePowersToPlayer(ServerPlayer player) {
        Map<Identifier, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        collectActivePowers(player, powerMap, capabilities);
        PacketDistributor.sendToPlayer(player, new SyncActivePowersPayload(powerMap, capabilities));
    }

    /**
     * Populates {@code powerMapOut} with {@code powerId → toggleOn} for every power
     * currently granted to the player (dimension restrictions applied) and
     * {@code capabilitiesOut} with the union of capability tags from powers that
     * are currently active (granted AND, if toggleable, toggled on).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void collectActivePowers(ServerPlayer player,
                                            Map<Identifier, Boolean> powerMapOut,
                                            Set<String> capabilitiesOut) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        var dim = player.level().dimension();
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder == null) continue;
                boolean toggledOn = true;
                if (holder.type() instanceof AbstractTogglePower<?>) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    AbstractTogglePower tp = (AbstractTogglePower) holder.type();
                    toggledOn = !tp.isToggledOff(player, holder.config());
                }
                powerMapOut.put(powerId, toggledOn);
                if (toggledOn) {
                    capabilitiesOut.addAll(((PowerHolder) holder).type().capabilities(player, holder.config()));
                }
            }
        }
        // Also include dynamically-granted powers (via /power grant or grant_power actions).
        for (Identifier powerId : data.getDynamicGrantedPowers()) {
            if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null) continue;
            boolean toggledOn = true;
            if (holder.type() instanceof AbstractTogglePower<?>) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                AbstractTogglePower tp = (AbstractTogglePower) holder.type();
                toggledOn = !tp.isToggledOff(player, holder.config());
            }
            powerMapOut.put(powerId, toggledOn);
            if (toggledOn) {
                capabilitiesOut.addAll(((PowerHolder) holder).type().capabilities(player, holder.config()));
            }
        }
    }

    /** Open the origin selection screen on the client. */
    public static void openSelectionScreen(ServerPlayer player, boolean isOrb) {
        openSelectionScreen(player, isOrb, false);
    }

    /** Open the origin selection screen, optionally forcing re-selection of filled layers. */
    public static void openSelectionScreen(ServerPlayer player, boolean isOrb, boolean forceReselect) {
        PacketDistributor.sendToPlayer(player, new OpenOriginScreenPayload(isOrb, forceReselect));
    }

    /** Sync the full origin/layer/power registry to a player so their client can render the GUI. */
    public static void syncRegistryToPlayer(ServerPlayer player) {
        // Build power display entries from all known powers
        java.util.Map<Identifier, com.cyberday1.neoorigins.client.ClientPowerCache.Entry> powerEntries = new java.util.HashMap<>();
        for (var entry : com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getAllPowers().entrySet()) {
            var holder = entry.getValue();
            boolean isToggle = holder.type() instanceof AbstractTogglePower<?>;
            powerEntries.put(entry.getKey(), new com.cyberday1.neoorigins.client.ClientPowerCache.Entry(
                holder.name(), holder.description(), holder.isActive(), isToggle, holder.hidden()));
        }

        // Filter out config-disabled origins from the client sync — they stay
        // registered server-side for /neoorigins set but shouldn't appear in the GUI.
        java.util.Map<Identifier, com.cyberday1.neoorigins.api.origin.Origin> visibleOrigins = new java.util.HashMap<>(OriginDataManager.INSTANCE.getOrigins());
        visibleOrigins.entrySet().removeIf(e -> com.cyberday1.neoorigins.NeoOriginsConfig.isOriginDisabled(e.getKey()));

        PacketDistributor.sendToPlayer(player, new SyncOriginRegistryPayload(
            visibleOrigins,
            LayerDataManager.INSTANCE.getSortedLayers(),
            powerEntries,
            com.cyberday1.neoorigins.compat.OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP,
            com.cyberday1.neoorigins.compat.OriginsMultipleExpander.MULTIPLE_DISPLAY_MAP
        ));
    }

    /**
     * Perform the deferred orb-of-origin commit: revoke all existing origins,
     * clear the equipment-grant ledger, deduct XP, shrink one orb from the
     * player's inventory, and bump the orb-use counter. Called from the first
     * ChooseOrigin after an orb is used — the orb's use() only stages the
     * intent, so closing the picker without picking is a free cancel.
     */
    private static void commitOrbUse(ServerPlayer sp, PlayerOriginData data) {
        int cost = com.cyberday1.neoorigins.content.OrbOfOriginItem.computeCost(data.getOrbUseCount());

        ActiveOriginService.revokeAllPowers(sp);
        for (var layer : LayerDataManager.INSTANCE.getLayers().values()) {
            data.removeOrigin(layer.id());
        }
        data.setHadAllOrigins(false);
        data.clearGrantedEquipment();

        if (!sp.isCreative() && cost > 0) {
            sp.giveExperienceLevels(-cost);
        }
        if (!sp.isCreative()) {
            shrinkOrbFromInventory(sp);
        }
        data.incrementOrbUseCount();
        data.resetEvolution();
        data.setPendingOrbCommit(false);
    }

    private static void shrinkOrbFromInventory(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty()
                && s.getItem() instanceof com.cyberday1.neoorigins.content.OrbOfOriginItem) {
                s.shrink(1);
                return;
            }
        }
    }
}
