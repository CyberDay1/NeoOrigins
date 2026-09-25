package com.cyberday1.neoorigins.dev;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * POWER_REFERENCE.txt must be exactly what PowerReferenceGenerator renders from
 * this build. With -PwritePowerReference the test rewrites the file instead.
 */
// hub: neoorigins/power-reference.md
class PowerReferenceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void committedReferenceIsCurrent() throws IOException {
        String projectDir = System.getProperty("neoorigins.projectDir");
        assertNotNull(projectDir, "build.gradle must pass neoorigins.projectDir to the test task");
        Path file = Path.of(projectDir, "POWER_REFERENCE.txt");
        String rendered = PowerReferenceGenerator.render(Path.of(projectDir, "src/main/resources"));

        if (Boolean.getBoolean("neoorigins.writePowerReference")) {
            Files.writeString(file, rendered, StandardCharsets.UTF_8);
            return;
        }
        String committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(rendered, committed, "POWER_REFERENCE.txt is stale. Regenerate it with\n"
            + "  ./gradlew test --tests '*PowerReferenceTest' -PwritePowerReference");
    }
}
