package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.command.CommandPowerGuard;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.power.builtin.ActiveAbilityPower;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealCommands;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * A pack command that cannot parse used to fail without a trace: every power
 * runs commands on a suppressed-output source, so vanilla's own error went
 * nowhere. Each site that runs a pack command now logs it, naming the power.
 * Driven through the real actions and condition with the game's dispatcher.
 */
// hub: neoorigins/pack-command-parse-warning.md
class PackCommandParseWarningTest {

    private static final String BROKEN = "tellraw @s {\"Triggered\"}";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private org.apache.logging.log4j.core.Logger logger;
    private Level previousLevel;
    private Commands commands;
    private MinecraftServer server;
    private ServerPlayer player;
    private boolean loadedAdmin, loadedToggles;

    @BeforeEach
    void rig() {
        CommandPowerGuard.clearParseMemory();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        commands = Mockito.spy(RealCommands.commands());
        Mockito.doNothing().when(commands).performPrefixedCommand(any(), any());
        server = RealCommands.server(commands);
        player = PlayerLifecycle.realPlayer(server);

        logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(NeoOrigins.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        appender = new AbstractAppender("parse-warnings", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) warnings.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.removeAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        CommandPowerGuard.clearParseMemory();
    }

    private List<String> parseWarnings() {
        return warnings.stream().filter(w -> w.contains("does not parse")).toList();
    }

    private static JsonObject json(String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static String commandJson(String command) {
        JsonObject o = new JsonObject();
        o.addProperty("c", command);
        return o.get("c").toString();
    }

    /** A native active ability whose action is {@code execute_command}, activated as a keypress would. */
    private PowerHolder<?> nativeAbility(String id, String command) {
        JsonObject config = json("{\"type\": \"neoorigins:active_ability\", \"cooldown_ticks\": 20,"
            + " \"entity_action\": {\"type\": \"neoorigins:execute_command\", \"command\": " + commandJson(command) + "}}");
        var type = new ActiveAbilityPower();
        var parsed = type.codec().parse(JsonOps.INSTANCE, config).getOrThrow();
        return new PowerHolder<>(ResourceLocation.parse(id), type, parsed, Component.empty(), Component.empty());
    }

    @Test
    void brokenExecuteCommandNamesThePower() {
        nativeAbility("testpack:broken_trigger", BROKEN).onActivated(player);

        List<String> found = parseWarnings();
        assertEquals(1, found.size(), "expected one parse warning, got " + warnings);
        String w = found.get(0);
        assertTrue(w.contains("testpack:broken_trigger execute_command"), "the power id is missing: " + w);
        assertTrue(w.contains(BROKEN), "the command is missing: " + w);
        // Behaviour is otherwise unchanged: the command is still handed to vanilla.
        Mockito.verify(commands).performPrefixedCommand(any(), eq(BROKEN));
    }

    @Test
    void validExecuteChainDoesNotWarn() {
        nativeAbility("testpack:fine_trigger", "execute as @s run say hi").onActivated(player);
        assertEquals(List.of(), parseWarnings());
        Mockito.verify(commands).performPrefixedCommand(any(), eq("execute as @s run say hi"));
    }

    @Test
    void warnsOncePerPowerAndCommand() {
        var power = nativeAbility("testpack:broken_trigger", BROKEN);
        power.onActivated(player);
        player.tickCount += 100;
        power.onActivated(player);
        Mockito.verify(commands, Mockito.times(2)).performPrefixedCommand(any(), eq(BROKEN));
        assertEquals(1, parseWarnings().size(), "a repeating power must not flood the log: " + warnings);
    }

    @Test
    void apoliExecuteCommand() {
        EntityAction action = ActionParser.parse(json(
            "{\"type\": \"origins:execute_command\", \"command\": " + commandJson(BROKEN) + "}"), "apack:zap");
        action.execute(player);
        assertEquals(1, parseWarnings().size(), String.valueOf(warnings));
        assertTrue(parseWarnings().get(0).contains("apack:zap execute_command"), parseWarnings().get(0));
    }

    @Test
    void commandCondition() {
        var condition = ConditionParser.parse(json(
            "{\"type\": \"origins:command\", \"command\": " + commandJson(BROKEN) + "}"), "apack:gate");
        condition.test(player);
        assertEquals(1, parseWarnings().size(), String.valueOf(warnings));
        assertTrue(parseWarnings().get(0).contains("apack:gate command condition"), parseWarnings().get(0));
    }

    @Test
    void raycastCommandAlongRay() {
        EntityAction action = ActionParser.parse(json("{\"type\": \"origins:raycast\", \"distance\": 3,"
            + " \"block\": false, \"entity\": false, \"command_along_ray\": " + commandJson(BROKEN) + "}"), "apack:beam");
        try {
            action.execute(player);
        } catch (RuntimeException ignored) {
            // The ray walk after the along-ray commands needs a real world; the warning comes first.
        }
        assertEquals(1, parseWarnings().size(), String.valueOf(warnings));
        assertTrue(parseWarnings().get(0).contains("apack:beam command_along_ray"), parseWarnings().get(0));
    }

    @Test
    void raycastCommandAtHit() {
        var level = player.level();
        Mockito.when(level.clip(any())).thenReturn(
            new BlockHitResult(new Vec3(0.5, 64, 2.5), Direction.UP, new BlockPos(0, 64, 2), false));
        EntityAction action = ActionParser.parse(json("{\"type\": \"origins:raycast\", \"distance\": 3,"
            + " \"block\": true, \"entity\": false, \"command_at_hit\": " + commandJson(BROKEN) + "}"), "apack:blink");
        try {
            action.execute(player);
        } catch (RuntimeException ignored) {
            // Anything past the hit command needs a real world.
        }
        assertEquals(1, parseWarnings().size(), String.valueOf(warnings));
        assertTrue(parseWarnings().get(0).contains("apack:blink command_at_hit"), parseWarnings().get(0));
    }

    @Test
    void projectileAction() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        Mockito.when(level.getServer()).thenReturn(server);
        BuiltinActions.applyProjectileAction(player, player,
            json("{\"type\": \"origins:execute_command\", \"command\": " + commandJson(BROKEN) + "}"), level);
        assertEquals(1, parseWarnings().size(), String.valueOf(warnings));
        assertTrue(parseWarnings().get(0).contains("projectile_action execute_command"), parseWarnings().get(0));
    }

    @Test
    void legacyCommandTheCommandEventRepairsIsNotReported() {
        // Fails to parse as written, parses once LegacyCommandRewriter#rewrite has run.
        String legacy = "particle minecraft:dust 1 0 0 1 ~ ~ ~ 0 0 0 0 1";
        var source = player.createCommandSourceStack().withSuppressedOutput().withPermission(2);
        assertTrue(CommandPowerGuard.checkParses(source, legacy, "apack:legacy", "command_at_hit", true));
        assertEquals(List.of(), parseWarnings());
        // The direct-dispatch condition gets no rewrite, so the same text is reported there.
        CommandPowerGuard.checkParses(source, legacy, "apack:legacy", "command condition", false);
        assertEquals(1, parseWarnings().size(), String.valueOf(warnings));
    }
}
