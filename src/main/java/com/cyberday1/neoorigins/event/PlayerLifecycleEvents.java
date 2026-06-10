package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.NeoOriginsConfig.RandomMode;
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
        ActiveOriginService.forEach(sp, holder -> {
            holder.onTick(sp);
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

        if (LayerDataManager.INSTANCE.getSortedLayers().isEmpty()) {
            // Data hasn't loaded yet — defer the origin check to tick handler
            pendingOriginCheck.put(sp.getUUID(), LOGIN_RETRY_TICKS);
        } else {
            checkAndPromptOrigin(sp);
        }
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
            if (NeoOriginsConfig.isAutoHuman()) {
                assignAutoHuman(sp);
            } else if (NeoOriginsConfig.getRandomMode() == RandomMode.FIRST_JOIN) {
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
        pendingResync.remove(uuid);
        CompatTickScheduler.clearPlayer(uuid);
        CompatPlayerState.removePlayer(uuid);
        NeoOriginsNetwork.clearDebounce(uuid);
        MinionTracker.clearAll(uuid);
        com.cyberday1.neoorigins.power.builtin.ExtraInventoryPower.onPlayerLogout(sp);
        ActiveOriginService.invalidate(uuid);
        com.cyberday1.neoorigins.service.EventPowerIndex.invalidate(uuid);
        com.cyberday1.neoorigins.service.CombatTracker.forget(uuid);
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
        if (NeoOriginsConfig.getRandomMode() == RandomMode.EVERY_DEATH) {
            ActiveOriginService.revokeAllPowers(sp);
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.clear();
            assignRandomOrigins(sp);
        } else {
            ActiveOriginService.forEach(sp, holder -> holder.onRespawn(sp));
            NeoOriginsNetwork.syncToPlayer(sp);
        }
        // modify_player_spawn — per-power respawn override. Runs before the
        // bed-less fallback and may also override the bed if override_bed=true.
        if (!event.isEndConquered()) {
            final boolean[] teleported = {false};
            ActiveOriginService.forEachOfType(sp, com.cyberday1.neoorigins.power.builtin.ModifyPlayerSpawnPower.class, cfg -> {
                if (teleported[0]) return;
                if (!cfg.overrideBed() && sp.getRespawnPosition() != null) return;
                var target = cfg.location().locateSpawn(sp);
                if (target.isEmpty()) return;
                var pos = target.get().pos();
                if (target.get().level() == sp.level()) {
                    sp.teleportTo(pos.x, pos.y, pos.z);
                } else {
                    sp.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                        target.get().level(), pos, net.minecraft.world.phys.Vec3.ZERO,
                        sp.getYRot(), sp.getXRot(),
                        net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
                }
                teleported[0] = true;
            });
            if (!teleported[0] && sp.getRespawnPosition() == null) {
                com.cyberday1.neoorigins.service.OriginSpawnService.teleportToPrimaryOriginSpawn(sp);
            }
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.RESPAWN);
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

        // Check if any remaining layers (class) still need selection
        boolean needsMore = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) {
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

        // Kill all tracked minions belonging to the dying summoner — they
        // shouldn't outlive their owner. clearAll() discards them cleanly and
        // evicts them from MinionTracker + DIM_HINTS.
        MinionTracker.clearAll(sp.getUUID());
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
