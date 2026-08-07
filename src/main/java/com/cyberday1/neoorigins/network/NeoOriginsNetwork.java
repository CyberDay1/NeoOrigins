package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.OriginChangedEvent;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.network.payload.ActivateClassPowerPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerPayload;
import com.cyberday1.neoorigins.network.payload.AirJumpPayload;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.network.payload.EditorTogglePowerPayload;
import com.cyberday1.neoorigins.network.payload.OpenEditorScreenPayload;
import com.cyberday1.neoorigins.network.payload.OpenOriginScreenPayload;
import com.cyberday1.neoorigins.network.payload.SyncAbilitySlotsPayload;
import com.cyberday1.neoorigins.network.payload.SyncActivePowersPayload;
import com.cyberday1.neoorigins.network.payload.SyncActiveThemePayload;
import com.cyberday1.neoorigins.network.payload.SyncCooldownPayload;
import com.cyberday1.neoorigins.network.payload.SyncEvolutionConfigPayload;
import com.cyberday1.neoorigins.network.payload.SyncEvolutionProgressPayload;
import com.cyberday1.neoorigins.network.payload.SyncMoisturePayload;
import com.cyberday1.neoorigins.network.payload.SyncResourcePayload;
import com.cyberday1.neoorigins.network.payload.SyncResourceValuesPayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginRegistryPayload;
import com.cyberday1.neoorigins.network.payload.SyncMobOriginPayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginsPayload;
import com.cyberday1.neoorigins.network.payload.SyncKeybindRegistryPayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginClaimsPayload;
import com.cyberday1.neoorigins.network.payload.SyncPlayerMorphPayload;
import com.cyberday1.neoorigins.network.payload.SyncPlayerPowersPayload;
import com.cyberday1.neoorigins.network.payload.RemovePlayerPowersPayload;
import com.cyberday1.neoorigins.network.payload.SyncInvisibilityArmorPayload;
import com.cyberday1.neoorigins.network.payload.SyncElytraFlightPayload;
import com.cyberday1.neoorigins.network.payload.SyncFoodNutritionPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerByKeyPayload;
import com.cyberday1.neoorigins.power.builtin.EntityModelPower;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.ConditionPassivePower;
import com.cyberday1.neoorigins.power.builtin.FlightPower;
import com.cyberday1.neoorigins.power.builtin.PersistentEffectPower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
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
    /** The class layer never evolves, so its powers are read tier-flat (mirrors ActiveOriginService). */
    private static final ResourceLocation CLASS_LAYER =
        com.cyberday1.neoorigins.api.PowerLayers.CLASS_LAYER;
    /** Minimum ticks between two activations of the same slot from the same player (anti-spam). */
    private static final int SLOT_DEBOUNCE_TICKS = 5;
    /** Key: "uuid:slot" → server game-time tick that slot was last activated.
     *  Uses level game-time (monotonic, shared, never resets) — NOT
     *  {@code ServerPlayer.tickCount}, which resets to 0 on relog/respawn and
     *  would otherwise make {@code now - last} negative and silently swallow
     *  every activation for the rest of the session. */
    private static final Map<String, Long> LAST_ACTIVATE_TICK = new ConcurrentHashMap<>();

    /** Key: "uuid:action" → last epoch-ms a creator Save/Apply was accepted.
     *  Throttles the expensive creator write/reload payloads (a malicious or
     *  modified client can send them in a tight loop; the legitimate UX is a
     *  human clicking a button). Swept on logout in {@link #clearDebounce}. */
    private static final Map<String, Long> LAST_CREATOR_ACTION = new ConcurrentHashMap<>();
    private static final long SAVE_COOLDOWN_MS  = 1_500L;
    private static final long APPLY_COOLDOWN_MS = 3_000L;
    /** A datapack reload re-syncs every player and stalls the server thread —
     *  never run two concurrently, regardless of how many Apply packets land. */
    private static final java.util.concurrent.atomic.AtomicBoolean RELOAD_IN_FLIGHT =
        new java.util.concurrent.atomic.AtomicBoolean(false);

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
            SyncMobOriginPayload.TYPE,
            SyncMobOriginPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncMobOrigin
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
            SyncResourceValuesPayload.TYPE,
            SyncResourceValuesPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncResourceValues
        );

        registrar.playToClient(
            SyncEvolutionConfigPayload.TYPE,
            SyncEvolutionConfigPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncEvolutionConfig
        );

        registrar.playToClient(
            SyncEvolutionProgressPayload.TYPE,
            SyncEvolutionProgressPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncEvolutionProgress
        );

        registrar.playToClient(
            SyncActivePowersPayload.TYPE,
            SyncActivePowersPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncActivePowers
        );

        registrar.playToClient(
            SyncAbilitySlotsPayload.TYPE,
            SyncAbilitySlotsPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncAbilitySlots
        );

        registrar.playToClient(
            SyncKeybindRegistryPayload.TYPE,
            SyncKeybindRegistryPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncKeybindRegistry
        );

        registrar.playToClient(
            SyncActiveThemePayload.TYPE,
            SyncActiveThemePayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncActiveTheme
        );

        registrar.playToClient(
            SyncOriginClaimsPayload.TYPE,
            SyncOriginClaimsPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncOriginClaims
        );

        registrar.playToClient(
            SyncPlayerMorphPayload.TYPE,
            SyncPlayerMorphPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncPlayerMorph
        );

        registrar.playToClient(
            SyncInvisibilityArmorPayload.TYPE,
            SyncInvisibilityArmorPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncInvisibilityArmor
        );

        registrar.playToClient(
            SyncElytraFlightPayload.TYPE,
            SyncElytraFlightPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncElytraFlight
        );

        registrar.playToClient(
            SyncFoodNutritionPayload.TYPE,
            SyncFoodNutritionPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncFoodNutrition
        );

        registrar.playToClient(
            SyncPlayerPowersPayload.TYPE,
            SyncPlayerPowersPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncPlayerPowers
        );

        registrar.playToClient(
            RemovePlayerPowersPayload.TYPE,
            RemovePlayerPowersPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleRemovePlayerPowers
        );

        registrar.playToServer(
            ActivatePowerByKeyPayload.TYPE,
            ActivatePowerByKeyPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleActivatePowerByKey
        );

        registrar.playToClient(
            OpenEditorScreenPayload.TYPE,
            OpenEditorScreenPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleOpenEditorScreen
        );

        registrar.playToClient(
            com.cyberday1.neoorigins.network.payload.OpenMobCreatorScreenPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.OpenMobCreatorScreenPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleOpenMobCreatorScreen
        );

        registrar.playToClient(
            com.cyberday1.neoorigins.network.payload.CreatorResultPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.CreatorResultPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleCreatorResult
        );

        registrar.playToClient(
            com.cyberday1.neoorigins.network.payload.OriginTemplatesPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.OriginTemplatesPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleOriginTemplates
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
            com.cyberday1.neoorigins.network.payload.VanillaKeyStatePayload.TYPE,
            com.cyberday1.neoorigins.network.payload.VanillaKeyStatePayload.STREAM_CODEC,
            NeoOriginsNetwork::handleVanillaKeyState
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

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.RequestOpenMobCreatorPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.RequestOpenMobCreatorPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleRequestOpenMobCreator
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.SaveMobOriginPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.SaveMobOriginPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSaveMobOrigin
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.ApplyMobPackPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.ApplyMobPackPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleApplyMobPack
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.RequestMobOriginEggPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.RequestMobOriginEggPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleRequestMobOriginEgg
        );

        registrar.playToServer(
            com.cyberday1.neoorigins.network.payload.ToggleNightVisionPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.ToggleNightVisionPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleToggleNightVision
        );

        registrar.playToClient(
            com.cyberday1.neoorigins.network.payload.SyncNightVisionPayload.TYPE,
            com.cyberday1.neoorigins.network.payload.SyncNightVisionPayload.STREAM_CODEC,
            NeoOriginsNetwork::handleSyncNightVision
        );
    }

    /**
     * Flip the player's night-vision master switch. The client sends a bare
     * "flip it" request; every decision is made here so the server stays
     * authoritative.
     *
     * <p>The admin kill-switch wins outright: on a server with
     * {@code disable_night_vision} set, the key reports that night vision is off
     * and leaves the flag alone rather than pretending to enable something
     * {@link com.cyberday1.neoorigins.power.builtin.PersistentEffectPower} will
     * refuse to apply.
     *
     * <p>Ownership is checked next, and for the same reason. The flag is read in
     * exactly one place — {@code PersistentEffectPower.isNightVisionSuppressed} —
     * and only for a {@code minecraft:night_vision} effect spec, so on an origin
     * that owns no such spec the key used to flip a value nothing would ever read
     * while reporting "Night vision on" in green. 49 of the 78 built-in origins
     * are in that position. Say so instead, and leave the flag where it was.
     *
     * <p>Turning OFF actively strips the effect: persistent effects are applied
     * with INFINITE_DURATION, so waiting for it to expire would wait forever.
     * Turning ON needs no counterpart — the power's next tick reapplies it.
     */
    private static void handleToggleNightVision(
            com.cyberday1.neoorigins.network.payload.ToggleNightVisionPayload payload,
            IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (com.cyberday1.neoorigins.config.ContentTogglesConfig.isNightVisionDisabled()) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("neoorigins.night_vision.disabled_by_server")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
                sendNightVisionState(sp);
                return;
            }
            if (!com.cyberday1.neoorigins.power.builtin.PersistentEffectPower.grantsNightVision(
                    com.cyberday1.neoorigins.service.ActiveOriginService.allPowers(sp))) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("neoorigins.night_vision.no_power")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                sendNightVisionState(sp);
                return;
            }
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            boolean nowEnabled = !data.isNightVisionEnabled();
            data.setNightVisionEnabled(nowEnabled);
            if (!nowEnabled) {
                com.cyberday1.neoorigins.power.builtin.PersistentEffectPower.clearOwnedNightVision(sp);
            }
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(nowEnabled
                    ? "neoorigins.night_vision.on"
                    : "neoorigins.night_vision.off")
                .withStyle(nowEnabled ? net.minecraft.ChatFormatting.GREEN
                                      : net.minecraft.ChatFormatting.GRAY), true);
            sendNightVisionState(sp);
        });
    }

    /** Push the authoritative night-vision flag to its owning client. */
    public static void sendNightVisionState(ServerPlayer player) {
        boolean enabled = !com.cyberday1.neoorigins.config.ContentTogglesConfig.isNightVisionDisabled()
            && player.getData(OriginAttachments.originData()).isNightVisionEnabled();
        PacketDistributor.sendToPlayer(player,
            new com.cyberday1.neoorigins.network.payload.SyncNightVisionPayload(enabled));
    }

    private static void handleSyncNightVision(
            com.cyberday1.neoorigins.network.payload.SyncNightVisionPayload payload,
            IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientNightVisionState.set(payload.enabled()));
    }

    private static void handleCancelOrb(com.cyberday1.neoorigins.network.payload.CancelOrbPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.setPendingOrbCommit(false);

            // Scoped layer-picker cancel: the scoped layers were cleared up front,
            // so an uncommitted close must roll each back to its previous origin
            // (and re-grant its powers) rather than leave the player without them.
            // No orb was consumed and no XP was charged (deferred to first commit).
            if (data.isPendingLayerPickerCommit()) {
                for (var entry : data.getPendingLayerPickerPrev().entrySet()) {
                    ResourceLocation layer = entry.getKey();
                    ResourceLocation prev = entry.getValue();
                    if (prev != null && !data.hasOriginForLayer(layer)) {
                        data.setOrigin(layer, prev);
                        ActiveOriginService.applyOriginPowers(sp, layer, null, prev);
                    }
                }
                data.setHadAllOrigins(true);
                data.setPendingLayerPickerCommit(false);
                data.setPendingLayerPickerPrev(new java.util.HashMap<>());
                data.setPendingLayerPickerCost(0);
                data.setPendingLayerPickerConsumeItem(null);
                syncToPlayer(sp);
            }
        });
    }

    private static void handlePickerAbandoned(com.cyberday1.neoorigins.network.payload.PickerAbandonedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.setPickerAbandoned(true);
            // Abandoning the picker also ends any OP-granted re-selection.
            data.setPendingAdminReselect(false);
        });
    }

    // ---------- Client-side handlers ----------

    private static void handleSyncRegistry(SyncOriginRegistryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            OriginDataManager.INSTANCE.setClientData(payload.origins());

            java.util.Map<ResourceLocation, com.cyberday1.neoorigins.api.origin.OriginLayer> layerMap = new java.util.HashMap<>();
            for (var layer : payload.sortedLayers()) layerMap.put(layer.id(), layer);
            LayerDataManager.INSTANCE.setClientData(layerMap, payload.sortedLayers());

            com.cyberday1.neoorigins.client.ClientPowerCache.set(payload.powers());
            com.cyberday1.neoorigins.compat.OriginsMultipleExpander.setClientData(
                payload.multipleExpansionMap(), payload.multipleDisplayMap());
        });
    }

    private static void handleSyncMobOrigin(SyncMobOriginPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientMobOriginCache.set(
                payload.entityId(), payload.originId()));
    }

    /** Tell players tracking {@code mob} which mob origin it now carries. */
    public static void syncMobOriginToTrackers(net.minecraft.world.entity.LivingEntity mob,
                                               java.util.Optional<ResourceLocation> originId) {
        PacketDistributor.sendToPlayersTrackingEntity(mob,
            new SyncMobOriginPayload(mob.getId(), originId));
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
            // Ability-slot roster is re-pushed by syncAbilitySlotsToPlayer right
            // after this payload (syncToPlayer → syncActivePowersToPlayer).
            com.cyberday1.neoorigins.client.ClientAbilitySlots.clear();
            // Same for the diet overrides behind the AppleSkin food preview —
            // syncFoodNutritionToPlayer re-pushes them (empty list included)
            // immediately after this payload, via syncActivePowersToPlayer.
            com.cyberday1.neoorigins.client.ClientFoodNutrition.clear();
            // Do NOT clear HotkeyAssignments here. On login the server sends
            // SyncKeybindRegistryPayload BEFORE this payload (OnDatapackSyncEvent
            // fires before PlayerLoggedInEvent in PlayerList.placeNewPlayer, and
            // onPlayerLogin sends the keybind sync before syncToPlayer), so
            // clearing here wiped the just-applied assignments and left every
            // named-hotkey active power dead until a /reload — and every other
            // SyncOriginsPayload (origin change, respawn, dimension change) wiped
            // them again mid-session (#99). Cross-server staleness is handled by
            // the clear in NeoOriginsClientEvents.onClientPlayerLoggingOut, and
            // each registry payload replaces the assignment set wholesale anyway.
        });
    }

    private static void handleOpenScreen(OpenOriginScreenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.cyberday1.neoorigins.client.ClientOriginState.openSelectionScreen(payload.isOrb(), payload.forceReselect(), payload.scopedLayers());
        });
    }

    private static void handleSyncCooldown(SyncCooldownPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientCooldownState.set(payload.slot(), payload.totalTicks(), payload.remainingTicks(), payload.icon(), payload.countdown())
        );
    }

    private static void handleSyncMoisture(SyncMoisturePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientMoistureState.set(payload.moisture())
        );
    }

    private static void handleSyncOriginClaims(SyncOriginClaimsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientOriginClaims.set(payload.claims())
        );
    }

    private static void handleSyncResource(SyncResourcePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientResourceState.apply(payload.resources())
        );
    }

    private static void handleSyncResourceValues(SyncResourceValuesPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientResourceState.applyValues(payload.values(), payload.maxes())
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

    private static void handleSyncEvolutionProgress(SyncEvolutionProgressPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientEvolutionConfig.updateProgress(
                payload.kills(), payload.tier())
        );
    }

    /**
     * Lightweight live-progress packet sent on every kill. Carries only the
     * mutable (kills, tier) pair -- the static config travels via
     * {@link #syncEvolutionToPlayer(ServerPlayer)} on login and reload.
     */
    public static void syncEvolutionProgressToPlayer(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(sp,
            new SyncEvolutionProgressPayload(data.getEssenceKills(), data.getEvolutionTier()));
    }

    /**
     * Sends the server's evolution config + player's current progress to the client.
     * Called on login and whenever evolution state changes.
     */
    public static void syncEvolutionToPlayer(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(sp, new SyncEvolutionConfigPayload(
            GameplayConfig.isEvolutionEnabled(),
            GameplayConfig.evolutionTier1Kills(),
            GameplayConfig.evolutionTier2Kills(),
            GameplayConfig.evolutionTier3Kills(),
            GameplayConfig.evolutionMessageInterval(),
            data.getEssenceKills(),
            data.getEvolutionTier()
        ));
    }

    private static void handleSyncActivePowers(SyncActivePowersPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientActivePowers.set(payload.powers(), payload.capabilities())
        );
    }

    private static void handleSyncAbilitySlots(SyncAbilitySlotsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientAbilitySlots.set(payload.slots())
        );
    }

    private static void handleSyncFoodNutrition(SyncFoodNutritionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientFoodNutrition.set(payload.overrides())
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
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientOriginState.openEditorScreen()
        );
    }

    private static void handleCreatorResult(
            com.cyberday1.neoorigins.network.payload.CreatorResultPayload payload, IPayloadContext ctx) {
        // Registered playToClient; same dist-safety guard as handleOpenEditorScreen.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientCreatorState.setResult(
                payload.ok(), payload.message())
        );
    }

    private static void handleOriginTemplates(
            com.cyberday1.neoorigins.network.payload.OriginTemplatesPayload payload, IPayloadContext ctx) {
        // Same dedicated-server dist-cleaner guard as handleOpenEditorScreen.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientTemplateCache.setFromJson(payload.json())
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
        // Ship the template bundle BEFORE OpenEditorScreenPayload so the
        // picker's first render has data — payloads inside a single tick are
        // ordered, so the client receives them in this order.
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
            new com.cyberday1.neoorigins.network.payload.OriginTemplatesPayload(
                com.cyberday1.neoorigins.service.OriginTemplates.toJson(
                    com.cyberday1.neoorigins.service.OriginTemplates.collect())));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new OpenEditorScreenPayload());
    }

    private static void sendCreatorResult(ServerPlayer sp, boolean ok, String message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
            new com.cyberday1.neoorigins.network.payload.CreatorResultPayload(ok, message));
    }

    /** True (and replies with a notice) if {@code sp} performed {@code action}
     *  more recently than {@code cooldownMs} ago — caller should abort. */
    private static boolean creatorRateLimited(ServerPlayer sp, String action, long cooldownMs) {
        String key = sp.getUUID() + ":" + action;
        long now = System.currentTimeMillis();
        Long last = LAST_CREATOR_ACTION.get(key);
        if (last != null && now - last < cooldownMs) {
            sendCreatorResult(sp, false, "Slow down — please wait a moment before you "
                + action + " again.");
            return true;
        }
        LAST_CREATOR_ACTION.put(key, now);
        return false;
    }

    /** Throwable message that is never literally {@code "null"}. */
    private static String msgOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getSimpleName();
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
            if (creatorRateLimited(sp, "save", SAVE_COOLDOWN_MS)) return;
            com.cyberday1.neoorigins.screen.creator.model.OriginDraft draft;
            try {
                draft = com.cyberday1.neoorigins.service.OriginDraftJson.fromJson(payload.draftJson());
            } catch (IllegalArgumentException e) {
                sendCreatorResult(sp, false, "Invalid draft: " + e.getMessage());
                return;
            }
            // validate + write are wrapped so any unexpected failure (e.g. a
            // packaging error surfacing as UncheckedIOException from the schema
            // load inside the validator) still sends a result — otherwise the
            // exception escapes enqueueWork and the player's button looks dead.
            try {
                var validation = com.cyberday1.neoorigins.service.CreatorValidator.validate(
                    sp.getServer().registryAccess(), draft);
                if (!validation.ok()) {
                    NeoOrigins.LOGGER.warn("Player {} submitted an invalid custom origin: {}",
                        sp.getName().getString(), validation.message());
                    sendCreatorResult(sp, false, "Invalid: " + validation.message());
                    return;
                }
                var result = com.cyberday1.neoorigins.service.CustomPackWriter.write(sp.getServer(), draft);
                sendCreatorResult(sp, result.ok(), result.ok()
                    ? "Saved " + result.paths().size() + " file(s). Press Apply to reload."
                    : "Save failed: " + result.error());
            } catch (RuntimeException e) {
                NeoOrigins.LOGGER.error("[creator] save failed unexpectedly for {}",
                    sp.getName().getString(), e);
                sendCreatorResult(sp, false, "Save failed: " + msgOf(e));
            }
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
            if (creatorRateLimited(sp, "apply", APPLY_COOLDOWN_MS)) return;
            if (!RELOAD_IN_FLIGHT.compareAndSet(false, true)) {
                sendCreatorResult(sp, false, "A datapack reload is already in progress — please wait.");
                return;
            }
            java.util.concurrent.CompletableFuture<Void> fut;
            try {
                fut = com.cyberday1.neoorigins.service.CustomPackReloadService.reload(sp.getServer());
            } catch (RuntimeException e) {
                RELOAD_IN_FLIGHT.set(false);
                NeoOrigins.LOGGER.error("[creator] reload failed to start", e);
                sendCreatorResult(sp, false, "Reload failed: " + msgOf(e));
                return;
            }
            // Result must go back on the server thread (the future may complete
            // on a reload-worker / common-pool thread).
            fut.whenCompleteAsync((v, err) -> {
                RELOAD_IN_FLIGHT.set(false);
                sendCreatorResult(sp, err == null,
                    err == null ? "Datapack reloaded — custom origins are live."
                                 : "Reload failed: " + msgOf(err));
            }, sp.getServer());
        });
    }

    // ── Mob Origin Creator (parallels the player creator above; reuses the
    //    shared CreatorResultPayload / gate / rate-limit / RELOAD_IN_FLIGHT) ──

    private static void handleOpenMobCreatorScreen(
            com.cyberday1.neoorigins.network.payload.OpenMobCreatorScreenPayload payload,
            IPayloadContext ctx) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientMobCreatorState.openMobCreatorScreen());
    }

    /** Shared open path for the Mob Origin Creator (command + keybind). */
    public static void openMobCreatorFor(ServerPlayer sp) {
        syncRegistryToPlayer(sp);
        syncToPlayer(sp);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
            new com.cyberday1.neoorigins.network.payload.OpenMobCreatorScreenPayload());
    }

    private static void handleRequestOpenMobCreator(
            com.cyberday1.neoorigins.network.payload.RequestOpenMobCreatorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                NeoOrigins.LOGGER.warn("Player {} requested the mob creator without permission",
                    sp.getName().getString());
                sendCreatorResult(sp, false, "You don't have permission to use the mob origin creator.");
                return;
            }
            openMobCreatorFor(sp);
        });
    }

    private static void handleSaveMobOrigin(
            com.cyberday1.neoorigins.network.payload.SaveMobOriginPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                sendCreatorResult(sp, false, "You don't have permission to use the mob origin creator.");
                return;
            }
            if (creatorRateLimited(sp, "save_mob", SAVE_COOLDOWN_MS)) return;
            com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft draft;
            try {
                draft = com.cyberday1.neoorigins.service.MobOriginDraftJson.fromJson(payload.draftJson());
            } catch (IllegalArgumentException e) {
                sendCreatorResult(sp, false, "Invalid draft: " + e.getMessage());
                return;
            }
            try {
                var validation = com.cyberday1.neoorigins.service.MobCreatorValidator.validate(draft);
                if (!validation.ok()) {
                    NeoOrigins.LOGGER.warn("Player {} submitted an invalid mob origin: {}",
                        sp.getName().getString(), validation.message());
                    sendCreatorResult(sp, false, "Invalid: " + validation.message());
                    return;
                }
                var result = com.cyberday1.neoorigins.service.CustomPackWriter.write(sp.getServer(), draft);
                sendCreatorResult(sp, result.ok(), result.ok()
                    ? "Saved " + result.paths().size() + " file(s). Press Apply to reload."
                    : "Save failed: " + result.error());
            } catch (RuntimeException e) {
                NeoOrigins.LOGGER.error("[creator] mob save failed unexpectedly for {}",
                    sp.getName().getString(), e);
                sendCreatorResult(sp, false, "Save failed: " + msgOf(e));
            }
        });
    }

    private static void handleApplyMobPack(
            com.cyberday1.neoorigins.network.payload.ApplyMobPackPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                sendCreatorResult(sp, false, "You don't have permission to use the mob origin creator.");
                return;
            }
            if (creatorRateLimited(sp, "apply_mob", APPLY_COOLDOWN_MS)) return;
            if (!RELOAD_IN_FLIGHT.compareAndSet(false, true)) {
                sendCreatorResult(sp, false, "A datapack reload is already in progress — please wait.");
                return;
            }
            java.util.concurrent.CompletableFuture<Void> fut;
            try {
                fut = com.cyberday1.neoorigins.service.CustomPackReloadService.reload(sp.getServer());
            } catch (RuntimeException e) {
                RELOAD_IN_FLIGHT.set(false);
                NeoOrigins.LOGGER.error("[creator] mob reload failed to start", e);
                sendCreatorResult(sp, false, "Reload failed: " + msgOf(e));
                return;
            }
            fut.whenCompleteAsync((v, err) -> {
                RELOAD_IN_FLIGHT.set(false);
                sendCreatorResult(sp, err == null,
                    err == null ? "Datapack reloaded — custom mob origins are live."
                                 : "Reload failed: " + msgOf(err));
            }, sp.getServer());
        });
    }

    private static void handleRequestMobOriginEgg(
            com.cyberday1.neoorigins.network.payload.RequestMobOriginEggPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!com.cyberday1.neoorigins.service.CreatorAccess.canUse(sp)) {
                sendCreatorResult(sp, false, "You don't have permission to mint mob-origin spawn eggs.");
                return;
            }
            ResourceLocation originId = ResourceLocation.tryParse(payload.originId());
            if (originId == null) {
                sendCreatorResult(sp, false, "Bad origin id."); return;
            }
            ResourceLocation override = payload.entityTypeOverride().isBlank() ? null
                : ResourceLocation.tryParse(payload.entityTypeOverride());
            int count = Math.max(1, Math.min(64, payload.count()));
            var result = com.cyberday1.neoorigins.service.MobOriginSpawnEggService
                .buildEgg(originId, override, count);
            if (!result.ok()) {
                sendCreatorResult(sp, false, result.error());
                return;
            }
            if (!sp.getInventory().add(result.stack())) sp.drop(result.stack(), false);
            sendCreatorResult(sp, true, "Gave " + count + " × " + originId + " spawn egg.");
        });
    }

    // ---------- Server-side handlers ----------

    private static void handleChooseOrigin(ChooseOriginPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            ResourceLocation layerId = payload.layer();
            ResourceLocation originId = payload.origin();

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

            // Unique-origin enforcement. For layers configured as unique
            // (unique_origin_layers), an origin already claimed by another
            // player cannot be taken. An operator in creative mode bypasses
            // the lock (the admin override path). Checked BEFORE any orb is
            // consumed below so a rejected pick never wastes the player's orb.
            boolean uniqueLayer = AdminConfig.isUniqueLayer(layerId);
            boolean uniqueBypass = sp.hasPermissions(2) && sp.isCreative();
            if (uniqueLayer && !uniqueBypass
                    && OriginClaimsData.get(sp.getServer()).isClaimedByOther(layerId, originId, sp.getUUID())) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "That origin has already been claimed by another player on this server.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
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
                // The orb re-picks only the origin layer, so release just that
                // layer's unique-origin claim (if any). Capture before the commit
                // clears it; the class/other layers keep their claims since the orb
                // no longer resets them.
                ResourceLocation orbLayer =
                    com.cyberday1.neoorigins.content.OrbOfOriginItem.ORIGIN_LAYER;
                ResourceLocation preOrbOrigin = data.getOrigin(orbLayer);
                commitOrbUse(sp, data);
                if (preOrbOrigin != null && AdminConfig.isUniqueLayer(orbLayer)) {
                    OriginClaimsData.get(sp.getServer())
                        .releaseIfOwner(orbLayer, preOrbOrigin, sp.getUUID());
                }
            }

            // First commit after a scoped layer-picker was opened (Orb of Class,
            // open_layer_picker action, /origin gui <player> <layer>): the scoped
            // layers were cleared up front, so this fires on the first pick of any
            // of them. Charge the deferred XP cost and (for the Orb of Class)
            // consume one orb. Deferring to first pick means closing the picker is
            // a free cancel (handleCancelOrb restores the prior origins). The
            // reselection anti-cheat gate below tolerates this because the layer
            // was cleared, so oldOrigin is null.
            if (data.isPendingLayerPickerCommit()) {
                commitLayerPicker(sp, data);
            }

            // Any pick re-engages the player — a previous picker-abandon no
            // longer applies. Clearing here restores the first-pick
            // invulnerability window if they reopen + abandon + reopen, which
            // is fine because the player clearly committed this time.
            data.setPickerAbandoned(false);

            ResourceLocation oldOrigin = data.getOrigin(layerId);

            // Server-authoritative re-selection gate. Changing a layer that
            // already holds an origin (once the player has completed initial
            // selection) is only permitted via an Orb of Origin commit (the
            // paid path — handled just above, which clears the layer so
            // oldOrigin is null here), an OP-granted re-selection
            // (/origin gui <player>, sets pendingAdminReselect on the target),
            // or a sender who is themselves OP. First-time selection
            // (oldOrigin == null) and the initial multi-layer walkthrough
            // (hadAllOrigins still false, incl. back-button re-picks) are
            // always allowed. Without this a non-OP player could reset their
            // origin for free via /origin gui or a crafted ChooseOrigin packet.
            boolean isReselection = oldOrigin != null
                && data.isHadAllOrigins()
                && !oldOrigin.equals(originId);
            if (isReselection
                    && !data.isPendingOrbCommit()
                    && !data.isPendingAdminReselect()
                    && !sp.hasPermissions(2)) {
                NeoOrigins.LOGGER.warn(
                    "Player {} attempted unauthorized origin re-selection in layer {} ({} -> {}); rejected",
                    sp.getName().getString(), layerId, oldOrigin, originId);
                return;
            }

            OriginChangedEvent event = new OriginChangedEvent(sp, layerId, oldOrigin, originId);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) return;

            data.setOrigin(layerId, event.getNewOrigin());
            if (uniqueLayer) {
                OriginClaimsData.get(sp.getServer()).claim(layerId, event.getNewOrigin(), sp.getUUID());
            }
            ActiveOriginService.applyOriginPowers(sp, layerId, oldOrigin, event.getNewOrigin());
            // CHOSEN runs the origin's entity_action_chosen callbacks
            // (action_on_callback). During the initial multi-layer walkthrough
            // this is DEFERRED until every layer is picked (fired in the
            // firstTimeAllFilled block below) — a CHOSEN action that relocates
            // the player across dimensions (e.g. a "tp into a pocket dimension"
            // setup) would otherwise tear down the client selection screen via
            // the vanilla respawn screen-swap before the remaining layers (class,
            // etc.) were ever shown, stranding the player with an incomplete
            // pick. This mirrors the spawn_location teleport gate below. Re-picks
            // after completion (admin /origin gui, Orb of Origin — hadAllOrigins
            // already true) still fire immediately.
            if (data.isHadAllOrigins()) {
                com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                    sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.CHOSEN, event.getNewOrigin());
            }
            // KubeJS: originChosen is UI-specific (first pick via the picker).
            // originChanged is fired from inside applyOriginPowers so admin /set,
            // /reset, and cascade invalidation all cover it.
            if (oldOrigin == null) {
                com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireOriginChosen(
                    sp, layerId, event.getNewOrigin());
            }

            // Cascade invalidation: if the player changed a parent layer,
            // sub-layer choices whose conditions no longer pass must be cleared.
            final java.util.Map<ResourceLocation, ResourceLocation> cascadeChoices = data.getOrigins();
            for (var otherLayer : LayerDataManager.INSTANCE.getSortedLayers()) {
                if (otherLayer.order() <= layer.order()) continue;
                if (!data.hasOriginForLayer(otherLayer.id())) continue;
                ResourceLocation chosenInOther = data.getOrigin(otherLayer.id());
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
            final java.util.Map<ResourceLocation, ResourceLocation> filledChoices = data.getOrigins();
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
                // An OP-granted re-selection session ends once every layer is
                // filled again — consume the grant so it can't be reused for
                // a later free re-pick.
                data.setPendingAdminReselect(false);
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

                // Now that every layer is chosen, fire the deferred CHOSEN events
                // for each picked origin in layer order. The per-pick dispatch in
                // the walk-through is suppressed (gated on isHadAllOrigins above)
                // because an origin's entity_action_chosen may tear down the
                // picker mid-flow — e.g. Seer's callback teleports the player to a
                // pocket dimension, which closes the client screen before the
                // class layer can show (the "class skip" bug). Deferring to here
                // lets the player finish every layer first.
                for (var l : LayerDataManager.INSTANCE.getSortedLayers()) {
                    ResourceLocation chosen = data.getOrigin(l.id());
                    if (chosen != null) {
                        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.CHOSEN, chosen);
                    }
                }
            }

            // Grant a brief invulnerability grace once the initial pick completes
            // (initial on-join flow only — orb re-picks never qualify).
            if (firstTimeAllFilled && data.getOrbUseCount() == 0) {
                com.cyberday1.neoorigins.service.FirstPickGraceTracker.grant(sp, 100); // 5s @ 20tps
            }
        });
    }

    private static void handleActivatePower(ActivatePowerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            int slot = payload.slot();
            if (slot < 0 || slot >= 6) return;

            // Per-slot debounce — prevents key-spam without blocking adjacent
            // slots. Keyed on monotonic level game-time so it survives relog.
            long now = sp.level().getGameTime();
            String debounceKey = sp.getUUID() + ":" + slot;
            Long lastTick = LAST_ACTIVATE_TICK.get(debounceKey);
            if (lastTick != null && (now - lastTick) < SLOT_DEBOUNCE_TICKS) return;
            LAST_ACTIVATE_TICK.put(debounceKey, now);

            List<PowerHolder<?>> actives = ActiveOriginService.activePowers(sp);
            if (slot >= actives.size()) return;

            PowerHolder<?> holder = actives.get(slot);
            holder.onActivatedByKeypress(sp);
            syncCooldownIfStarted(sp, holder, slot);
            if (isToggleLike(holder)) {
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
            holder.onActivatedByKeypress(sp);
            syncCooldownIfStarted(sp, holder, -1);
            if (isToggleLike(holder)) {
                syncActivePowersToPlayer(sp);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handleEditorTogglePower(EditorTogglePowerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ResourceLocation powerId = payload.powerId();

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

            // A GUI click rather than a key press, but it is just as deliberate
            // and cannot repeat, so a suppression refusal is worth announcing.
            holder.onActivatedByKeypress(sp);
            syncActivePowersToPlayer(sp);
        });
    }

    private static void handleAirJump(AirJumpPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.onGround() || sp.isInWater() || sp.isPassenger() || sp.isSpectator()) return;
            if (sp.isFallFlying()) return;
            // Record the airborne jump press for compat key.jump-bound active_self
            // powers (e.g. double-jump). Done before the FlightPower gate so it
            // works for players who only have a key.jump compat power, not flight.
            com.cyberday1.neoorigins.compat.CompatPlayerState.recordJumpKey(sp);
            if (!FlightPower.isActive(sp)) return;
            sp.startFallFlying();
        });
    }

    private static void handleVanillaKeyState(
            com.cyberday1.neoorigins.network.payload.VanillaKeyStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            com.cyberday1.neoorigins.compat.CompatPlayerState.setVanillaKeyState(
                sp, payload.useDown(), payload.attackDown());
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void syncCooldownIfStarted(ServerPlayer sp, PowerHolder<?> holder, int slot) {
        String key;
        int totalTicks;
        String icon = "";
        boolean countdown = false;

        if (holder.type() instanceof AbstractActivePower) {
            AbstractActivePower ap = (AbstractActivePower) holder.type();
            AbstractActivePower.Config cfg = (AbstractActivePower.Config) holder.config();
            icon = cfg.cooldownIcon();
            countdown = cfg.cooldownCountdown();
            // getCooldownKey's default impl reads PowerHolder.currentDispatchId(), which
            // is only set inside the onActivated dispatch wrapper and has been cleared by
            // now.  Resolve the key the same way onActivated did: use holder.id() for the
            // default impl, but honour subclass overrides that don't rely on dispatch ID.
            String candidateKey = ap.getCooldownKey(cfg);
            key = candidateKey.equals(ap.getClass().getName())
                    ? holder.id().toString()   // default impl fell back — use real ID
                    : candidateKey;            // subclass override (e.g. SummonMinionPower)
            // Resolve dynamic cooldown (cooldown_resource) so the ring total
            // matches the live counter value, not just the fixed cooldownTicks().
            totalTicks = ap.resolveCooldown(sp, cfg);
        } else if (holder.type() instanceof com.cyberday1.neoorigins.compat.CompatPower
                && holder.config() instanceof com.cyberday1.neoorigins.compat.CompatPower.Config cc
                && cc.cooldownTicks() > 0) {
            // Route B compat powers store cooldowns keyed by power ID string.
            key = holder.id().toString();
            totalTicks = cc.cooldownTicks();
        } else {
            return;
        }

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        int remaining = data.remainingCooldown(key, sp.tickCount);
        if (remaining > 0) {
            PacketDistributor.sendToPlayer(sp, new SyncCooldownPayload(slot, totalTicks, remaining, icon, countdown));
        }
    }

    /** Clean up debounce entries for a player on logout. */
    public static void clearDebounce(java.util.UUID playerUuid) {
        String prefix = playerUuid + ":";
        LAST_ACTIVATE_TICK.keySet().removeIf(key -> key.startsWith(prefix));
        LAST_CREATOR_ACTION.keySet().removeIf(key -> key.startsWith(prefix));
        LAST_KEY_PRESS_TICK.keySet().removeIf(key -> key.startsWith(prefix));
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
        // Night-vision master switch. Rides the same triggers as everything else
        // here (login, respawn, dimension change, origin change) so the client's
        // enhanced_vision gate can never be left holding a stale value.
        sendNightVisionState(player);
    }

    /** Origins-map sync only; does not push active-powers. */
    public static void syncOriginsOnlyToPlayer(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(player,
            new SyncOriginsPayload(data.getOrigins(), data.isHadAllOrigins()));
        // SyncOriginsPayload wipes the client's ClientResourceState (handleSyncOrigins)
        // to drop stale HUD state from a previous session. That clear assumed the
        // server's tick-sync would re-send current values — but a static resource
        // (regen_rate 0, never spent) is never dirty, so its bar would vanish and
        // never return. Re-push resources right after the origins payload; packets
        // on this connection are ordered, so this always lands after the clear.
        com.cyberday1.neoorigins.compat.CompatAttachments.syncResourcesToClient(player);
    }

    /**
     * Push the player's current set of granted powers + toggle state + active
     * capability tags to their client. Call after any change that affects the
     * active-powers map: origin change, toggle flip, dimension transition
     * (dimension restrictions filter the map).
     */
    public static void syncActivePowersToPlayer(ServerPlayer player) {
        Map<ResourceLocation, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        List<PowerHolder<?>> activeHolders = new ArrayList<>();
        collectActivePowers(player, powerMap, capabilities, activeHolders);
        // Remember the phasing-gate signature we just published so the per-tick
        // detector (resyncIfPhaseGateFlipped) only re-sends when it actually
        // changes at runtime — a condition (dimension, in-block, etc.) flipping
        // mid-session would otherwise leave the client predicting phasing while
        // the server has dropped the capability, re-introducing the #109 spasm.
        LAST_PHASE_GATE.put(player.getUUID(), phaseGateSignature(capabilities));
        PacketDistributor.sendToPlayer(player, new SyncActivePowersPayload(powerMap, capabilities));
        // Keep the HUD ability-slot roster in lockstep with the active-powers
        // map — same change triggers, same connection ordering.
        syncAbilitySlotsToPlayer(player);
        // modify_food_nutrition config values, for the AppleSkin food preview.
        // The active-powers map above carries power ids only, not their config,
        // so the client can't work out what a held cod is worth without this.
        syncFoodNutritionToPlayer(player);
        // Morph state (entity_model power) must reach every client that can see
        // this player, not just the player themselves — broadcast it separately.
        broadcastMorphState(player, morphSpecFrom(activeHolders));
        // Same for the invisibility armor-hide flag (neoorigins:invisibility with
        // render_armor:false) — every viewer's armor-layer mixin needs it.
        broadcastInvisibilityArmor(player, hidesArmorFrom(capabilities));
        // Same for the elytra_flight cosmetic wings (render_elytra + optional
        // texture) — every viewer's elytra render layer needs it.
        broadcastElytraFlight(player, capabilities);
        // Per-player, all-viewer state for the Figura soft-dep API: broadcast this
        // player's origins + powers + capabilities to every client that can see
        // them (and themselves), reusing the same powerMap/capabilities already
        // computed above so it stays in lockstep with the local-only sync.
        broadcastPlayerPowers(player, powerMap, capabilities);
    }

    /**
     * Broadcast a player's full NeoOrigins state (origins + powers + capabilities)
     * to every client tracking them AND the player themselves, for the Figura
     * soft-dep API. Reuses the already-collected {@code powerMap}/{@code capabilities}
     * from {@link #syncActivePowersToPlayer}. Reads the chosen-origins map straight
     * from the attachment (not filtered by dimension — origin selection is
     * dimension-agnostic; only the derived powers/caps are dimension-gated).
     */
    private static void broadcastPlayerPowers(ServerPlayer player,
                                              Map<ResourceLocation, Boolean> powerMap,
                                              Set<String> capabilities) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new SyncPlayerPowersPayload(player.getId(), player.getUUID(),
                new HashMap<>(data.getOrigins()), new HashMap<>(powerMap),
                new HashSet<>(capabilities), data.getEvolutionTier()));
    }

    /**
     * Send {@code tracked}'s full NeoOrigins state to a single observer who just
     * started tracking them, so a late-joining / newly-visible viewer's Figura
     * script sees current state instead of nothing. Recomputes the power set for
     * {@code tracked} (tier-aware, dimension-gated) via {@link #collectActivePowers}.
     */
    public static void sendPlayerPowersStateTo(ServerPlayer observer, ServerPlayer tracked) {
        Map<ResourceLocation, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        collectActivePowers(tracked, powerMap, capabilities);
        PlayerOriginData data = tracked.getData(OriginAttachments.originData());
        PacketDistributor.sendToPlayer(observer,
            new SyncPlayerPowersPayload(tracked.getId(), tracked.getUUID(),
                new HashMap<>(data.getOrigins()), powerMap, capabilities,
                data.getEvolutionTier()));
    }

    private static void handleSyncPlayerPowers(SyncPlayerPowersPayload payload, IPayloadContext ctx) {
        // Dist-safety: registered playToClient, but a stray route shouldn't
        // classload the client-only store on a dedicated server.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientPlayerPowers.set(
                payload.playerId(), payload.origins(), payload.powers(),
                payload.capabilities(), payload.evolutionTier()));
    }

    /** Evict a player's Figura-facing state from a single observer's client store. */
    public static void clearPlayerPowersStateFor(ServerPlayer observer, java.util.UUID playerId) {
        PacketDistributor.sendToPlayer(observer, new RemovePlayerPowersPayload(playerId));
    }

    private static void handleRemovePlayerPowers(RemovePlayerPowersPayload payload, IPayloadContext ctx) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientPlayerPowers.remove(payload.playerId()));
    }

    /**
     * Push the player's keybind-slot ability roster (skill slots 0–5 + class
     * active -1) for the cooldown/ability HUD cluster. Slot order mirrors
     * {@link ActiveOriginService#activePowers} — the same list
     * {@link #handleActivatePower} indexes into — so HUD slots, keybinds and
     * activations all agree.
     */
    public static void syncAbilitySlotsToPlayer(ServerPlayer player) {
        List<SyncAbilitySlotsPayload.Entry> entries = new java.util.ArrayList<>();
        List<PowerHolder<?>> actives = ActiveOriginService.activePowers(player);
        for (int i = 0; i < Math.min(actives.size(), 6); i++) {
            entries.add(abilitySlotEntry(i, actives.get(i)));
        }
        List<PowerHolder<?>> classActives = ActiveOriginService.activeClassPowers(player);
        if (!classActives.isEmpty()) {
            entries.add(abilitySlotEntry(-1, classActives.get(0)));
        }
        PacketDistributor.sendToPlayer(player, new SyncAbilitySlotsPayload(entries));
    }

    /**
     * Push the player's {@code modify_food_nutrition} overrides to their client so
     * the AppleSkin tooltip / held-food HUD can preview the value the origin will
     * actually receive instead of the item's vanilla one.
     *
     * <p>Collected through the same {@code ActiveOriginService.forEachOfType} walk
     * the server's eat path uses ({@code InteractionPowerEvents.onItemUseFinish}),
     * in the same order, so the client resolves against exactly the config set —
     * and the same last-match-wins ordering — that will be applied on eat.
     *
     * <p>Sent unconditionally, including when the list is empty: an empty payload
     * is what clears a previous origin's diet from the client after an origin
     * change. It is a couple of bytes and only rides origin/toggle/dimension
     * changes, not the tick loop. It is also sent regardless of whether the
     * receiving client runs AppleSkin — the server can't see the client's mod list,
     * and a client without AppleSkin simply stores the values and never reads them.
     *
     * <p>Known limit: {@code forEachOfType} applies each power's top-level condition
     * gate at the moment it runs, so a diet override conditioned on transient state
     * (in-water, night, …) is snapshotted at sync time, while the eat path
     * re-evaluates it at eat time. A preview taken mid-flip could therefore be a
     * tick or two stale. Every shipped diet override is unconditional today, so this
     * is theoretical; if a conditional one ever ships, re-sync it on the condition
     * flip the way {@code resyncIfPhaseGateFlipped} does for phasing.
     */
    public static void syncFoodNutritionToPlayer(ServerPlayer player) {
        List<SyncFoodNutritionPayload.Entry> entries = new java.util.ArrayList<>();
        ActiveOriginService.forEachOfType(player,
            com.cyberday1.neoorigins.power.builtin.ModifyFoodNutritionPower.class,
            config -> entries.add(SyncFoodNutritionPayload.Entry.of(config)));
        PacketDistributor.sendToPlayer(player, new SyncFoodNutritionPayload(List.copyOf(entries)));
    }

    private static SyncAbilitySlotsPayload.Entry abilitySlotEntry(int slot, PowerHolder<?> holder) {
        String icon = "";
        boolean alwaysShow = false;
        boolean countdown = false;
        if (holder.config() instanceof com.cyberday1.neoorigins.power.builtin.base.HudIconConfig hic) {
            icon = hic.cooldownIcon();
            alwaysShow = hic.alwaysShowIcon();
        }
        if (holder.config() instanceof AbstractActivePower.Config ac) {
            countdown = ac.cooldownCountdown();
        }
        boolean toggleable = isToggleLike(holder);
        return new SyncAbilitySlotsPayload.Entry(slot, holder.id(), icon, toggleable, alwaysShow, countdown);
    }

    /**
     * True for powers whose keybind flips an on/off state the HUD should mirror:
     * {@link AbstractTogglePower} subclasses, plus {@code persistent_effect} and
     * {@code condition_passive} powers authored with {@code "toggleable": true}.
     */
    private static boolean isToggleLike(PowerHolder<?> holder) {
        if (holder.type() instanceof AbstractTogglePower<?>) return true;
        if (holder.type() instanceof PersistentEffectPower
                && holder.config() instanceof PersistentEffectPower.Config pc) {
            return pc.toggleable();
        }
        if (holder.type() instanceof ConditionPassivePower
                && holder.config() instanceof ConditionPassivePower.Config cc) {
            return cc.toggleable();
        }
        return false;
    }

    /** Current on/off state for a toggle-like power (true = currently switched off). */
    private static boolean isToggleLikeOff(ServerPlayer player, PowerHolder<?> holder) {
        if (holder.type() instanceof AbstractTogglePower<?>) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            AbstractTogglePower tp = (AbstractTogglePower) holder.type();
            return tp.isToggledOff(player, holder.config());
        }
        if (holder.type() instanceof PersistentEffectPower pep
                && holder.config() instanceof PersistentEffectPower.Config pc) {
            return pep.isToggledOff(player, pc);
        }
        if (holder.type() instanceof ConditionPassivePower cpp
                && holder.config() instanceof ConditionPassivePower.Config cc) {
            return cpp.isToggledOff(player, cc);
        }
        return false;
    }

    /**
     * Resolve the player's current morph from their active powers, or empty if
     * none of them is an {@code entity_model}. The holder list comes from the
     * same pass that computed the capability set, so morph detection stays in
     * lockstep with the power's actual active state (toggles, conditions,
     * dimension restrictions).
     *
     * <p>Resolution happens here, on the server, so the client receives one
     * concrete {@link MorphSpec} and never has to know about morph definition
     * ids or inline-override precedence. When several {@code entity_model}
     * powers are somehow active at once the first one wins — stacking morphs
     * has no coherent meaning.
     */
    private static java.util.Optional<MorphSpec> morphSpecFrom(List<PowerHolder<?>> activeHolders) {
        for (PowerHolder<?> holder : activeHolders) {
            if (holder.type() instanceof EntityModelPower
                    && holder.config() instanceof EntityModelPower.Config cfg) {
                return java.util.Optional.of(cfg.resolve());
            }
        }
        return java.util.Optional.empty();
    }

    /** Broadcast a player's morph state to all tracking clients and the player. */
    private static void broadcastMorphState(ServerPlayer player,
                                            java.util.Optional<MorphSpec> spec) {
        // The server keeps its own copy as well as sending one. Sounds are asked
        // for on both logical sides, and the server side has no packet to read
        // the answer out of; recording it here means it can never disagree with
        // what the clients were just told.
        com.cyberday1.neoorigins.power.morph.ServerMorphState.set(player.getUUID(), spec.orElse(null));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new SyncPlayerMorphPayload(player.getId(), spec));
    }

    /**
     * Send {@code tracked}'s current morph state to a single observer who just
     * started tracking them (so a late-joining viewer sees an existing morph).
     */
    public static void sendMorphStateTo(ServerPlayer observer, ServerPlayer tracked) {
        Map<ResourceLocation, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        List<PowerHolder<?>> activeHolders = new ArrayList<>();
        collectActivePowers(tracked, powerMap, capabilities, activeHolders);
        java.util.Optional<MorphSpec> spec = morphSpecFrom(activeHolders);
        if (spec.isEmpty()) return;
        PacketDistributor.sendToPlayer(observer,
            new SyncPlayerMorphPayload(tracked.getId(), spec));
    }

    private static void handleSyncPlayerMorph(SyncPlayerMorphPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientMorphState.set(
                payload.entityId(), payload.spec().orElse(null)));
    }

    /**
     * True if the capability set carries the {@code neoorigins:invisibility}
     * armor-hide tag (render_armor:false while active). Reuses the same caps
     * already computed for {@link #syncActivePowersToPlayer} so armor-hide stays
     * in lockstep with the power's actual active state (the top-level condition
     * gate is applied when the caps are collected).
     */
    private static boolean hidesArmorFrom(Set<String> capabilities) {
        return capabilities.contains(
            com.cyberday1.neoorigins.power.builtin.InvisibilityPower.CAP_HIDE_ARMOR);
    }

    /** Broadcast a player's invisibility armor-hide flag to all tracking clients and the player. */
    private static void broadcastInvisibilityArmor(ServerPlayer player, boolean hideArmor) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new SyncInvisibilityArmorPayload(player.getId(), hideArmor));
    }

    /**
     * Send {@code tracked}'s current invisibility armor-hide flag to a single
     * observer who just started tracking them (so a late-joining viewer hides the
     * armor of an already-invisible player). Only sent when the flag is set —
     * absence is the client default.
     */
    public static void sendInvisibilityArmorStateTo(ServerPlayer observer, ServerPlayer tracked) {
        Map<ResourceLocation, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        collectActivePowers(tracked, powerMap, capabilities);
        if (!hidesArmorFrom(capabilities)) return;
        PacketDistributor.sendToPlayer(observer,
            new SyncInvisibilityArmorPayload(tracked.getId(), true));
    }

    private static void handleSyncInvisibilityArmor(SyncInvisibilityArmorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.ClientInvisibilityArmorState.set(
                payload.entityId(), payload.hideArmor()));
    }

    /**
     * True if the capability set carries the {@code neoorigins:elytra_flight}
     * render-elytra tag (render_elytra:true while active). Same lockstep guarantee
     * as {@link #hidesArmorFrom} — the caps were collected with the top-level
     * condition gate applied.
     */
    private static boolean rendersElytraFrom(Set<String> capabilities) {
        return capabilities.contains(
            com.cyberday1.neoorigins.power.builtin.ElytraFlightPower.CAP_RENDER_ELYTRA);
    }

    /**
     * Extract the custom elytra texture id encoded as
     * {@code elytra_texture:<id>} in the capability set, or {@code ""} for the
     * vanilla elytra texture. Only meaningful when {@link #rendersElytraFrom} is true.
     */
    private static String elytraTextureFrom(Set<String> capabilities) {
        String prefix = com.cyberday1.neoorigins.power.builtin.ElytraFlightPower.CAP_TEXTURE_PREFIX;
        for (String cap : capabilities) {
            if (cap.startsWith(prefix)) {
                return cap.substring(prefix.length());
            }
        }
        return "";
    }

    /** Broadcast a player's elytra_flight cosmetic wing state to all tracking clients and the player. */
    private static void broadcastElytraFlight(ServerPlayer player, Set<String> capabilities) {
        boolean render = rendersElytraFrom(capabilities);
        String texture = render ? elytraTextureFrom(capabilities) : "";
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new SyncElytraFlightPayload(player.getId(), render, texture));
    }

    /**
     * Send {@code tracked}'s current elytra_flight wing state to a single observer
     * who just started tracking them (so a late-joining viewer sees an already-active
     * power's wings). Only sent when render is on — absence is the client default.
     */
    public static void sendElytraFlightStateTo(ServerPlayer observer, ServerPlayer tracked) {
        Map<ResourceLocation, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        collectActivePowers(tracked, powerMap, capabilities);
        if (!rendersElytraFrom(capabilities)) return;
        PacketDistributor.sendToPlayer(observer,
            new SyncElytraFlightPayload(tracked.getId(), true, elytraTextureFrom(capabilities)));
    }

    private static void handleSyncElytraFlight(SyncElytraFlightPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ResourceLocation tex = null;
            if (!payload.texture().isBlank()) {
                tex = ResourceLocation.tryParse(payload.texture());
                if (tex == null) {
                    com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                        "[elytra_flight] malformed texture_location '{}' — falling back to vanilla elytra texture",
                        payload.texture());
                }
            }
            com.cyberday1.neoorigins.client.ClientElytraFlightState.set(
                payload.entityId(), payload.render(), tex);
        });
    }

    /**
     * Populates {@code powerMapOut} with {@code powerId → toggleOn} for every power
     * currently granted to the player (dimension restrictions applied) and
     * {@code capabilitiesOut} with the union of capability tags from powers that
     * are currently active (granted AND, if toggleable, toggled on).
     */
    private static void collectActivePowers(ServerPlayer player,
                                            Map<ResourceLocation, Boolean> powerMapOut,
                                            Set<String> capabilitiesOut) {
        collectActivePowers(player, powerMapOut, capabilitiesOut, null);
    }

    /**
     * As above, but also collects the {@link PowerHolder}s behind the active
     * capabilities into {@code activeHoldersOut} when it is non-null.
     *
     * <p>Capability tags are strings, which is enough for on/off signals but
     * loses everything structured — the {@code entity_model} morph needs the
     * whole config (variant NBT, scale, first-person mode), so its sync reads
     * the holder rather than re-parsing a tag.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void collectActivePowers(ServerPlayer player,
                                            Map<ResourceLocation, Boolean> powerMapOut,
                                            Set<String> capabilitiesOut,
                                            List<PowerHolder<?>> activeHoldersOut) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        var dim = player.level().dimension();
        int evolutionTier = data.getEvolutionTier();
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            // Apply evolution tier overlays for non-class layers (classes don't
            // evolve), exactly as ActiveOriginService.getOrBuild does. Without
            // this, a tier-only power's capabilities never reach the client, so
            // client-predicted mixins (natural_glide, wall_phase, wall_climb)
            // silently fail once the power is granted via a tier overlay —
            // e.g. Windwalker's tier-1 "Sky Dancer" glide never activates
            // because the client never learns it has the natural_glide capability
            // and so never sends the START_FALL_FLYING packet.
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            List<ResourceLocation> effectivePowers = isClassLayer
                ? origin.powers()
                : origin.powersForTier(evolutionTier);
            for (ResourceLocation powerId : effectivePowers) {
                if (AdminConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder == null) continue;
                boolean toggledOn = !isToggleLike(holder) || !isToggleLikeOff(player, holder);
                powerMapOut.put(powerId, toggledOn);
                // Only publish capability tags when the power is BOTH toggled on
                // AND its top-level condition is satisfied. onTick/tickEffect is
                // condition-gated (PowerHolder.onTick), so a capability like
                // wall_phase whose condition is unmet would let the client
                // predict phasing (disable collision, set its own position) while
                // the server never sets noPhysics — the server then rejects the
                // client position and the player rubber-bands (issue #109).
                if (toggledOn && holder.isConditionSatisfied(player)) {
                    capabilitiesOut.addAll(((PowerHolder) holder).type().capabilities(player, holder.config()));
                    if (activeHoldersOut != null) activeHoldersOut.add(holder);
                }
            }
        }
        // Also include dynamically-granted powers (via /power grant or grant_power actions).
        for (ResourceLocation powerId : data.getDynamicGrantedPowers()) {
            if (AdminConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null) continue;
            boolean toggledOn = !isToggleLike(holder) || !isToggleLikeOff(player, holder);
            powerMapOut.put(powerId, toggledOn);
            if (toggledOn && holder.isConditionSatisfied(player)) {
                capabilitiesOut.addAll(((PowerHolder) holder).type().capabilities(player, holder.config()));
                if (activeHoldersOut != null) activeHoldersOut.add(holder);
            }
        }
    }

    /**
     * Last phasing-gate signature pushed to each player, keyed by UUID. Used by
     * {@link #resyncIfPhaseGateFlipped} to detect a runtime condition flip that
     * added or dropped a phasing capability without any of the usual sync
     * triggers (origin change / toggle / dimension) firing.
     */
    private static final Map<java.util.UUID, Integer> LAST_PHASE_GATE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A tiny signature of just the phasing-relevant capabilities in a set. Only
     * client-authoritative movement caps ({@code wall_phase}, {@code no_physics},
     * {@code wall_climb}) can cause the #109-style position desync when the
     * server's view flips out from under the client, so those alone drive the
     * per-tick re-sync — cheap enough to check every tick for every player.
     * wall_climb joined for issue #110: conditioned climbing powers flip on
     * block/toggle conditions with no origin/toggle/dimension sync trigger.
     */
    private static int phaseGateSignature(Set<String> capabilities) {
        int sig = 0;
        if (capabilities.contains("wall_phase")) sig |= 1;
        if (capabilities.contains("no_physics")) sig |= 2;
        if (capabilities.contains("wall_climb")) sig |= 4;
        // forced_swimming (Route B origins:swimming) flips on runtime conditions
        // (entering/leaving lava, resource toggles) and drives client-predicted
        // water-physics; a stale client view rubber-bands exactly like #109.
        if (capabilities.contains("forced_swimming")) sig |= 8;
        // cobweb_selection_passthrough flips on sneak state; it only affects
        // crosshair targeting, but sneak flips have no other sync trigger.
        if (capabilities.contains("cobweb_selection_passthrough")) sig |= 16;
        return sig;
    }

    /**
     * Per-tick guard: if the player's phasing-gate signature changed since the
     * last active-powers sync (a top-level power condition flipped mid-session,
     * which no origin/toggle/dimension trigger would have caught), push a fresh
     * active-powers sync so the client stops predicting phasing the instant the
     * server drops the capability (and starts again when it returns). Fixes the
     * runtime half of issue #109's rubber-banding.
     */
    public static void resyncIfPhaseGateFlipped(ServerPlayer player) {
        Map<ResourceLocation, Boolean> powerMap = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        collectActivePowers(player, powerMap, capabilities);
        int sig = phaseGateSignature(capabilities);
        Integer prev = LAST_PHASE_GATE.get(player.getUUID());
        if (prev != null && prev == sig) return;
        // Full sync keeps HUD slots / morph / armor-hide in lockstep and also
        // updates LAST_PHASE_GATE.
        syncActivePowersToPlayer(player);
    }

    /** Drop the cached phasing-gate signature when a player leaves. */
    public static void clearPhaseGate(java.util.UUID uuid) {
        LAST_PHASE_GATE.remove(uuid);
    }

    /** Open the origin selection screen on the client. */
    public static void openSelectionScreen(ServerPlayer player, boolean isOrb) {
        openSelectionScreen(player, isOrb, false);
    }

    /** Open the origin selection screen, optionally forcing re-selection of filled layers. */
    public static void openSelectionScreen(ServerPlayer player, boolean isOrb, boolean forceReselect) {
        openSelectionScreen(player, isOrb, forceReselect, java.util.List.of());
    }

    /**
     * Open the origin selection screen scoped to an allowlist of layers. Empty
     * {@code scopedLayers} = unscoped (the normal picker walk).
     */
    public static void openSelectionScreen(ServerPlayer player, boolean isOrb, boolean forceReselect,
                                           java.util.List<ResourceLocation> scopedLayers) {
        syncClaimsToPlayer(player);
        PacketDistributor.sendToPlayer(player, new OpenOriginScreenPayload(isOrb, forceReselect,
            scopedLayers == null ? java.util.List.of() : scopedLayers));
    }

    /**
     * Send the set of origins locked for {@code player} (claimed by someone else in a
     * unique-enforced layer) so the picker can grey them out. The player's own claims are
     * excluded so they can re-pick their current origin; an OP in creative bypasses the lock
     * entirely and receives an empty map.
     */
    public static void syncClaimsToPlayer(ServerPlayer player) {
        Map<ResourceLocation, Map<ResourceLocation, String>> locked = new java.util.HashMap<>();
        boolean bypass = player.hasPermissions(2) && player.isCreative();
        if (!bypass) {
            OriginClaimsData claimsData = OriginClaimsData.get(player.getServer());
            claimsData.view().forEach((layer, byOrigin) -> {
                if (!AdminConfig.isUniqueLayer(layer)) return;
                byOrigin.forEach((origin, owner) -> {
                    if (owner.equals(player.getUUID())) return;
                    locked.computeIfAbsent(layer, k -> new java.util.HashMap<>())
                          .put(origin, ownerName(player.getServer(), owner));
                });
            });
        }
        PacketDistributor.sendToPlayer(player, new SyncOriginClaimsPayload(locked));
    }

    private static String ownerName(net.minecraft.server.MinecraftServer server, java.util.UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        return server.getProfileCache() != null
            ? server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString())
            : id.toString();
    }

    private static void handleSyncKeybindRegistry(SyncKeybindRegistryPayload payload, IPayloadContext ctx) {
        // Dist-safety: registered playToClient, but routing weirdness shouldn't
        // classload Minecraft on a dedicated-server JVM.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() ->
            com.cyberday1.neoorigins.client.HotkeyAssignments.set(
                payload.declaredKeys(), payload.continuousFlags(), payload.powerToKey())
        );
    }

    /** Per-(uuid:key) edge tracker — debounces continuous payload bursts so
     *  edge-detection powers don't fire every tick when the client also sends
     *  "still held" samples. Cleared on logout via {@link #clearDebounce}. */
    private static final Map<String, Long> LAST_KEY_PRESS_TICK = new ConcurrentHashMap<>();
    /** Minimum game-ticks between two non-continuous fires of the same translation key. */
    private static final int KEY_PRESS_DEBOUNCE_TICKS = 5;

    private static void handleActivatePowerByKey(ActivatePowerByKeyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            String key = payload.translationKey();
            if (key == null || key.isEmpty()) return;
            if (!PowerKeybindRegistry.isDeclared(key)) {
                // A client that's out of sync (e.g. just before SyncKeybindRegistryPayload
                // lands after a /reload) can send a key we've already cleared. Drop silently.
                return;
            }

            // Continuous samples skip the debounce — they're supposed to arrive
            // every tick. Non-continuous (edge) presses use the debounce to
            // collapse double-clicks from key-bounce / packet duplication.
            if (!payload.held() || !PowerKeybindRegistry.isContinuous(key)) {
                long now = sp.level().getGameTime();
                String dKey = sp.getUUID() + ":" + key;
                Long last = LAST_KEY_PRESS_TICK.get(dKey);
                if (last != null && (now - last) < KEY_PRESS_DEBOUNCE_TICKS) return;
                LAST_KEY_PRESS_TICK.put(dKey, now);
            }

            PowerKeybindRegistry.dispatch(sp, key, payload.held());
        });
    }

    private static void handleSyncActiveTheme(SyncActiveThemePayload payload, IPayloadContext ctx) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        ctx.enqueueWork(() -> {
            String raw = payload.themeId();
            ResourceLocation id = (raw == null || raw.isEmpty()) ? null : ResourceLocation.tryParse(raw);
            com.cyberday1.neoorigins.client.theme.ActiveThemeRegistry.setServerDeclared(id);
        });
    }

    /**
     * Push the datapack-declared active UI theme to one player. {@code themeId}
     * may be {@code null} to clear (server's stack has no declaration).
     */
    public static void syncActiveThemeToPlayer(ServerPlayer player) {
        ResourceLocation id = com.cyberday1.neoorigins.data.ActiveThemeManager.INSTANCE.getSelected();
        PacketDistributor.sendToPlayer(player,
            new SyncActiveThemePayload(id == null ? "" : id.toString()));
    }

    /** Send the named-keybind registry snapshot to one player. */
    public static void syncKeybindRegistryToPlayer(ServerPlayer player) {
        List<String> keys = PowerKeybindRegistry.declaredKeys();
        List<Boolean> flags = new java.util.ArrayList<>(keys.size());
        for (String k : keys) flags.add(PowerKeybindRegistry.isContinuous(k));
        PacketDistributor.sendToPlayer(player,
            new SyncKeybindRegistryPayload(keys, flags, PowerKeybindRegistry.powerToKey()));
    }

    /** Sync the full origin/layer/power registry to a player so their client can render the GUI. */
    public static void syncRegistryToPlayer(ServerPlayer player) {
        // Build power display entries from all known powers
        java.util.Map<ResourceLocation, com.cyberday1.neoorigins.client.ClientPowerCache.Entry> powerEntries = new java.util.HashMap<>();
        net.minecraft.core.Registry<com.cyberday1.neoorigins.api.power.PowerType<?>> typeRegistry =
            player.server.registryAccess().registryOrThrow(com.cyberday1.neoorigins.power.registry.PowerTypes.REGISTRY_KEY);
        for (var entry : com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getAllPowers().entrySet()) {
            var holder = entry.getValue();
            boolean isToggle = isToggleLike(holder);
            ResourceLocation typeId = typeRegistry.getKey(holder.type());
            powerEntries.put(entry.getKey(), new com.cyberday1.neoorigins.client.ClientPowerCache.Entry(
                holder.name(), holder.description(), holder.isActive(), isToggle, typeId, holder.hidden()));
        }

        // Filter out config-disabled origins from the client sync — they stay
        // registered server-side for /neoorigins set but shouldn't appear in the GUI.
        java.util.Map<ResourceLocation, com.cyberday1.neoorigins.api.origin.Origin> visibleOrigins = new java.util.HashMap<>(OriginDataManager.INSTANCE.getOrigins());
        visibleOrigins.entrySet().removeIf(e -> ContentTogglesConfig.isOriginDisabled(e.getKey()));

        PacketDistributor.sendToPlayer(player, new SyncOriginRegistryPayload(
            visibleOrigins,
            LayerDataManager.INSTANCE.getSortedLayers(),
            powerEntries,
            com.cyberday1.neoorigins.compat.OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP,
            com.cyberday1.neoorigins.compat.OriginsMultipleExpander.MULTIPLE_DISPLAY_MAP
        ));
    }

    /**
     * Perform the deferred orb-of-origin commit: revoke the ORIGIN layer's powers,
     * clear only that layer, deduct XP, shrink one orb from the player's inventory,
     * and bump the orb-use counter. Called from the first ChooseOrigin after an orb
     * is used — the orb's use() only stages the intent, so closing the picker
     * without picking is a free cancel.
     *
     * <p>Scoped to {@link com.cyberday1.neoorigins.content.OrbOfOriginItem#ORIGIN_LAYER}
     * (issue #113): the Orb of Origin no longer resets the Class (or any other)
     * layer — that is the Orb of Class's job. Class/global powers stay granted; a
     * class whose conditions depend on the origin is cleaned up by the cascade
     * invalidation in {@link #handleChooseOrigin}. The equipment-grant ledger is
     * intentionally NOT cleared: wiping it would let {@code grantAllPending} re-issue
     * the untouched class's starting equipment (a dupe), while the new origin's
     * grant carries a fresh grantId and is issued normally.
     */
    private static void commitOrbUse(ServerPlayer sp, PlayerOriginData data) {
        int cost = com.cyberday1.neoorigins.content.OrbOfOriginItem.computeCost(data.getOrbUseCount());

        ResourceLocation originLayer = com.cyberday1.neoorigins.content.OrbOfOriginItem.ORIGIN_LAYER;
        ResourceLocation prevOrigin = data.getOrigin(originLayer);
        if (prevOrigin != null) {
            ActiveOriginService.applyOriginPowers(sp, originLayer, prevOrigin, null);
        }
        data.removeOrigin(originLayer);
        data.setHadAllOrigins(false);

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

    /**
     * Reusable scoped layer-picker entry point (generalizes the former Orb-of-Class
     * flow). Clears the given {@code layers} (revoking their powers), marks selection
     * incomplete, and reopens the picker scoped to just those layers — so an
     * already-filled layer can be re-shown while every other layer stays chosen.
     *
     * <p>Unknown or hidden layers are skipped with a warning; if none remain valid
     * the method returns without opening a picker.
     *
     * <p>When {@code deferred} is true the destructive commit (XP charge + optional
     * class-orb consume) is deferred to the first successful pick via
     * {@link #commitLayerPicker}, and closing the picker is a free cancel that
     * restores the prior origins ({@link #handleCancelOrb}); {@code isOrb=true} on
     * the open routes ESC through {@link com.cyberday1.neoorigins.network.payload.CancelOrbPayload}.
     * When {@code deferred} is false the {@code cost} is charged immediately and no
     * pending state is set (closing the picker leaves the layers empty — the normal
     * auto-default path handles e.g. class → nitwit).
     *
     * @param consumeItem if non-null, one of this item is removed from the player's
     *                    inventory when the pick commits (the Orb of Class passes
     *                    itself; the {@code open_layer_picker} action passes the held
     *                    item when {@code consume_item} is set). Null = consume nothing.
     * @param message     optional chat line shown to the player when the picker opens
     *                    (translation key or literal); null/blank = silent.
     */
    public static void beginLayerPicker(ServerPlayer sp, java.util.List<ResourceLocation> layers,
                                        boolean deferred, int cost,
                                        net.minecraft.world.item.Item consumeItem, String message) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());

        // Resolve/validate: keep only known, non-hidden layers (preserving order).
        java.util.List<ResourceLocation> valid = new java.util.ArrayList<>();
        for (ResourceLocation id : layers) {
            var layer = LayerDataManager.INSTANCE.getLayer(id);
            if (layer == null) {
                NeoOrigins.LOGGER.warn("[LayerPicker] unknown layer '{}' — skipped", id);
                continue;
            }
            if (layer.hidden()) {
                NeoOrigins.LOGGER.warn("[LayerPicker] layer '{}' is hidden — skipped", id);
                continue;
            }
            valid.add(id);
        }
        if (valid.isEmpty()) return;

        // Clear each scoped layer (revoking its powers) and remember the prior
        // origin of layers that HAD one, so a cancelled pick can roll them back.
        java.util.Map<ResourceLocation, ResourceLocation> prevMap = new java.util.HashMap<>();
        for (ResourceLocation layer : valid) {
            ResourceLocation prev = data.getOrigin(layer);
            if (prev != null) {
                ActiveOriginService.applyOriginPowers(sp, layer, prev, null);
                prevMap.put(layer, prev);
            }
            data.removeOrigin(layer);
        }
        // Selection is no longer complete until the scoped layers are re-picked.
        data.setHadAllOrigins(false);

        if (deferred) {
            data.setPendingLayerPickerCommit(true);
            data.setPendingLayerPickerPrev(prevMap);
            data.setPendingLayerPickerCost(cost);
            data.setPendingLayerPickerConsumeItem(consumeItem);
        } else if (!sp.isCreative() && cost > 0) {
            // Immediate: charge now; no pending state (close = leave layers empty).
            sp.giveExperienceLevels(-cost);
        }

        syncToPlayer(sp);
        // isOrb=deferred so the deferred variant gets ESC=free-cancel routed through
        // CancelOrbPayload; the immediate variant is a plain scoped picker.
        // forceReselect=false — the scoped allowlist drives the walk regardless.
        openSelectionScreen(sp, deferred, false, valid);

        // Optional author-supplied prompt, shown once the picker is open. Translatable
        // so a plain string renders as-is while a translation key resolves.
        if (message != null && !message.isBlank()) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(message));
        }
    }

    /**
     * Commit a deferred scoped layer-picker on the first successful pick: charge the
     * stored XP cost and, when the Orb of Class opened it, consume one orb. Clears
     * all pending layer-picker state so this fires exactly once.
     */
    private static void commitLayerPicker(ServerPlayer sp, PlayerOriginData data) {
        int cost = data.getPendingLayerPickerCost();
        if (!sp.isCreative() && cost > 0) {
            sp.giveExperienceLevels(-cost);
        }
        net.minecraft.world.item.Item consumeItem = data.getPendingLayerPickerConsumeItem();
        if (consumeItem != null && consumeItem != net.minecraft.world.item.Items.AIR && !sp.isCreative()) {
            shrinkItemFromInventory(sp, consumeItem);
        }
        data.setPendingLayerPickerCommit(false);
        data.setPendingLayerPickerPrev(new java.util.HashMap<>());
        data.setPendingLayerPickerCost(0);
        data.setPendingLayerPickerConsumeItem(null);
    }

    /** Remove one of {@code item} from the player's inventory (first match). */
    private static void shrinkItemFromInventory(ServerPlayer sp, net.minecraft.world.item.Item item) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                s.shrink(1);
                return;
            }
        }
    }
}
