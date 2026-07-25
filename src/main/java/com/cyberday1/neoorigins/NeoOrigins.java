package com.cyberday1.neoorigins;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.config.PowerOverridesConfig;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.GameplayConfig;
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

        // 2.2.2 config-folder split (config/neoorigins/):
        //  • COMMON gameplay.toml / admin.toml / power_overrides.toml —
        //    server-side tuning/debug values consumed during the boot-time
        //    datapack reload (power overrides, compat ratio, dimension
        //    restrictions, debug flags). COMMON loads early enough to be read
        //    at datapack load; it is not synced, which is fine because these
        //    are baked into the synced power/origin data, not read by the
        //    client directly.
        //  • SERVER neoorigins/content.toml — origin/class enable toggles and
        //    the resource-bar disable. NeoForge auto-syncs SERVER configs to
        //    connecting clients, so disabling an origin server-side correctly
        //    hides it on remote clients. These values are only read after a
        //    world is active, so the SERVER load-timing restriction (not
        //    loaded during boot-time datapack reload) is moot.
        // Legacy monolithic files are migrated BEFORE the specs register so
        // hand-tuned values (power overrides especially) carry over.
        com.cyberday1.neoorigins.config.ConfigMigrator.migrateBootTime();
        modContainer.registerConfig(ModConfig.Type.COMMON,
            com.cyberday1.neoorigins.config.GameplayConfig.SPEC, "neoorigins/gameplay.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON,
            com.cyberday1.neoorigins.config.AdminConfig.SPEC, "neoorigins/admin.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON,
            com.cyberday1.neoorigins.config.PowerOverridesConfig.SPEC, "neoorigins/power_overrides.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER,
            com.cyberday1.neoorigins.config.ContentTogglesConfig.SPEC, "neoorigins/content.toml");
        // Per-world serverconfig override migration — must hook the config
        // Loading event because the world path isn't known at constructor time.
        modEventBus.addListener(com.cyberday1.neoorigins.config.ConfigMigrator::onModConfigLoading);

        // Client TOML config (config/neoorigins/client.toml) — UI theme
        // override + HUD display options. Registered on physical-client side
        // only so the dedicated server doesn't manage a useless file.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                com.cyberday1.neoorigins.client.NeoOriginsClientConfig.SPEC, "neoorigins/client.toml");
            modEventBus.addListener(
                com.cyberday1.neoorigins.client.NeoOriginsClientConfig::onConfigLoadOrReload);
        }

        // Wire the auto-generated NeoForge config screen into the mod menu's
        // "Config" button. ConfigurationScreen + IConfigScreenFactory are
        // client-only types — load through a client-package trampoline so the
        // dedicated server JVM never resolves them. (Same pattern as
        // feedback_new_clientclass_opcode.)
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
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

        // Register mod blocks (temporary cobweb, etc.)
        com.cyberday1.neoorigins.content.ModBlocks.register(modEventBus);

        // Register custom entities (cobweb projectile, etc.)
        com.cyberday1.neoorigins.content.ModEntities.register(modEventBus);

        // Register origin-gated recipe serializer (2.1.6 backlog #2).
        // Hooks BuiltInRegistries.RECIPE_SERIALIZER on both physical sides so the
        // recipe-book sync packet deserializes correctly on clients.
        com.cyberday1.neoorigins.recipe.OriginRecipeRegistry.register(modEventBus);

        // Register attachment types (origin data + Route B compat state + entity minion-owner)
        OriginAttachments.register(modEventBus);
        CompatAttachments.register(modEventBus);
        EntityAttachments.register(modEventBus);

        // Register the global-loot-modifier serializer for mob-origin drops.
        com.cyberday1.neoorigins.event.MobOriginLootModifiers.register(modEventBus);

        // Register network payloads
        modEventBus.addListener(NeoOriginsNetwork::register);

        // Register client-only keybindings and entity renderers
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsKeybindings::onRegisterKeyMappings);
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsClientEvents::onRegisterRenderers);
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsClientEvents::onAddLayers);
            // UI theme + animated resource-bar FX reload listeners — client
            // resources only (NOT server-side). FX presets live in assets/
            // because the whole bar render is client-side; only the preset id
            // is synced from the server.
            modEventBus.addListener(
                (net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent ev) -> {
                    ev.registerReloadListener(com.cyberday1.neoorigins.client.theme.UIThemeManager.INSTANCE);
                    ev.registerReloadListener(com.cyberday1.neoorigins.client.BarFxManager.INSTANCE);
                });
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
        // FTB Ultimine soft-compat — registers a RestrictionHandler so vein-mining
        // is gated to players with an active neoorigins:ultimine power. All
        // FTB-Ultimine-typed code lives in compat.ftbultimine and only classloads
        // behind this gate, so a pack without FTB Ultimine loads normally.
        if (net.neoforged.fml.ModList.get().isLoaded("ftbultimine")) {
            com.cyberday1.neoorigins.compat.ftbultimine.FtbUltimineCompat.register();
        }

        // The One Probe soft-compat — registers an entity provider that shows a
        // looked-at mob's NeoOrigins origin in the probe overlay. TOP uses the
        // IMC handshake, so wire the enqueue listener here; the actual send is
        // guarded by a ModList check (and all TOP-typed code is confined to the
        // compat.top package) so a missing TOP never NoClassDefFounds.
        modEventBus.addListener(NeoOrigins::onInterModEnqueue);

        // Common setup — soft-compat registrations that must run after every
        // mod constructor (e.g. FTBQ's RewardTypes.init()).
        modEventBus.addListener(NeoOrigins::onCommonSetup);
    }

    private static void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // FTB Quests reward type — registered after FTBQ's constructor has run
        // RewardTypes.init(). Gated so a pack without FTBQ loads normally; the
        // FTBQ-typed reward classes only classload inside registerRewardType().
        if (net.neoforged.fml.ModList.get().isLoaded("ftbquests")) {
            event.enqueueWork(com.cyberday1.neoorigins.compat.FtbQuestsCompat::registerRewardType);
        }
    }

    private static void onInterModEnqueue(net.neoforged.fml.event.lifecycle.InterModEnqueueEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("theoneprobe")) {
            com.cyberday1.neoorigins.compat.top.TopIntegration.enqueueImc();
        }
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        var folder = resolveOriginpacksDir();
        if (event.getPackType() == PackType.SERVER_DATA || event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new OriginsPackFinder(folder));
            LOGGER.info("Registered originpacks/ for {} at {}", event.getPackType(), folder);
        }
    }

    private static void onAddReloadListeners(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        // Load order matters:
        //   1. power_data         — native Route A powers + compat translation
        //   2. origins_compat_b   — Route B powers injected into PowerDataManager
        //   3. origin_data        — reads MULTIPLE_EXPANSION_MAP (now includes Route B IDs); closes log
        //   4. layer_data
        //   5. mob_origin_data   — NeoOrigins-native; only needs powers (above)
        event.addListener(PowerDataManager.INSTANCE);
        event.addListener(OriginsCompatPowerLoader.INSTANCE);
        event.addListener(OriginDataManager.INSTANCE);
        event.addListener(LayerDataManager.INSTANCE);
        event.addListener(com.cyberday1.neoorigins.data.MobOriginDataManager.INSTANCE);
        // global_powers — Apoli apoli:global port. Grants powers to players/mobs
        // without an origin. Registered AFTER mob_origin_data; only needs powers.
        event.addListener(com.cyberday1.neoorigins.data.GlobalPowerSetDataManager.INSTANCE);
        // entity_groups — data-driven pseudo entity-group defs behind the
        // entity_group power. Built-in defaults live in code; a datapack file of
        // the same id overrides. Independent of the power loaders (queried at
        // event time), so ordering relative to them is immaterial.
        event.addListener(com.cyberday1.neoorigins.data.EntityGroupDataManager.INSTANCE);
        // UI theming — addon packs declare which theme to use via
        // data/<ns>/neoorigins/active_theme.json. Listener resolves the winner;
        // the result is broadcast to clients at login and on datapack sync.
        event.addListener(com.cyberday1.neoorigins.data.ActiveThemeManager.INSTANCE);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        OriginCommand.register(event.getDispatcher());
    }

    private static void onServerStarting(ServerStartingEvent event) {
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
