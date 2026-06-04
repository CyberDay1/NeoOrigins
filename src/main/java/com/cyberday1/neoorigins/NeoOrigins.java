package com.cyberday1.neoorigins;

import com.cyberday1.neoorigins.content.ModItems;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader;
import com.cyberday1.neoorigins.command.OriginCommand;
import com.cyberday1.neoorigins.compat.OriginsPackFinder;
import com.cyberday1.neoorigins.compat.PackItemAutoRegistrar;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(NeoOrigins.MOD_ID)
public class NeoOrigins {

    public static final String MOD_ID = "neoorigins";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Canonical location for origin-packs as of NeoOrigins 2.0: {@code config/originpacks/}.
     * Legacy installs put the folder at {@code originpacks/} in the game root;
     * {@link #resolveOriginpacksDir()} falls back to the legacy path when the new one
     * is absent so existing setups keep loading without manual intervention.
     */
    public static java.nio.file.Path resolveOriginpacksDir() {
        java.nio.file.Path configDir = FMLPaths.CONFIGDIR.get().resolve("originpacks");
        if (java.nio.file.Files.exists(configDir)) return configDir;
        java.nio.file.Path legacy = FMLPaths.GAMEDIR.get().resolve("originpacks");
        if (java.nio.file.Files.exists(legacy)) {
            if (LEGACY_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("[originpacks] Legacy 'originpacks/' folder found at game root. "
                    + "Please move it to 'config/originpacks/' — the game-root location is deprecated "
                    + "and will be removed in a future release.");
            }
            return legacy;
        }
        return configDir;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean LEGACY_WARNED =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public NeoOrigins(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("NeoOrigins initializing...");

        // Two-spec gameplay config split:
        //  • COMMON (config/neoorigins-common.toml) — server-side tuning/debug
        //    values consumed during the boot-time datapack reload (power
        //    overrides, compat ratio, dimension restrictions, debug flags).
        //    COMMON loads early enough to be read at datapack load; it is not
        //    synced, which is fine because these are baked into the synced
        //    power/origin data, not read by the client directly.
        //  • SERVER (<world>/serverconfig/neoorigins-server.toml) — origin/class
        //    enable toggles and the resource-bar disable. NeoForge auto-syncs
        //    SERVER configs to connecting clients, so disabling an origin
        //    server-side now correctly hides it on remote clients. These values
        //    are only read after a world is active, so the SERVER load-timing
        //    restriction (not loaded during boot-time datapack reload) is moot.
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoOriginsConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, NeoOriginsConfig.SERVER_SPEC);

        // Client TOML config (config/neoorigins-client.toml) — currently just
        // the UI theme override. Registered on physical-client side only so the
        // dedicated server doesn't manage a useless file.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                com.cyberday1.neoorigins.client.NeoOriginsClientConfig.SPEC);
            modEventBus.addListener(
                com.cyberday1.neoorigins.client.NeoOriginsClientConfig::onConfigLoadOrReload);
        }

        // Wire the auto-generated NeoForge config screen into the mod menu's
        // "Config" button. ConfigurationScreen + IConfigScreenFactory are
        // client-only types — load through a client-package trampoline so the
        // dedicated server JVM never resolves them. (Same pattern as
        // feedback_new_clientclass_opcode.)
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            com.cyberday1.neoorigins.client.NeoOriginsConfigScreen.register(modContainer);
        }

        // Create config/originpacks/ folder on first launch. If a legacy
        // originpacks/ folder exists at the game root it will still be picked
        // up by resolveOriginpacksDir() for back-compat.
        try {
            java.nio.file.Files.createDirectories(FMLPaths.CONFIGDIR.get().resolve("originpacks"));
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to create config/originpacks/ folder", e);
        }

        // Register custom power type registry
        PowerTypes.register(modEventBus);

        // Register the compat-verb descriptor registries (action/condition/item).
        // Behavior-neutral foundation for the registry refactor — empty until the
        // parser switches are migrated verb-by-verb in a later step.
        com.cyberday1.neoorigins.compat.registry.CompatRegistries.register(modEventBus);

        // 2.0 — bootstrap legacy power-type aliases so old JSON still loads.
        com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases.bootstrap();

        // Register mod items (Orb of Origin, etc.)
        ModItems.register(modEventBus);

        // Register custom entities (cobweb projectile, etc.)
        com.cyberday1.neoorigins.content.ModEntities.register(modEventBus);

        // Register attachment types (origin data + Route B compat state + entity minion-owner)
        OriginAttachments.register(modEventBus);
        CompatAttachments.register(modEventBus);
        EntityAttachments.register(modEventBus);

        // Register the global-loot-modifier serializer for mob-origin drops.
        com.cyberday1.neoorigins.event.MobOriginLootModifiers.register(modEventBus);

        // Register the origin-gated crafting recipe serializer.
        com.cyberday1.neoorigins.recipe.OriginRecipeRegistry.register(modEventBus);

        // Register network payloads
        modEventBus.addListener(NeoOriginsNetwork::register);

