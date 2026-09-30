package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.rig.RealCommands;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code parsesCleanly} must agree with what the game would run. It used to
 * read only the top-level parse context, which an {@code execute ... run}
 * redirect leaves without a command, so every such command read as broken.
 */
class ParsesCleanlyTest {

    private static CommandSourceStack source;

    @BeforeAll
    static void dispatcher() {
        source = RealCommands.source();
    }

    private static boolean parses(String command) {
        return OriginsCompatCommands.parsesCleanly(source, command);
    }

    @Test
    void executeChainsParse() {
        assertTrue(parses("execute as @s run say hi"));
        assertTrue(parses("execute as @s at @s run tp @s ~ ~1 ~"));
        assertTrue(parses("/execute if entity @s run effect give @s minecraft:speed 5 1"));
    }

    @Test
    void plainCommandsParse() {
        assertTrue(parses("say hi"));
        assertTrue(parses("/tellraw @s {\"text\":\"Triggered\"}"));
    }

    @Test
    void brokenCommandsDoNot() {
        assertFalse(parses("tellraw @s {\"Triggered\"}"), "an object with no key is not a component");
        assertFalse(parses("nosuchcommand @s"));
        assertFalse(parses("say"), "say without a message is not executable");
        assertFalse(parses("execute as @s"), "an execute chain that runs nothing is not executable");
        assertFalse(parses("execute as @s run"));
        assertFalse(parses("kill @s junk"), "a runnable prefix with trailing input is still refused");
    }

    @Test
    void failureCarriesVanillasReason() {
        assertNotNull(OriginsCompatCommands.parseFailure(source, "tellraw @s {\"Triggered\"}"));
    }
}
