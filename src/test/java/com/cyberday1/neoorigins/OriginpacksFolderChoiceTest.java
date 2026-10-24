package com.cyberday1.neoorigins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which folder {@link NeoOrigins#chooseOriginpacksDir} reads packs from.
 *
 * <p>2.0 moved origin-packs from {@code originpacks/} in the game root to
 * {@code config/originpacks/}, and the resolver read the legacy folder only when
 * the new one did not <em>exist</em>. But the same release also created
 * {@code config/originpacks/} on every single launch, so one launch of 2.0 was
 * enough to put an empty canonical folder beside a full legacy one, and from
 * then on the packs were never read again. Existence is the wrong question:
 * {@link #theEmptyCanonicalFolderDoesNotStrandLegacyPacks} is the case that
 * shipped broken from 2.0 through 2.2.30.
 */
class OriginpacksFolderChoiceTest {

    private static Path withPack(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("SomeOriginPack"));
        return dir;
    }

    private static Path empty(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    // ── The bug ─────────────────────────────────────────────────────────

    @Test
    void theEmptyCanonicalFolderDoesNotStrandLegacyPacks(@TempDir Path root) throws IOException {
        Path config = empty(root.resolve("config/originpacks"));
        Path legacy = withPack(root.resolve("originpacks"));

        assertEquals(legacy, NeoOrigins.chooseOriginpacksDir(config, legacy));
    }

    @Test
    void aCanonicalFolderHoldingNothingButDotFilesCountsAsEmpty(@TempDir Path root) throws IOException {
        Path config = empty(root.resolve("config/originpacks"));
        Files.createFile(config.resolve(".DS_Store"));
        Path legacy = withPack(root.resolve("originpacks"));

        assertEquals(legacy, NeoOrigins.chooseOriginpacksDir(config, legacy));
    }

    // ── The cases that already worked, kept honest ──────────────────────

    @Test
    void theCanonicalFolderWinsWhenItHoldsPacks(@TempDir Path root) throws IOException {
        Path config = withPack(root.resolve("config/originpacks"));
        Path legacy = withPack(root.resolve("originpacks"));

        assertEquals(config, NeoOrigins.chooseOriginpacksDir(config, legacy));
    }

    @Test
    void anAbsentCanonicalFolderFallsBackToLegacyPacks(@TempDir Path root) throws IOException {
        Path config = root.resolve("config/originpacks");
        Path legacy = withPack(root.resolve("originpacks"));

        assertEquals(legacy, NeoOrigins.chooseOriginpacksDir(config, legacy));
    }

    @Test
    void anEmptyLegacyFolderIsNeverPreferred(@TempDir Path root) throws IOException {
        Path config = root.resolve("config/originpacks");
        Path legacy = empty(root.resolve("originpacks"));

        assertEquals(config, NeoOrigins.chooseOriginpacksDir(config, legacy));
    }

    @Test
    void aFreshInstallGetsTheCanonicalFolder(@TempDir Path root) {
        Path config = root.resolve("config/originpacks");
        Path legacy = root.resolve("originpacks");

        assertEquals(config, NeoOrigins.chooseOriginpacksDir(config, legacy));
    }
}
