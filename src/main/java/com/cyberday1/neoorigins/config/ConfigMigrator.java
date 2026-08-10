package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.NeoOrigins;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

/**
 * One-shot migration from the pre-2.2.2 monolithic config files to the
 * {@code config/neoorigins/} folder layout.
 *
 * <p>Old → new mapping:
 * <ul>
 *   <li>{@code config/neoorigins-common.toml} → split across
 *       {@code neoorigins/gameplay.toml}, {@code neoorigins/admin.toml} and
 *       {@code neoorigins/power_overrides.toml} (key paths are unchanged, so
 *       the split is a straight partition of top-level entries).</li>
 *   <li>{@code config/neoorigins-server.toml} → {@code neoorigins/content.toml}
 *       (same spec layout; both the global {@code config/} default and the
 *       per-world {@code serverconfig/} override locations are migrated).</li>
 *   <li>{@code config/neoorigins-client.toml} → {@code neoorigins/client.toml}.</li>
 *   <li>{@code config/neoorigins-hud.json} → {@code neoorigins/hud.json}.</li>
 * </ul>
 *
 * <p>{@link #migrateBootTime()} runs in the mod constructor BEFORE the specs
 * are registered, so the new files exist (with the user's hand-tuned values —
 * power overrides especially) before NeoForge first loads them. Migration is
 * idempotent: a new file that already exists is never overwritten, and each
 * legacy file is renamed to {@code *.migrated} after processing so the
 * migration never runs twice.
 *
 * <p>Per-world SERVER overrides can't be reached at constructor time (the
 * world path isn't known yet, and NeoForge loads SERVER configs inside
 * {@code ServerLifecycleHooks.handleServerAboutToStart} BEFORE
 * {@code ServerAboutToStartEvent} posts). {@link #onModConfigLoading} therefore
 * hooks the content spec's {@link ModConfigEvent.Loading}: at that moment
 * {@code ServerLifecycleHooks.getCurrentServer()} is already set, so a legacy
 * {@code <world>/serverconfig/neoorigins-server.toml} is copied to
 * {@code serverconfig/neoorigins/content.toml} (picked up on the next boot)
 * AND its values are pushed into the just-loaded in-memory config so they
 * apply this boot too.
 *
 * <p>Boot time is also the only place a WRITTEN value can be healed, which is
 * what {@link #healedDrainRateTicks} is for: once a spec is registered NeoForge
 * has already corrected the file, and it only corrects keys that are missing or
 * out of range, so a stale-but-valid default survives forever. See that method
 * for the {@code config_version} stamp that makes "this file predates the
 * change" a fact rather than a guess.
 */
public final class ConfigMigrator {

    private ConfigMigrator() {}

    /** Top-level sections of the legacy COMMON file that belong to gameplay.toml. */
    private static final Set<String> GAMEPLAY_SECTIONS = Set.of(
        "orb_of_origins", "auto_human", "random_assignment", "evolution",
        "ocean_origins", "sun_damage", "mount", "friendly_fire", "armor_classes");

    private static final String POWER_OVERRIDES_SECTION = "power_overrides";

