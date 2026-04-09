package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes logs/neoorigins-compat.log.
 * One line per attempted translation: [PASS], [FAIL], or [SKIP].
 * Opened at start of PowerDataManager.apply(), closed at end of OriginDataManager.apply().
 */
public final class CompatTranslationLog {

    private static PrintWriter writer;
    private static int passed;
    private static int failed;
    private static int skipped;

    private CompatTranslationLog() {}

    public static void open() {
        passed = 0;
        failed = 0;
        skipped = 0;
        try {
            Path logsDir = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(logsDir);
            Path logFile = logsDir.resolve("neoorigins-compat.log");
            writer = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            writer.println("NeoOrigins Compat Translation Log — " + ts);
            writer.println("---------------------------------------------------------");
            writer.flush();
        } catch (IOException e) {
            NeoOrigins.LOGGER.error("Failed to open neoorigins-compat.log", e);
            writer = null;
        }
    }

    public static void pass(ResourceLocation id, String detail) {
        passed++;
        writeLine("[PASS] " + id + "  (" + detail + ")");
    }

    public static void fail(ResourceLocation id, String reason) {
        failed++;
        writeLine("[FAIL] " + id + "  (" + reason + ")");
    }

    public static void skip(ResourceLocation id, String originType, String reason) {
        skipped++;
        writeLine("[SKIP] " + id + "  (" + originType + " — " + reason + ")");
    }

    public static void close() {
        if (writer == null) return;
        writer.println("---------------------------------------------------------");
        writer.println("Summary: " + passed + " passed, " + failed + " failed, " + skipped + " skipped");
        writer.flush();
        writer.close();
        writer = null;
    }

    private static void writeLine(String line) {
        if (writer != null) {
            writer.println(line);
            writer.flush();
        }
    }
}
