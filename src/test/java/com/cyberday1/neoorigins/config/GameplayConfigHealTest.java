package com.cyberday1.neoorigins.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-time {@code [ocean_origins] drain_rate_ticks} heal in
 * {@link ConfigMigrator#healedDrainRateTicks}.
 *
 * <p>Three separate reports of "it takes 15 seconds per air bubble" all came
 * from the same cause: the default moved from 10 to 1 in 2.2.22, but
 * {@code ModConfigSpec} only rewrites keys that are missing or out of range, and
 * 10 sits inside the declared {@code [1, 1200]} range. So every install that had
 * ever run 2.2.21 kept 10 forever, and nothing in the codebase would ever have
 * healed it.
 *
 * <p>What makes this safe to automate is that the heal does NOT reason from the
 * value. A 10 is a perfectly legitimate setting meaning 2.5 minutes of land
 * time. It reasons from the file: no {@code config_version} key means the file
 * was written before the key existed, which is evidence and not a heuristic.
 * {@link #aStampedFileIsNeverTouched()} is the test that actually protects the
 * admin who chose 10, and {@link #onlyTheExactStaleDefaultIsHealed()} is the one
 * that protects every hand-typed value.
 */
class GameplayConfigHealTest {

    private static final boolean PRE_STAMP = false;
    private static final boolean STAMPED = true;

    // ── The reported bug ────────────────────────────────────────────────

    @Test
    void anUnstampedStaleDefaultIsHealed() {
        // The exact shape of every affected install: written by <=2.2.21, so no
        // config_version, and still sitting on the old default.
        assertEquals(1, ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 10),
            "a pre-config_version file still on the old default of 10 is the bug, and must be healed to 1");
    }

    // ── Values that must be left alone ──────────────────────────────────

    @Test
    void onlyTheExactStaleDefaultIsHealed() {
        // Anything that isn't exactly 10 was necessarily typed by hand, so it is
        // deliberate no matter how old the file is.
        assertEquals(40, ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 40),
            "40 was never a default, so it can only have been chosen: it must survive the heal");
        assertEquals(9, ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 9));
        assertEquals(11, ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 11));
        assertEquals(1200, ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 1200));
    }

    @Test
    void anUnstampedFileAlreadyOnTheCurrentValueIsUnchanged() {
        // Old file, but the admin (or a 2.2.22+ write that predates the stamp)
        // already has the right value. The heal must be a no-op, not a rewrite.
        assertEquals(1, ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 1),
            "a file already on 1 must come out of the heal unchanged");
    }

    @Test
    void aStampedFileIsNeverTouched() {
        // THE case that stops this becoming an every-boot value stomp: once the
        // stamp is written, 10 means the admin wants 2.5 minutes of land time.
        assertEquals(10, ConfigMigrator.healedDrainRateTicks(STAMPED, 10),
            "after the one-time heal has stamped the file, a 10 is a deliberate choice and must stand");
        assertEquals(1, ConfigMigrator.healedDrainRateTicks(STAMPED, 1));
        assertEquals(40, ConfigMigrator.healedDrainRateTicks(STAMPED, 40));
    }

    /**
     * Running the heal twice must be identical to running it once. The stamp is
     * what guarantees that, so drive the second pass as stamped.
     */
    @Test
    void theHealIsIdempotentAcrossBoots() {
        int firstBoot = ConfigMigrator.healedDrainRateTicks(PRE_STAMP, 10);
        int secondBoot = ConfigMigrator.healedDrainRateTicks(STAMPED, firstBoot);
        assertEquals(firstBoot, secondBoot, "the second boot must not move the value again");

        // And an admin who reverts to 10 after the heal keeps it across boots.
        assertEquals(10, ConfigMigrator.healedDrainRateTicks(STAMPED,
            ConfigMigrator.healedDrainRateTicks(STAMPED, 10)));
    }

    // ── The constants the heal keys off ─────────────────────────────────

    /**
     * The heal is a value comparison against two literals, so if either drifts
     * away from what actually shipped, every test above goes on passing while
     * healing the wrong thing. Pin them to the versions they came from.
     */
    @Test
    void theHealTargetsTheDefaultsThatActuallyShipped() {
        assertEquals(10, ConfigMigrator.STALE_DRAIN_RATE_TICKS,
            "10 is the default that shipped up to 2.2.21 and is the only value safe to heal");
        assertEquals(1, ConfigMigrator.CURRENT_DRAIN_RATE_TICKS,
            "1 tick per air point is the 2.2.22+ default: 300 air = 15s on land, vanilla cod/salmon parity");
        assertEquals(1, GameplayConfig.CURRENT_CONFIG_VERSION,
            "the stamp written into gameplay.toml must match the version the spec declares");

        // The heal writes CURRENT_DRAIN_RATE_TICKS into the file, so if the spec's
        // default ever moves again and this literal does not, the heal would drag
        // every unstamped install onto a value the mod no longer ships. Read the
        // default off the spec rather than restating it.
        assertEquals(ConfigMigrator.CURRENT_DRAIN_RATE_TICKS,
            GameplayConfig.OCEAN_ORIGINS_DRAIN_RATE_TICKS.getDefault(),
            "the value the heal writes must be the default the spec actually declares");
    }

    // ── The file the heal actually rewrites ─────────────────────────────

    /**
     * A gameplay.toml as {@code ModConfigSpec} writes one: every value carries the
     * spec's comment above it.
     */
    private static final String AFFECTED_FILE = """
        #Ocean origins slowly lose air while out of water (Minecraft-fish style).
        #Turn off to disable the on-land suffocation entirely.
        \t
        [ocean_origins]
        \tdries_out = true
        \t#Ticks per single air point lost while out of water.
        \tdrain_rate_ticks = 10
        \t#Damage applied per second once air is exhausted.
        \tdrown_damage_per_second = 2.0

        [orb_of_origins]
        \tlevels_per_use = 3
        """;

    private static Path write(Path dir, String contents) throws IOException {
        Path file = dir.resolve("gameplay.toml");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * The end-to-end path, not just the decision: parse the user's real file, move
     * the value, stamp it, and write it back.
     *
     * <p>The comment assertions are the point of doing this against a whole file.
     * The heal rewrites gameplay.toml wholesale, and {@code ModConfigSpec} only
     * rewrites when it has a correction to make, so comments this pass dropped
     * would be gone from the user's config permanently. Unrelated sections are
     * pinned for the same reason.
     */
    @Test
    void healingRewritesTheValueAndKeepsTheRestOfTheFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, AFFECTED_FILE);
        ConfigMigrator.healGameplayConfig(file);
        String healed = read(file);

        assertTrue(healed.contains("drain_rate_ticks = 1"), "the stale value must be healed:\n" + healed);
        assertFalse(healed.contains("drain_rate_ticks = 10"), "and not left behind:\n" + healed);
        assertTrue(healed.contains("config_version = 1"), "the file must be stamped:\n" + healed);

        assertTrue(healed.contains("Ticks per single air point lost while out of water."),
            "the spec's comments must survive the rewrite:\n" + healed);
        assertTrue(healed.contains("Turn off to disable the on-land suffocation entirely."),
            "including the ones above the section:\n" + healed);
        assertTrue(healed.contains("levels_per_use = 3"),
            "and every unrelated setting the user had:\n" + healed);
        assertTrue(healed.contains("drown_damage_per_second = 2.0"),
            "including the neighbours of the healed key:\n" + healed);
    }

    /** A second boot must be a true no-op, byte for byte. */
    @Test
    void theSecondBootLeavesTheFileAlone(@TempDir Path dir) throws IOException {
        Path file = write(dir, AFFECTED_FILE);
        ConfigMigrator.healGameplayConfig(file);
        String afterFirst = read(file);

        ConfigMigrator.healGameplayConfig(file);
        assertEquals(afterFirst, read(file), "the stamp must stop the second pass touching the file at all");
    }

    /**
     * An admin who deliberately wants the slow drain sets it back after the heal.
     * The stamp is already in the file by then, so it has to stand.
     */
    @Test
    void aDeliberateTenSurvivesOnceTheFileIsStamped(@TempDir Path dir) throws IOException {
        Path file = write(dir, AFFECTED_FILE);
        ConfigMigrator.healGameplayConfig(file);
        Files.writeString(file, read(file).replace("drain_rate_ticks = 1", "drain_rate_ticks = 10"));

        ConfigMigrator.healGameplayConfig(file);
        assertTrue(read(file).contains("drain_rate_ticks = 10"),
            "a stamped file's value belongs to the admin:\n" + read(file));
    }

    /** A config the mod cannot parse must never be damaged, and must not stop the boot. */
    @Test
    void anUnparseableFileIsLeftExactlyAsItWas(@TempDir Path dir) throws IOException {
        Path file = write(dir, "[ocean_origins\ndrain_rate_ticks = = 10\n");
        String before = read(file);

        ConfigMigrator.healGameplayConfig(file);

        assertEquals(before, read(file), "a file the parser rejected must come back byte-identical");
        assertFalse(Files.exists(file.resolveSibling("gameplay.toml.tmp")), "and leave no temp file behind");
    }

    /** Fresh install: the spec is about to write the file with current defaults. */
    @Test
    void aMissingFileIsNotCreated(@TempDir Path dir) {
        Path file = dir.resolve("gameplay.toml");
        ConfigMigrator.healGameplayConfig(file);
        assertFalse(Files.exists(file), "the heal must not conjure a config file the spec has not written yet");
    }
}
