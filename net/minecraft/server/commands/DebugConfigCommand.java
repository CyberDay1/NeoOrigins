package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

public class DebugConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_294998_) {
        p_294998_.register(
            Commands.literal("debugconfig")
                .requires(p_295607_ -> p_295607_.hasPermission(3))
                .then(
                    Commands.literal("config")
                        .then(
                            Commands.argument("target", EntityArgument.player())
                                .executes(p_294114_ -> config(p_294114_.getSource(), EntityArgument.getPlayer(p_294114_, "target")))
                        )
                )
                .then(
                    Commands.literal("unconfig")
                        .then(
                            Commands.argument("target", UuidArgument.uuid())
                                .suggests(
                                    (p_295936_, p_294731_) -> SharedSuggestionProvider.suggest(getUuidsInConfig(p_295936_.getSource().getServer()), p_294731_)
                                )
                                .executes(p_294910_ -> unconfig(p_294910_.getSource(), UuidArgument.getUuid(p_294910_, "target")))
                        )
                )
        );
    }

    private static Iterable<String> getUuidsInConfig(MinecraftServer p_294210_) {
        Set<String> set = new HashSet<>();

        for (Connection connection : p_294210_.getConnection().getConnections()) {
            if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl) {
                set.add(serverconfigurationpacketlistenerimpl.getOwner().getId().toString());
            }
        }

        return set;
    }

    private static int config(CommandSourceStack p_295666_, ServerPlayer p_294477_) {
        GameProfile gameprofile = p_294477_.getGameProfile();
        p_294477_.connection.switchToConfig();
        p_295666_.sendSuccess(() -> Component.literal("Switched player " + gameprofile.getName() + "(" + gameprofile.getId() + ") to config mode"), false);
        return 1;
    }

    private static int unconfig(CommandSourceStack p_294216_, UUID p_296468_) {
        for (Connection connection : p_294216_.getServer().getConnection().getConnections()) {
            PacketListener packetlistener = connection.getPacketListener();
            if (packetlistener instanceof ServerConfigurationPacketListenerImpl) {
                ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl = (ServerConfigurationPacketListenerImpl)packetlistener;
                if (serverconfigurationpacketlistenerimpl.getOwner().getId().equals(p_296468_)) {
                    serverconfigurationpacketlistenerimpl.returnToWorld();
                }
            }
        }

        p_294216_.sendFailure(Component.literal("Can't find player to unconfig"));
        return 0;
    }
}