    /** Runs in the mod constructor, before any spec is registered. */
    public static void migrateBootTime() {
        Path cfgDir = FMLPaths.CONFIGDIR.get();
        Path newDir = cfgDir.resolve("neoorigins");
        try {
            Files.createDirectories(newDir);
        } catch (IOException e) {
            NeoOrigins.LOGGER.error("[config] Failed to create config/neoorigins/ folder", e);
            return;
        }

        migrateCommon(cfgDir.resolve("neoorigins-common.toml"), newDir);
        // Straight copies — the new specs keep the same key paths.
        migrateWholeFile(cfgDir.resolve("neoorigins-server.toml"), newDir.resolve("content.toml"));
        migrateWholeFile(cfgDir.resolve("neoorigins-client.toml"), newDir.resolve("client.toml"));

        // HUD positions JSON — plain rename.
        Path oldHud = cfgDir.resolve("neoorigins-hud.json");
        Path newHud = newDir.resolve("hud.json");
        if (Files.exists(oldHud)) {
            try {
                if (Files.exists(newHud)) {
                    Files.move(oldHud, markMigrated(oldHud), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(oldHud, newHud);
                }
                NeoOrigins.LOGGER.info("[config] Migrated neoorigins-hud.json -> config/neoorigins/hud.json");
            } catch (IOException e) {
                NeoOrigins.LOGGER.error("[config] Failed to migrate neoorigins-hud.json", e);
            }
        }

        // Runs last: migrateCommon may have only just created gameplay.toml out
        // of the legacy monolithic file, stale value and all.
        healGameplayConfig(newDir.resolve("gameplay.toml"));
    }

    // ── One-time value heals (gameplay.toml) ────────────────────────────

    private static final List<String> CONFIG_VERSION_PATH = List.of("config_version");
    private static final List<String> DRAIN_RATE_PATH = List.of("ocean_origins", "drain_rate_ticks");

    /** The {@code drain_rate_ticks} default shipped up to and including v2.2.21. */
    static final int STALE_DRAIN_RATE_TICKS = 10;

    /**
     * The {@code drain_rate_ticks} default from v2.2.22 onwards: one tick per air
     * point, so 300 air = 15 seconds on land, matching vanilla cod and salmon.
     * Kept as a literal rather than read off the spec so the decision logic stays
     * testable without booting {@link GameplayConfig}; it must match the default
     * declared for {@code [ocean_origins] drain_rate_ticks} there.
     */
    static final int CURRENT_DRAIN_RATE_TICKS = 1;

    /**
     * Decides what {@code [ocean_origins] drain_rate_ticks} should become.
     *
     * <p>The default changed from {@value #STALE_DRAIN_RATE_TICKS} to
     * {@value #CURRENT_DRAIN_RATE_TICKS} in v2.2.22, but {@code ModConfigSpec}
     * only ever corrects keys that are missing or out of range, and 10 is inside
     * the declared {@code [1, 1200]} range. So every file written by v2.2.21 or
     * earlier is pinned at 10 forever, and users read that as "15 seconds per air
     * bubble".
     *
     * <p>A 10 in the file cannot be told apart from a 10 the admin deliberately
     * chose by looking at the value, so the age of the FILE is what decides: a
     * file with no {@code config_version} key provably predates the change,
     * because every version that knows about the key writes it. Once the stamp is
     * there the value is the admin's, whatever it is. And only an exact 10 is
     * touched: any other value was necessarily typed by hand.
     *
     * @param configVersionPresent whether the raw TOML already carries a
     *                             {@code config_version} key
     * @param current              the value currently in the file
     * @return the value the file should hold: {@code current} unless this is a
     *         pre-stamp file still sitting on the stale default
     */
    static int healedDrainRateTicks(boolean configVersionPresent, int current) {
        if (configVersionPresent) return current; // already stamped: the value is the admin's
        if (current != STALE_DRAIN_RATE_TICKS) return current; // deliberate: leave it
        return CURRENT_DRAIN_RATE_TICKS;
    }

    /**
     * Applies {@link #healedDrainRateTicks} to the raw {@code gameplay.toml} and
     * stamps it, before any spec is registered. Reads the file as plain TOML on
     * purpose: this has to see what the user's file actually says, ahead of
     * NeoForge's correction pass.
     *
     * <p>Everything here is best-effort. A config file the mod cannot read or
     * rewrite must never stop the mod from loading, so all failures log and
     * return.
     */
    static void healGameplayConfig(Path gameplay) {
        // Fresh install: no file yet, and the spec will create it with the
        // current defaults. Nothing to heal, and nothing to stamp either.
        if (!Files.exists(gameplay)) return;

        try {
            CommentedConfig parsed = parseToml(gameplay);
            if (parsed == null) return;

            // The stamp is the whole mechanism: once it is there, hands off, forever.
            if (parsed.contains(CONFIG_VERSION_PATH)) return;

            Object raw = parsed.get(DRAIN_RATE_PATH);
            if (raw instanceof Number number) {
                int current = number.intValue();
                int healed = healedDrainRateTicks(false, current);
                if (healed != current) {
                    parsed.set(DRAIN_RATE_PATH, healed);
                    NeoOrigins.LOGGER.info(
                        "[config] One-time fix: [ocean_origins] drain_rate_ticks in "
                            + "config/neoorigins/gameplay.toml moved from {} to {}. Your gameplay.toml "
                            + "predates 2.2.22, when {} was the default, and a value already in range is "
                            + "never re-defaulted, so it stayed there: that is 15 seconds per air bubble "
                            + "instead of 1.5. If you chose {} deliberately, set it back and it will be "
                            + "left alone from now on.",
                        current, healed, STALE_DRAIN_RATE_TICKS, STALE_DRAIN_RATE_TICKS);
                }
            }

            // Stamped whether or not anything was healed: that is what stops this
            // from reconsidering the admin's value on the next boot.
            parsed.set(CONFIG_VERSION_PATH, GameplayConfig.CURRENT_CONFIG_VERSION);
            writeInPlace(parsed, gameplay);
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("[config] Failed to check {} for one-time migrations — leaving it untouched",
                gameplay, e);
        }
    }

    /** Splits the legacy COMMON file into gameplay/admin/power_overrides TOMLs. */
    private static void migrateCommon(Path legacy, Path newDir) {
        if (!Files.exists(legacy)) return;
        CommentedConfig parsed = parseToml(legacy);
        if (parsed == null) return;

        CommentedConfig gameplay = TomlFormat.newConfig();
        CommentedConfig admin = TomlFormat.newConfig();
        CommentedConfig powers = TomlFormat.newConfig();

        for (UnmodifiableConfig.Entry entry : parsed.entrySet()) {
            String key = entry.getKey();
            CommentedConfig target;
            if (POWER_OVERRIDES_SECTION.equals(key)) {
                target = powers;
            } else if (GAMEPLAY_SECTIONS.contains(key)) {
                target = gameplay;
            } else {
                target = admin; // debug flags, unique_origin_layers, dimension_restrictions,
                                // compat_filtering, commands, command_powers, entity_exclusions
            }
            target.set(java.util.List.of(key), entry.getValue());
        }

        boolean ok = writeIfAbsent(gameplay, newDir.resolve("gameplay.toml"))
            & writeIfAbsent(admin, newDir.resolve("admin.toml"))
            & writeIfAbsent(powers, newDir.resolve("power_overrides.toml"));
        if (ok) {
            try {
                Files.move(legacy, markMigrated(legacy), StandardCopyOption.REPLACE_EXISTING);
                NeoOrigins.LOGGER.info(
                    "[config] Migrated neoorigins-common.toml into config/neoorigins/{gameplay,admin,power_overrides}.toml");
            } catch (IOException e) {
                NeoOrigins.LOGGER.error("[config] Failed to rename legacy neoorigins-common.toml", e);
            }
        }
    }

    /** Copies a legacy file 1:1 to its new location (same spec layout), then renames it. */
    private static void migrateWholeFile(Path legacy, Path target) {
        if (!Files.exists(legacy)) return;
        try {
            if (!Files.exists(target)) {
                Files.copy(legacy, target);
            }
            Files.move(legacy, markMigrated(legacy), StandardCopyOption.REPLACE_EXISTING);
            NeoOrigins.LOGGER.info("[config] Migrated {} -> {}", legacy.getFileName(), target);
        } catch (IOException e) {
            NeoOrigins.LOGGER.error("[config] Failed to migrate {}", legacy.getFileName(), e);
        }
    }

    /**
     * Per-world SERVER override migration. Fired on the mod event bus while
     * NeoForge is loading the content spec at server start; see class javadoc
     * for why this can't run any earlier.
     */
    public static void onModConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != ContentTogglesConfig.SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Path dir = server.getWorldPath(new LevelResource("serverconfig"));
        Path legacy = dir.resolve("neoorigins-server.toml");
        if (!Files.exists(legacy)) return;
        Path override = dir.resolve("neoorigins").resolve("content.toml");
        try {
            if (!Files.exists(override)) {
                Files.createDirectories(override.getParent());
                Files.copy(legacy, override);
                // This load already resolved to the global config/ file (the
                // override didn't exist when NeoForge resolved paths), so push
                // the world's legacy values into the live config for this boot.
                CommentedConfig parsed = parseToml(legacy);
                if (parsed != null) {
                    event.getConfig().getLoadedConfig().config().putAll(parsed);
                }
            }
            Files.move(legacy, markMigrated(legacy), StandardCopyOption.REPLACE_EXISTING);
            NeoOrigins.LOGGER.info(
                "[config] Migrated world serverconfig neoorigins-server.toml -> serverconfig/neoorigins/content.toml");
        } catch (IOException e) {
            NeoOrigins.LOGGER.error("[config] Failed to migrate world serverconfig neoorigins-server.toml", e);
        }
    }

