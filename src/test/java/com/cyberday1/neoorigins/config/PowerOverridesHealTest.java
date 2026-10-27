package com.cyberday1.neoorigins.config;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-time {@code power_overrides.toml} heal in
 * {@link ConfigMigrator#healPowerOverridesConfig}.
 *
 * <p>2.2.32 wired override keys that reached no field before and set their
 * defaults to the shipped JSON values. NeoForge writes every default to disk on
 * first launch, so each existing file holds the old default, which now differs
 * from the new one and would be applied: Sentinel knockback resistance would
 * fall from 0.4 to 0.2 on every server that updated. Same rules as the
 * gameplay.toml heal: the missing {@code config_version} stamp, not the value,
 * says the file is old, and only an exact stale value moves.
 */
class PowerOverridesHealTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The heal's target values must be the defaults the spec declares, or it heals to a wrong value. */
    @Test
    void everyHealTargetIsTheSpecDefault() {
        for (ConfigMigrator.DefaultChange change : ConfigMigrator.CHANGED_POWER_DEFAULTS) {
            Object def = PowerOverridesConfig.getPowerDefault("neoorigins:" + change.power(), change.field());
            assertTrue(def instanceof Number, change.power() + "." + change.field() + " is not a numeric override key");
            assertEquals(change.current().doubleValue(), ((Number) def).doubleValue(), 1e-9,
                change.power() + "." + change.field() + " heals to a value that is not its default");
        }
    }

    @Test
    void anUnstampedFileOnStaleDefaultsIsHealedAndStamped(@TempDir Path dir) throws IOException {
        Path file = write(dir, OLD_FILE);
        ConfigMigrator.healPowerOverridesConfig(file);
        String healed = read(file);

        assertTrue(healed.contains("amount = 0.4"), "Sentinel must move off the stale 0.2:\n" + healed);
        assertTrue(healed.contains("hunger_cost = 0"), "Voidwalker phase must move off the stale 3:\n" + healed);
        assertTrue(healed.contains("speed = 2.0"), "Warden sonic boom must move off the stale 1.0:\n" + healed);
        assertTrue(healed.contains("config_version = 1"), "the file must be stamped:\n" + healed);
        assertTrue(healed.contains("return_ratio = 0.3"), "unrelated keys must survive:\n" + healed);
    }

    @Test
    void aHandTypedValueSurvives(@TempDir Path dir) throws IOException {
        Path file = write(dir, OLD_FILE.replace("amount = 0.2", "amount = 0.25"));
        ConfigMigrator.healPowerOverridesConfig(file);
        assertTrue(read(file).contains("amount = 0.25"), "0.25 was never a default, so it was chosen:\n" + read(file));
    }

    @Test
    void aStampedFileIsNeverTouched(@TempDir Path dir) throws IOException {
        Path file = write(dir, "config_version = 1\n" + OLD_FILE);
        String before = read(file);
        ConfigMigrator.healPowerOverridesConfig(file);
        assertEquals(before, read(file), "a stamped file's values belong to the admin");
    }

    @Test
    void theSecondBootLeavesTheFileAlone(@TempDir Path dir) throws IOException {
        Path file = write(dir, OLD_FILE);
        ConfigMigrator.healPowerOverridesConfig(file);
        String afterFirst = read(file);
        ConfigMigrator.healPowerOverridesConfig(file);
        assertEquals(afterFirst, read(file), "the stamp must stop the second pass touching the file");
    }

    /** The relevant slice of a power_overrides.toml written by 2.2.31. */
    private static final String OLD_FILE = """
        #Per-power parameter overrides.
        [power_overrides]

        	[power_overrides.abyssal_thorns_aura]
        		#Range: 0.0 ~ 10.0
        		return_ratio = 0.3

        	[power_overrides.class_sentinel_knockback_resist]
        		#Range: -10.0 ~ 10.0
        		amount = 0.2

        	[power_overrides.voidwalker_active_phase]
        		#Range: 0 ~ 100
        		hunger_cost = 3

        	[power_overrides.warden_sonic_boom]
        		#Range: 0.0 ~ 10.0
        		speed = 1.0
        """;

    private static Path write(Path dir, String contents) throws IOException {
        Path file = dir.resolve("power_overrides.toml");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