        // Register client-only keybindings and entity renderers
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsKeybindings::onRegisterKeyMappings);
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsClientEvents::onRegisterRenderers);
            // UI theme reload listener — client resources only (NOT server-side).
            modEventBus.addListener(
                (net.neoforged.neoforge.client.event.AddClientReloadListenersEvent ev) ->
                    ev.addListener(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "ui_themes"),
                        com.cyberday1.neoorigins.client.theme.UIThemeManager.INSTANCE));
        }

        // Auto-register items from originpacks/ before the registry freezes
        modEventBus.addListener(PackItemAutoRegistrar::onRegisterItems);

        // Register the originpacks/ folder as a datapack source (mod event bus)
        modEventBus.addListener(NeoOrigins::onAddPackFinders);

        // Register data reload listeners (on NeoForge event bus)
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onServerStarting);

        // Optional mod compat — only loads if the target mod is present
        if (net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
            com.cyberday1.neoorigins.compat.ArsNouveauCompat.register();
        }
        // FTB Quests soft-compat (v2.1.6 backlog #3) — wires the
        // `neoorigins_loot_pool_grant:<table_id>` quest tag to the
        // loot_pool_grant power's grant pipeline. Tag-marker path is the
        // chosen integration; a future Provider-API-based RewardType is
        // stubbed in FtbQuestsCompat#registerRewardType.
        if (net.neoforged.fml.ModList.get().isLoaded("ftbquests")) {
            com.cyberday1.neoorigins.compat.FtbQuestsCompat.register();
        }
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        var folder = resolveOriginpacksDir();
        if (event.getPackType() == PackType.SERVER_DATA || event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new OriginsPackFinder(folder));
            LOGGER.info("Registered originpacks/ for {} at {}", event.getPackType(), folder);
        }
    }

    private static void onAddReloadListeners(net.neoforged.neoforge.event.AddServerReloadListenersEvent event) {
        // Load order matters:
        //   1. power_data         — native Route A powers + compat translation
        //   2. origins_compat_b   — Route B powers injected into PowerDataManager
        //   3. origin_data        — reads MULTIPLE_EXPANSION_MAP (now includes Route B IDs); closes log
        //   4. layer_data
        //   5. mob_origin_data   — NeoOrigins-native; only needs powers (above)
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "power_data"),       PowerDataManager.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "origins_compat_b"), OriginsCompatPowerLoader.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "origin_data"),      OriginDataManager.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "layer_data"),       LayerDataManager.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "mob_origin_data"),  com.cyberday1.neoorigins.data.MobOriginDataManager.INSTANCE);
        // global_powers — Apoli apoli:global port. Grants powers to players/mobs
        // without an origin. Registered AFTER mob_origin_data; only needs powers.
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "global_powers"),    com.cyberday1.neoorigins.data.GlobalPowerSetDataManager.INSTANCE);
        // UI theming — addon packs declare which theme to use via
        // data/<ns>/neoorigins/active_theme.json. Listener resolves the winner;
        // the result is broadcast to clients at login and on datapack sync.
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "active_theme"),     com.cyberday1.neoorigins.data.ActiveThemeManager.INSTANCE);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        OriginCommand.register(event.getDispatcher());
    }

    private static void onServerStarting(ServerStartingEvent event) {
        // 26.1 quirk: the integrated client's initial ResourceManager reload
        // runs at game/world launch time, before item data components are
        // bound onto their Holder.References. Anything in our codecs that
        // builds an ItemStack hits "Components not bound yet"; IconCodec
        // catches it and substitutes ItemStack.EMPTY so origins still parse,
        // but every icon ends up empty until a subsequent reload.
        //
        // By the time this event fires, registry freeze + component bind are
        // done. Re-prepare+apply OriginDataManager and MobOriginDataManager
        // here so icons populate with their real items before any player
        // joins. No-op on dedicated servers (where the first reload already
        // sees bound items), but harmless — runs once per server start.
        var rm = event.getServer().getResourceManager();
        // 26.1: vanilla removed ProfilerFiller.NOP; use the InactiveProfiler
        // singleton instead (it implements ProfileCollector which extends
        // ProfilerFiller).
        var profiler = net.minecraft.util.profiling.InactiveProfiler.INSTANCE;
        try {
            OriginDataManager.INSTANCE.reloadFromResources(rm, profiler);
            com.cyberday1.neoorigins.data.MobOriginDataManager.INSTANCE
                .reloadFromResources(rm, profiler);
        } catch (RuntimeException e) {
            LOGGER.warn("NeoOrigins post-bind origin reload failed — icons may stay empty until /reload",
                e);
        }

        LOGGER.info("NeoOrigins server starting — origins: {}, layers: {}, powers: {}",
            OriginDataManager.INSTANCE.getOrigins().size(),
            LayerDataManager.INSTANCE.getLayers().size(),
            PowerDataManager.INSTANCE.getPowers().size());
        // Safety net: if any mob origin authored on disk has drops but the
        // carrier files are missing (e.g. hand-edited JSON), write them now so
        // the loot modifier activates on the next reload without requiring
        // another Save trip through the creator.
        if (com.cyberday1.neoorigins.service.MobLootModifierGenerator.anyMobOriginHasDrops()) {
            com.cyberday1.neoorigins.service.MobLootModifierGenerator.ensureCarriers(event.getServer());
        }
    }
}