    private static CommentedConfig parseToml(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            return new TomlParser().parse(reader);
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("[config] Failed to parse legacy config {} — leaving it untouched", path, e);
            return null;
        }
    }

    /** Writes the config to {@code target} unless it already exists. Returns false on I/O failure. */
    private static boolean writeIfAbsent(CommentedConfig config, Path target) {
        if (Files.exists(target)) return true; // already migrated / user-created — never overwrite
        try (Writer writer = Files.newBufferedWriter(target)) {
            new TomlWriter().write(config, writer);
            return true;
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("[config] Failed to write migrated config {}", target, e);
            return false;
        }
    }

    /**
     * Overwrites an existing config file with {@code config}. Separate from
     * {@link #writeIfAbsent} on purpose: that one refuses to clobber a file that
     * exists, which is exactly right for the legacy-file migration and exactly
     * wrong for an in-place edit. Writes to a sibling temp file and moves it into
     * place, so a failure part-way through leaves the user's config intact rather
     * than truncated. Returns false on I/O failure.
     */
    private static boolean writeInPlace(CommentedConfig config, Path target) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                new TomlWriter().write(config, writer);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("[config] Failed to rewrite config {} — leaving it untouched", target, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best effort; a stray .tmp is harmless
            }
            return false;
        }
    }

    private static Path markMigrated(Path legacy) {
        return legacy.resolveSibling(legacy.getFileName() + ".migrated");
    }
}
