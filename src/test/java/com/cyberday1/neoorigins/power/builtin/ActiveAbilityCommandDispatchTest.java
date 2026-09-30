package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.command.CommandPowerGuard;
import com.cyberday1.neoorigins.command.OriginsCompatCommands;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealCommands;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameRules;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitHub #135: the reporter's active_ability, run through the game's real
 * dispatcher. The plumbing delivers a well-formed command; theirs does not parse.
 */
// hub: neoorigins/issue-135-active-ability-command.md
class ActiveAbilityCommandDispatchTest {

    private static final String REPORTED = "tellraw @s {\"Triggered\"}";
    private static final String CORRECTED = "tellraw @s {\"text\":\"Triggered\"}";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private org.apache.logging.log4j.core.Logger logger;
    private Level previousLevel;
    private ServerPlayer player;
    private ServerGamePacketListenerImpl connection;
    private boolean loadedAdmin, loadedToggles;

    @BeforeEach
    void rig() {
        CommandPowerGuard.clearParseMemory();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        MinecraftServer server = RealCommands.server(RealCommands.commands());
        Mockito.when(server.getGameRules()).thenReturn(new GameRules());
        player = PlayerLifecycle.realPlayer(server);
        connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        player.connection = connection;

        logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(NeoOrigins.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        appender = new AbstractAppender("issue-135", null, null, true, Property.EMPTY_ARRAY) {
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

    /** The JSON from the issue, byte for byte apart from the command string. */
    private PowerHolder<?> reportersPower(String command) {
        JsonObject cmd = new JsonObject();
        cmd.addProperty("c", command);
        JsonObject config = JsonParser.parseString("{\"type\": \"neoorigins:active_ability\", \"name\": \"Test\","
            + " \"hunger_cost\": 4, \"entity_action\": {\"type\": \"neoorigins:execute_command\","
            + " \"command\": " + cmd.get("c") + "}}").getAsJsonObject();
        var type = new ActiveAbilityPower();
        var parsed = type.codec().parse(JsonOps.INSTANCE, config).getOrThrow();
        return new PowerHolder<>(ResourceLocation.parse("testpack:test"), type, parsed,
            Component.empty(), Component.empty());
    }

    private List<String> chatSentToPlayer() {
        return Mockito.mockingDetails(connection).getInvocations().stream()
            .flatMap(i -> java.util.Arrays.stream(i.getArguments()))
            .filter(a -> a instanceof ClientboundSystemChatPacket)
            .map(a -> ((ClientboundSystemChatPacket) a).content().getString())
            .toList();
    }

    private float food() {
        return player.getFoodData().getFoodLevel() + player.getFoodData().getSaturationLevel();
    }

    @Test
    void correctedCommandReachesThePlayer() {
        float before = food();
        reportersPower(CORRECTED).onActivated(player);

        assertEquals(List.of("Triggered"), chatSentToPlayer());
        assertEquals(before - 4, food(), 1e-4);
        assertEquals(List.of(), warnings);
    }

    @Test
    void reportedCommandSpendsHungerAndSaysNothing() {
        var source = player.createCommandSourceStack().withSuppressedOutput().withPermission(2);
        assertNull(OriginsCompatCommands.parseFailure(source, CORRECTED));
        String vanilla = OriginsCompatCommands.parseFailure(source, REPORTED);
        assertNotNull(vanilla, "vanilla parsed the reported command");

        float before = food();
        reportersPower(REPORTED).onActivated(player);

        assertEquals(List.of(), chatSentToPlayer());
        assertEquals(before - 4, food(), 1e-4, "the ability counted as a success");
        assertEquals(1, warnings.size(), String.valueOf(warnings));
        String w = warnings.get(0);
        assertTrue(w.contains("[testpack:test execute_command]") && w.contains(vanilla) && w.contains(REPORTED), w);
        System.out.println("issue135 vanilla: " + vanilla);
        System.out.println("issue135 logged: " + w);
    }
}
