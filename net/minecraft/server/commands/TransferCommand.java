package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;

public class TransferCommand {
    private static final SimpleCommandExceptionType ERROR_NO_PLAYERS = new SimpleCommandExceptionType(
        Component.translatable("commands.transfer.error.no_players")
    );

    public static void register(CommandDispatcher<CommandSourceStack> p_321596_) {
        p_321596_.register(
            Commands.literal("transfer")
                .requires(p_321781_ -> p_321781_.hasPermission(3))
                .then(
                    Commands.argument("hostname", StringArgumentType.string())
                        .executes(
                            p_321698_ -> transfer(
                                    p_321698_.getSource(),
                                    StringArgumentType.getString(p_321698_, "hostname"),
                                    25565,
                                    List.of(p_321698_.getSource().getPlayerOrException())
                                )
                        )
                        .then(
                            Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(
                                    p_321648_ -> transfer(
                                            p_321648_.getSource(),
                                            StringArgumentType.getString(p_321648_, "hostname"),
                                            IntegerArgumentType.getInteger(p_321648_, "port"),
                                            List.of(p_321648_.getSource().getPlayerOrException())
                                        )
                                )
                                .then(
                                    Commands.argument("players", EntityArgument.players())
                                        .executes(
                                            p_321632_ -> transfer(
                                                    p_321632_.getSource(),
                                                    StringArgumentType.getString(p_321632_, "hostname"),
                                                    IntegerArgumentType.getInteger(p_321632_, "port"),
                                                    EntityArgument.getPlayers(p_321632_, "players")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int transfer(CommandSourceStack p_321844_, String p_321828_, int p_321760_, Collection<ServerPlayer> p_321481_) throws CommandSyntaxException {
        if (p_321481_.isEmpty()) {
            throw ERROR_NO_PLAYERS.create();
        } else {
            for (ServerPlayer serverplayer : p_321481_) {
                serverplayer.connection.send(new ClientboundTransferPacket(p_321828_, p_321760_));
            }

            if (p_321481_.size() == 1) {
                p_321844_.sendSuccess(
                    () -> Component.translatable("commands.transfer.success.single", p_321481_.iterator().next().getDisplayName(), p_321828_, p_321760_), true
                );
            } else {
                p_321844_.sendSuccess(() -> Component.translatable("commands.transfer.success.multiple", p_321481_.size(), p_321828_, p_321760_), true);
            }

            return p_321481_.size();
        }
    }
}
