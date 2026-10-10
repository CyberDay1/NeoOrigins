package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.rig.RealCommands;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The execute_command examples in docs/ACTIONS.md are copied by pack authors, so
 * each one must parse against the real dispatcher once its JSON string is decoded,
 * and the shape the page names as broken must not.
 */
class DocumentedCommandExamplesTest {

    private static final Pattern EXAMPLE =
        Pattern.compile("^\\{ \"type\": \"neoorigins:execute_command\".*}$", Pattern.MULTILINE);

    private static CommandSourceStack source;

    @BeforeAll
    static void dispatcher() {
        source = RealCommands.source();
    }

    private static List<String> documentedCommands() throws IOException {
        String page = Files.readString(RealDatapack.projectPath("docs/ACTIONS.md")).replace("\r\n", "\n");
        List<String> out = new ArrayList<>();
        Matcher m = EXAMPLE.matcher(page);
        while (m.find()) {
            JsonObject action = JsonParser.parseString(m.group()).getAsJsonObject();
            out.add(action.get("command").getAsString());
        }
        return out;
    }

    @Test
    void everyDocumentedExampleParses() throws IOException {
        List<String> commands = documentedCommands();
        assertTrue(commands.contains("tellraw @s {\"text\":\"Triggered\"}"),
            "the escaped tellraw example is missing from ACTIONS.md: " + commands);
        for (String command : commands) {
            assertNull(OriginsCompatCommands.parseFailure(source, command), command);
        }
    }

    @Test
    void theShapeNamedAsBrokenDoesNotParse() {
        assertFalse(OriginsCompatCommands.parsesCleanly(source, "tellraw @s {\"Triggered\"}"));
    }
}
