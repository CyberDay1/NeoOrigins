package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.config.GameplayConfig.RandomMode;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatPlayerState;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.origin.OriginUpgrade;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.MinionTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class PlayerLifecycleEvents {

    /** Grace period (in ticks) after login to retry the origin check if data wasn't loaded yet. */
    private static final int LOGIN_RETRY_TICKS = 100;
    private static final java.util.Map<java.util.UUID, Integer> pendingOriginCheck = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Players whose origin powers could not be derived at login because the
     * datapack-driven managers weren't loaded yet (UUID → ticks remaining).
     * Drained in the tick handler: once the managers can resolve the player's
     * persisted origin, we re-apply the full power set and re-sync. This is the
     * self-healing fallback for the relog/login data-load race.
     */
    private static final java.util.Map<java.util.UUID, Integer> pendingPowerReapply = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        repairCorruptedVitals(sp);

        // Retry origin check for players who logged in before data was loaded
        Integer remaining = pendingOriginCheck.get(sp.getUUID());
        if (remaining != null) {
            if (LayerDataManager.INSTANCE.getSortedLayers().isEmpty()) {
                if (remaining <= 0) {
                    pendingOriginCheck.remove(sp.getUUID());
                } else {
                    pendingOriginCheck.put(sp.getUUID(), remaining - 1);
                }
            } else {
                pendingOriginCheck.remove(sp.getUUID());
                checkAndPromptOrigin(sp);
            }
        }

        // Drain deferred power re-apply: the player logged in with a persisted
        // origin before the datapack managers were ready, so the power set
        // derived empty. Once the managers can resolve the origin, re-apply the
        // full set server-authoritatively and re-sync; otherwise count down and
        // give up after the grace window (managers genuinely have no such origin).
        Integer reapplyRemaining = pendingPowerReapply.get(sp.getUUID());
        if (reapplyRemaining != null) {
            if (!powerDataNotReady(sp)) {
                pendingPowerReapply.remove(sp.getUUID());
                ActiveOriginService.invalidate(sp.getUUID());
                com.cyberday1.neoorigins.service.EventPowerIndex.invalidate(sp.getUUID());
                applyLoginPowers(sp);
            } else if (reapplyRemaining <= 0) {
                pendingPowerReapply.remove(sp.getUUID());
            } else {
                pendingPowerReapply.put(sp.getUUID(), reapplyRemaining - 1);
            }
        }

        // Drain deferred re-sync after respawn
        Integer resyncRemaining = pendingResync.get(sp.getUUID());
        if (resyncRemaining != null) {
            if (resyncRemaining <= 0) {
                pendingResync.remove(sp.getUUID());
                NeoOriginsNetwork.syncToPlayer(sp);
            } else {
                pendingResync.put(sp.getUUID(), resyncRemaining - 1);
            }
        }

        CompatTickScheduler.tick(sp);
        MinionTracker.tick(sp);
        // KubeJS power_tick: opt-in via hasListeners() — skip the per-power
        // closure allocation entirely when nothing's listening to the
        // high-frequency event.
        boolean fireKubePowerTick =
            com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.powerTickHasListeners();
        // Native active powers bound to a vanilla input key (e.g. key.jump
        // double-jump) are polled here from the server-side input state. Cheap
        // global guard skips the per-power lookup when no pack declares one.
        boolean pollVanillaNative =
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.hasVanillaNative();
        ActiveOriginService.forEach(sp, holder -> {
            holder.onTick(sp);
            if (pollVanillaNative) {
                com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.pollVanillaNative(sp, holder);
            }
            if (fireKubePowerTick) {
                com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.firePowerTick(sp, holder.id());
            }
        });
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.TICK);
        // CLIMB fires once per tick while the player is on a climbable block
        // (ladder/vine/scaffolding). No NeoForge event exists for this, so it
        // rides the player tick — pack authors gate frequency with cooldown or
        // conditions. Context is null (the player is the subject).
        if (sp.onClimbable()) {
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.CLIMB);
        }
    }

    /** Last-seen onGround state per player, for the creative-safe LAND detector below. */
    private static final java.util.Map<java.util.UUID, Boolean> lastOnGround =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Creative-safe {@link com.cyberday1.neoorigins.service.EventPowerIndex.Event#LAND}
     * source. {@code LivingFallEvent} only fires when the player accrues fall
     * distance, which never happens in creative flight or when descending slowly,
     * so {@code action_on_event} powers keyed on {@code land} silently did nothing
     * outside survival. This watches {@code onGround} for a false→true rising edge
     * and dispatches LAND itself.
     *
     * <p>Runs on {@code PlayerTickEvent.Post} so its {@code tickCount} matches the
     * one {@code LivingFallEvent} observes; in survival LivingFallEvent fires first
     * and stamps the tick via {@link com.cyberday1.neoorigins.service.EventPowerIndex#markLandDispatched},
     * so this detector's same-tick dedup check skips it and LAND fires exactly once.
     * Context is null (the player is the subject); the cancellable fall-damage path
     * stays exclusive to the LivingFallEvent source.
     */
    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var uuid = sp.getUUID();

        // Re-sync active powers if a phasing capability was added/dropped this
        // tick by a top-level power condition flipping (dimension, in-block,
        // etc.) — no origin/toggle/dimension trigger catches a runtime condition
        // change, and a stale client belief that phasing is active while the
        // server disagrees produces the #109 rubber-band. Cheap: only re-sends
        // when the wall_phase/no_physics signature actually changes.
        NeoOriginsNetwork.resyncIfPhaseGateFlipped(sp);

        boolean onGround = sp.onGround();
        Boolean prev = lastOnGround.put(uuid, onGround);
        if (onGround && (prev == null || !prev)) {
            if (!com.cyberday1.neoorigins.service.EventPowerIndex.landDispatchedThisTick(uuid, sp.tickCount)) {
                com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                    sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.LAND);
                com.cyberday1.neoorigins.service.EventPowerIndex.markLandDispatched(uuid, sp.tickCount);
            }
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.ADVANCEMENT_EARNED,
            new com.cyberday1.neoorigins.service.EventPowerIndex.AdvancementContext(
                event.getAdvancement().id()));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        repairCorruptedVitals(sp);

        // Drop any cache entry left under this UUID by a previous session. The
        // per-player power cache is keyed by UUID and its version tuple
        // (PlayerOriginData.version() is transient and resets to 0 on every
        // deserialize), so a quick relog could otherwise resolve against a stale
        // entry built during the prior session's disconnect window — leaving the
        // origin intact but the resolved power set empty/partial. The CacheEntry
        // identity guard already prevents the cross-session hit; this is the
        // belt-and-suspenders so the very first read this login rebuilds clean.
        ActiveOriginService.invalidate(sp.getUUID());
        com.cyberday1.neoorigins.service.EventPowerIndex.invalidate(sp.getUUID());

        // Server-authoritative (re)application of the full power set derived from
        // the persisted origin selection. This is the self-healing path: it runs
        // every join and is idempotent (onGranted/onLogin re-apply attribute
        // modifiers and re-register event handlers from a clean slate above), so
        // a player who lost powers to any prior race recovers them on next login.
        applyLoginPowers(sp);

        if (LayerDataManager.INSTANCE.getSortedLayers().isEmpty()) {
            // Data hasn't loaded yet — defer the origin check to tick handler
            pendingOriginCheck.put(sp.getUUID(), LOGIN_RETRY_TICKS);
        } else {
            checkAndPromptOrigin(sp);
        }

        // If the datapack-driven origin/power managers weren't fully loaded when
        // we derived the power set above, the resolved set could be empty even
        // though the player has a persisted origin. Schedule a re-apply+re-sync
        // that fires once the managers report content, so powers self-heal
        // without a manual relog. No-op when everything was already loaded.
        if (powerDataNotReady(sp)) {
            pendingPowerReapply.put(sp.getUUID(), LOGIN_RETRY_TICKS);
        }
    }

    /**
     * Re-derives and re-applies the player's full power set from their persisted
     * origin, server-authoritatively, then pushes the authoritative state to the
     * client. Safe to call repeatedly — power lifecycle re-application is
     * idempotent and the client mirror is fully replaced by the sync.
     */
    private static void applyLoginPowers(ServerPlayer sp) {
        // Global power sets (apoli:global): grant/reconcile before onLogin so the
        // freshly-granted powers receive their onLogin dispatch in the same pass.
        com.cyberday1.neoorigins.service.GlobalPowerService.reconcilePlayer(sp);

        ActiveOriginService.forEach(sp, holder -> holder.onLogin(sp));

        // Clamp health to the (possibly changed) max — catches stale health
        // from modifier loss, evolution tier changes while offline, or attribute
        // reloads that reduced max_health below current health.
        if (sp.getHealth() > sp.getMaxHealth()) {
            sp.setHealth(sp.getMaxHealth());
        }

        NeoOriginsNetwork.syncRegistryToPlayer(sp);
        NeoOriginsNetwork.syncKeybindRegistryToPlayer(sp);
        NeoOriginsNetwork.syncToPlayer(sp);
        NeoOriginsNetwork.syncEvolutionToPlayer(sp);
        NeoOriginsNetwork.syncActiveThemeToPlayer(sp);
    }

    /**
     * True when the player has a persisted origin but the datapack-driven managers
     * can't yet resolve its powers (managers still empty at login). In that state
     * the derived power set would be empty, so we must defer a re-apply until the
     * datapack finishes loading rather than locking in an empty grant.
     */
    private static boolean powerDataNotReady(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        if (data.getOrigins().isEmpty()) return false; // nothing to re-derive
        for (var entry : data.getOrigins().entrySet()) {
            if (OriginDataManager.INSTANCE.getOrigin(entry.getValue()) != null) {
                return false; // at least one origin resolves — managers are up
            }
        }
        return true; // has stored origins but none resolve yet → managers not ready
    }

    private static void checkAndPromptOrigin(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());

        // If the player has any stored origins, they are a returning player — don't
        // force a full re-selection. This covers both the hadAllOrigins flag and legacy
        // players from before the flag was set during manual GUI selection.
        if (data.isHadAllOrigins() || !data.getOrigins().isEmpty()) {
            // Backfill the flag for legacy saves so future checks are fast
            if (!data.isHadAllOrigins() && !data.getOrigins().isEmpty()) {
                data.setHadAllOrigins(true);
            }
            return;
        }

        boolean needsOrigin = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) {
                NeoOrigins.LOGGER.debug("Player {} needs origin for layer {} (stored: {})",
                    sp.getName().getString(), layer.id(), data.getOrigins().keySet());
                needsOrigin = true;
                break;
            }
        }
        if (needsOrigin) {
            if (GameplayConfig.isSkipInitialSelection()) {
                // Skip the picker entirely: leave the player origin-less. Mark
                // selection "complete" so the first-pick invulnerability guard
                // releases and this check doesn't re-prompt on every relog. They
                // stay origin-less until granted one later (e.g. Orb of Origin).
                data.setHadAllOrigins(true);
                NeoOrigins.LOGGER.info("Skipped initial origin selection for {} (skip_initial_selection)",
                    sp.getName().getString());
            } else if (GameplayConfig.isAutoHuman()) {
                assignAutoHuman(sp);
            } else if (GameplayConfig.getRandomMode() == RandomMode.FIRST_JOIN) {
                assignRandomOrigins(sp);
            } else {
                NeoOriginsNetwork.openSelectionScreen(sp, false);
            }
        }
    }

    private static void repairCorruptedVitals(ServerPlayer sp) {
        if (!Float.isFinite(sp.getHealth())) {
            NeoOrigins.LOGGER.warn(
                "Repairing corrupted Health on {} ({}): was {}, resetting to max",
                sp.getName().getString(), sp.getUUID(), sp.getHealth());
            sp.setHealth(sp.getMaxHealth());
        }
        if (!Float.isFinite(sp.getAbsorptionAmount())) {
            NeoOrigins.LOGGER.warn(
                "Repairing corrupted AbsorptionAmount on {} ({}): was {}, resetting to 0",
                sp.getName().getString(), sp.getUUID(), sp.getAbsorptionAmount());
            sp.setAbsorptionAmount(0.0f);
        }
    }

    /**
     * Re-push the config-filtered origin registry after a datapack reload
     * (and on login). {@code /reload} re-runs {@code OriginDataManager.apply()},
     * which repopulates the shared singleton with the *unfiltered* origin set
     * (config-disabled origins are intentionally kept so {@code /neoorigins set}
     * can still reference them). On an integrated server the selection screen
     * shares that singleton, so without a re-sync the disabled origins reappear
     * in the picker after {@code /reload}. NeoForge fires this for the single
     * joining player on login, or for every player on {@code /reload} —
     * {@link OnDatapackSyncEvent#getRelevantPlayers()} resolves the right set.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(sp -> {
            // Re-apply global power sets so a /reload that added or removed an
            // apoli:global set immediately grants/revokes for online players.
            com.cyberday1.neoorigins.service.GlobalPowerService.reconcilePlayer(sp);
            NeoOriginsNetwork.syncRegistryToPlayer(sp);
            NeoOriginsNetwork.syncKeybindRegistryToPlayer(sp);
            NeoOriginsNetwork.syncActiveThemeToPlayer(sp);
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var uuid = sp.getUUID();
        pendingOriginCheck.remove(uuid);
        pendingPowerReapply.remove(uuid);
        pendingResync.remove(uuid);
        lastOnGround.remove(uuid);
        NeoOriginsNetwork.clearPhaseGate(uuid);
        com.cyberday1.neoorigins.power.morph.ServerMorphState.remove(uuid);
        MorphHitboxEvents.forget(uuid);
        CompatTickScheduler.clearPlayer(uuid);
        CompatPlayerState.removePlayer(uuid);
        NeoOriginsNetwork.clearDebounce(uuid);
        // Summoned minions are session-scoped and die with the logout, but tamed
        // pets get vanilla-pet persistence: they stay in the world (tame state
        // rides the persistent minion_owner attachment; their AI goals resolve
        // the owner lazily by UUID) and their tracker entries are kept so
        // max_tamed caps and death backlash stay correct across the relog.
        MinionTracker.clearAllExceptType(uuid,
            com.cyberday1.neoorigins.power.builtin.TameMobPower.tamedMobKey());
        com.cyberday1.neoorigins.power.builtin.ExtraInventoryPower.onPlayerLogout(sp);
        ActiveOriginService.invalidate(uuid);
        com.cyberday1.neoorigins.service.EventPowerIndex.invalidate(uuid);
        com.cyberday1.neoorigins.service.CombatTracker.forget(uuid);
        com.cyberday1.neoorigins.service.FirstPickGraceTracker.clear(uuid);
        // Abort any spawn_location biome search still running for this player —
        // there is nobody left to teleport, and the worker would otherwise burn
        // its full budget for nothing.
        com.cyberday1.neoorigins.service.AsyncSpawnLocator.cancel(uuid);
        com.cyberday1.neoorigins.power.builtin.ModelColorPower.clearPlayer(uuid);
        com.cyberday1.neoorigins.power.builtin.ResourcePower.clearPlayer(uuid);
        com.cyberday1.neoorigins.power.builtin.ShadowOrbPower.clearPlayer(uuid);
        com.cyberday1.neoorigins.power.builtin.StealthPower.clearPlayer(uuid);
        com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader.clearAmplifierModifiers(uuid);
        com.cyberday1.neoorigins.service.JumpActionRegistry.clearPlayer(uuid);
    }

    /**
     * Dimension restrictions filter the active-powers map, so any dimension
     * transition invalidates the client's mirror. Push a fresh sync.
     */
    /**
     * When an observer starts tracking another player, push that player's
     * current morph state (entity_model power) so a late-joining viewer sees
     * an already-active morph instead of the vanilla body. {@code getEntity()}
     * is the observer; {@code getTarget()} is the entity now being tracked.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) return;
        if (!(event.getTarget() instanceof ServerPlayer tracked)) return;
        NeoOriginsNetwork.sendMorphStateTo(observer, tracked);
        NeoOriginsNetwork.sendInvisibilityArmorStateTo(observer, tracked);
        NeoOriginsNetwork.sendElytraFlightStateTo(observer, tracked);
        // Per-player state for the Figura soft-dep API — always sent (not gated on
        // a specific power being active) so an observer's Figura script can read
        // the origin/powers of any newly-visible player.
        NeoOriginsNetwork.sendPlayerPowersStateTo(observer, tracked);
    }

    /**
     * When an observer stops tracking a player (they moved out of range / were
     * removed), evict that player's Figura-facing state from the observer's client
     * store so it can't go stale or leak. The observer re-receives current state
     * on the next StartTracking. Only the observer's own client is told — this is
     * a client-side eviction, so it rides a normal server→that-observer packet.
     */
    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) return;
        if (!(event.getTarget() instanceof ServerPlayer tracked)) return;
        NeoOriginsNetwork.clearPlayerPowersStateFor(observer, tracked.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        NeoOriginsNetwork.syncActivePowersToPlayer(sp);
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.DIMENSION_CHANGE);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Clear per-slot activation debounce — the new ServerPlayer's tickCount
        // resets to 0 on respawn, so any stored "last activate tick" from before
        // death would block all skill activations until the new tickCount caught
        // up (potentially tens of minutes).
        NeoOriginsNetwork.clearDebounce(sp.getUUID());
        if (GameplayConfig.getRandomMode() == RandomMode.EVERY_DEATH) {
            ActiveOriginService.revokeAllPowers(sp);
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.clear();
            assignRandomOrigins(sp);
        } else {
            ActiveOriginService.forEach(sp, holder -> holder.onRespawn(sp));
            NeoOriginsNetwork.syncToPlayer(sp);
            // Vanilla creates the respawned ServerPlayer with default (20) max_health
            // and fills it, THEN onRespawn re-applies the origin's max_health modifier
            // — which raises the max but never the current health. Without this the
            // player would respawn at 20/30 instead of full. Respawn is already a
            // full-heal moment in vanilla, so healing to the boosted max here matches
            // that and fills the bonus hearts.
            sp.setHealth(sp.getMaxHealth());
        }
        // modify_player_spawn — per-power respawn override. Runs before the
        // bed-less fallback and may also override the bed if override_bed=true.
        // First power that resolves a location wins; if none do, the origin's
        // own spawn_location is the fallback. Locating a biome-driven spawn is
        // far too expensive to do on the server thread, so the chain runs
        // asynchronously and applies the teleport on a later tick — see
        // OriginSpawnService.applyRespawnSpawnOverrides for the ordering
        // guarantee.
        if (!event.isEndConquered()) {
            com.cyberday1.neoorigins.service.OriginSpawnService.applyRespawnSpawnOverrides(sp);
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.RESPAWN);

        // Re-equip trinkets kept via keep_inventory. The new ServerPlayer's
        // Curios/Accessories handlers are ready by respawn; re-equip into the
        // original slot when it's empty, otherwise fall back to the inventory so
        // the item is never lost.
        var trinkets = KEPT_TRINKETS.remove(sp.getUUID());
        if (trinkets != null) {
            for (var t : trinkets) {
                boolean placed = com.cyberday1.neoorigins.compat.condition.AccessoryInspector.restoreSlot(
                    sp, t.source(), t.slotId(), t.index(), t.stack());
                if (!placed && !sp.getInventory().add(t.stack())) sp.drop(t.stack(), false);
            }
        }

        // Recall surviving tamed pets to the respawned tamer (vanilla-pet
        // semantics). Runs AFTER the spawn-override teleports above so the pets
        // arrive at the tamer's final respawn position, and rebinds their AI
        // goals — the follow/defend goals captured the OLD (now-dead)
        // ServerPlayer instance at tame time and would idle forever otherwise.
        // Gated on !isEndConquered: exiting the End also fires this event, but
        // the player didn't die there, so don't yank pets across the world.
        if (!event.isEndConquered()) {
            com.cyberday1.neoorigins.power.builtin.TameMobPower.recallTamedOnRespawn(sp);
        }

        // Deferred re-sync: the client may not be ready for packets at respawn time,
        // causing the HUD/info to show stale state until relog.
        pendingResync.put(sp.getUUID(), 2);
    }

    /** Players awaiting a deferred origin re-sync after respawn (UUID → ticks remaining). */
    private static final java.util.Map<java.util.UUID, Integer> pendingResync = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Applies datapack-defined origin upgrades when a player earns an advancement.
     * Each origin can declare {@code upgrades: [{advancement, origin, announcement}]}
     * in its JSON; when the referenced advancement fires and the player is currently
     * that origin on some layer, we swap them to the target origin on the same layer.
     *
     * <p>Runs every origin-layer the player currently has — so the same advancement
     * can drive different swaps on different layers (e.g. an origin evolution on the
     * origin layer and an unrelated class promotion on the class layer).
     */
    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ResourceLocation earnedId = event.getAdvancement().id();

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        // Snapshot before iteration — applyOriginPowers mutates the data map.
        var snapshot = new java.util.ArrayList<>(data.getOrigins().entrySet());
        for (var entry : snapshot) {
            ResourceLocation layerId = entry.getKey();
            ResourceLocation currentOriginId = entry.getValue();
            Origin origin = OriginDataManager.INSTANCE.getOrigin(currentOriginId);
            if (origin == null) continue;

            for (OriginUpgrade upgrade : origin.upgrades()) {
                if (!earnedId.equals(upgrade.advancement())) continue;
                if (!OriginDataManager.INSTANCE.hasOrigin(upgrade.origin())) {
                    NeoOrigins.LOGGER.warn(
                        "Origin upgrade from {} references unknown target origin {} — skipping",
                        currentOriginId, upgrade.origin());
                    continue;
                }

                data.setOrigin(layerId, upgrade.origin());
                ActiveOriginService.applyOriginPowers(sp, layerId, currentOriginId, upgrade.origin());
                NeoOriginsNetwork.syncToPlayer(sp);

                String announcement = upgrade.announcement();
                if (announcement != null && !announcement.isEmpty()) {
                    sp.sendSystemMessage(Component.translatable(announcement));
                }

                NeoOrigins.LOGGER.info(
                    "Upgraded {}'s origin on layer {}: {} → {} (via advancement {})",
                    sp.getName().getString(), layerId, currentOriginId, upgrade.origin(), earnedId);
                // One upgrade per layer per advancement — if authors want chains,
                // they should use distinct advancements.
                break;
            }
        }
    }

    private static void assignAutoHuman(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        ResourceLocation originLayer = ResourceLocation.parse("neoorigins:origin");
        ResourceLocation humanOrigin = ResourceLocation.parse("neoorigins:human");

        if (!data.hasOriginForLayer(originLayer) && OriginDataManager.INSTANCE.hasOrigin(humanOrigin)) {
            data.setOrigin(originLayer, humanOrigin);
            ActiveOriginService.applyOriginPowers(sp, originLayer, null, humanOrigin);
            NeoOriginsNetwork.syncToPlayer(sp);
            NeoOrigins.LOGGER.info("Auto-assigned human origin to {}", sp.getName().getString());
        }

        // Check if any remaining layers (class) still need selection. Only count a
        // layer that actually has a selectable origin — a layer whose entire option
        // list is config-disabled (e.g. every Class toggled off) can never be filled,
        // so treating it as pending would open a screen the client silently skips and
        // leave the player stuck in the first-pick invulnerability state forever
        // (issue #113). Mirrors the unfillable-layer handling in assignRandomOrigins.
        boolean needsMore = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (data.hasOriginForLayer(layer.id())) continue;
            if (layerHasSelectableOrigin(data, layer)) {
                needsMore = true;
                break;
            }
        }
        if (needsMore) {
            NeoOriginsNetwork.openSelectionScreen(sp, false);
        } else {
            data.setHadAllOrigins(true);
        }
    }

    /**
     * True when {@code layer} offers at least one origin the player could actually
     * pick: available under the layer's conditions, present in the datapack, and not
     * disabled via {@link ContentTogglesConfig}. A layer with no selectable origin is
     * unfillable and must not keep the initial-pick flow (and its invulnerability
     * grace) open.
     */
    private static boolean layerHasSelectableOrigin(PlayerOriginData data, OriginLayer layer) {
        for (ResourceLocation id : layer.getAvailableOriginIds(data.getOrigins())) {
            if (!OriginDataManager.INSTANCE.hasOrigin(id)) continue;
            if (ContentTogglesConfig.isOriginDisabled(id)) continue;
            return true;
        }
        return false;
    }

    private static void assignRandomOrigins(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        List<String> assigned = new ArrayList<>();

        for (OriginLayer layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            ResourceLocation layerId = layer.id();
            if (data.hasOriginForLayer(layerId)) continue;

            List<ResourceLocation> available = layer.getAvailableOriginIds().stream()
                .filter(OriginDataManager.INSTANCE::hasOrigin)
                .toList();
            if (available.isEmpty()) continue;

            ResourceLocation picked = available.get(sp.getRandom().nextInt(available.size()));
            data.setOrigin(layerId, picked);
            ActiveOriginService.applyOriginPowers(sp, layerId, null, picked);
            assigned.add(picked.toString());
        }

        data.setHadAllOrigins(true);
        NeoOriginsNetwork.syncToPlayer(sp);
        NeoOrigins.LOGGER.info("Randomly assigned origins to {}: {}",
            sp.getName().getString(), String.join(", ", assigned));
    }

    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.WAKE_UP);
    }

    /** Per-player stash for items kept across death via KeepInventoryPower. */
    private static final java.util.Map<java.util.UUID, java.util.List<net.minecraft.world.item.ItemStack>> KEPT_STASH
        = new java.util.concurrent.ConcurrentHashMap<>();

    /** One Curios/Accessories stack kept across death, with the slot to re-equip into. */
    private record KeptTrinket(
        com.cyberday1.neoorigins.compat.condition.AccessoryInspector.Source source,
        String slotId, int index, net.minecraft.world.item.ItemStack stack) {}

    /** Per-player stash for trinket (Curios/Accessories) items kept across death. */
    private static final java.util.Map<java.util.UUID, java.util.List<KeptTrinket>> KEPT_TRINKETS
        = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var inv = sp.getInventory();
        var kept = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        int total = inv.getContainerSize();
        for (int i = 0; i < total; i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            final int slotIdx = i;
            final var stackRef = stack;
            final boolean[] match = {false};
            ActiveOriginService.forEachOfType(sp, com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.class, cfg -> {
                var cat = com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.SlotCategory.forInventoryIndex(slotIdx);
                if (!com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.matchesSlot(cfg, cat)) return;
                if (!com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.matchesItem(cfg, stackRef)) return;
                match[0] = true;
            });
            if (match[0]) {
                kept.add(stack.copy());
                inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        if (!kept.isEmpty()) KEPT_STASH.put(sp.getUUID(), kept);

        // Trinket slots (Curios / Accessories) — keep_inventory must cover them
        // too. Capture matching equipped trinkets, copy them into the stash, and
        // empty the live slot so the mod doesn't drop it; restored on respawn.
        var keptTrinkets = new java.util.ArrayList<KeptTrinket>();
        for (var entry : com.cyberday1.neoorigins.compat.condition.AccessoryInspector.getEquippedEntries(sp)) {
            final boolean[] match = {false};
            ActiveOriginService.forEachOfType(sp, com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.class, cfg -> {
                if (!com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.matchesAccessorySlot(cfg, entry.slotId())) return;
                if (!com.cyberday1.neoorigins.power.builtin.KeepInventoryPower.matchesItem(cfg, entry.stack())) return;
                match[0] = true;
            });
            if (match[0]) {
                keptTrinkets.add(new KeptTrinket(entry.source(), entry.slotId(), entry.index(), entry.stack().copy()));
                com.cyberday1.neoorigins.compat.condition.AccessoryInspector.clearSlot(
                    sp, entry.source(), entry.slotId(), entry.index());
            }
        }
        if (!keptTrinkets.isEmpty()) KEPT_TRINKETS.put(sp.getUUID(), keptTrinkets);

        // Kill tracked SUMMONED minions belonging to the dying summoner — they
        // shouldn't outlive their owner. Tamed pets (tame_mob / tame_target) are
        // exempt: vanilla-pet semantics, so they survive the tamer's death and
        // are recalled to the tamer on respawn (Discord report — pets used to be
        // discarded here alongside the summons). See onPlayerRespawn.
        MinionTracker.clearAllExceptType(sp.getUUID(),
            com.cyberday1.neoorigins.power.builtin.TameMobPower.tamedMobKey());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var stash = KEPT_STASH.remove(sp.getUUID());
        if (stash == null) return;
        for (var stack : stash) {
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        }
    }
}
