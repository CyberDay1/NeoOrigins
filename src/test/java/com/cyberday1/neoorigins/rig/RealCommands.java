package com.cyberday1.neoorigins.rig;

import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.mockito.Mockito;

/**
 * The game's own command dispatcher, with every vanilla command registered,
 * behind a mocked server. Enough to parse any command exactly as the game
 * would; nothing here can execute one against a world.
 */
public final class RealCommands {

    private static Commands commands;

    private RealCommands() {}

    public static synchronized Commands commands() {
        if (commands == null) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            commands = new Commands(Commands.CommandSelection.DEDICATED,
                Commands.createValidationContext(VanillaRegistries.createWorldLookup()));
        }
        return commands;
    }

    /** A deep-stubbed server whose {@code getCommands()} is {@code commands}, with a real server source. */
    public static MinecraftServer server(Commands commands) {
        MinecraftServer server = Mockito.mock(MinecraftServer.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(server.getCommands()).thenReturn(commands);
        // Pack commands on this line start from the server's own source.
        ServerLevel overworld = Mockito.mock(ServerLevel.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(server.createCommandSourceStack()).thenAnswer(inv -> new CommandSourceStack(
            CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, overworld, LevelBasedPermissionSet.OWNER,
            Component.literal("Server"), server));
        return server;
    }

    /** A permission-2 source on a server that parses with the real dispatcher. */
    public static CommandSourceStack source() {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, LevelBasedPermissionSet.GAMEMASTER,
            Component.literal("rig"), server(commands()));
    }
}
