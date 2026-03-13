package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

import java.util.TreeMap;

public class OriginCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("origin")
                .requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                .then(Commands.literal("get")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeGet(ctx, null))
                        .then(Commands.argument("layer", IdentifierArgument.id())
                            .executes(ctx -> executeGet(ctx, IdentifierArgument.getId(ctx, "layer"))))))
                .then(Commands.literal("set")
                    .requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("layer", IdentifierArgument.id())
                            .then(Commands.argument("origin", IdentifierArgument.id())
                                .executes(ctx -> executeSet(ctx))))))
                .then(Commands.literal("reset")
                    .requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeReset(ctx, null))
                        .then(Commands.argument("layer", IdentifierArgument.id())
                            .executes(ctx -> executeReset(ctx, IdentifierArgument.getId(ctx, "layer"))))))
                .then(Commands.literal("list")
                    .executes(ctx -> executeList(ctx, null))
                    .then(Commands.argument("layer", IdentifierArgument.id())
                        .executes(ctx -> executeList(ctx, IdentifierArgument.getId(ctx, "layer")))))
                .then(Commands.literal("has")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("power", IdentifierArgument.id())
                            .executes(ctx -> executeHas(ctx)))))
                .then(Commands.literal("gui")
                    .executes(ctx -> executeGui(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                        .executes(ctx -> executeGui(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reload")
                    .requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                    .executes(ctx -> executeReload(ctx)))
        );
    }

    private static int executeGet(CommandContext<CommandSourceStack> ctx, Identifier layerId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        if (layerId != null) {
            Identifier originId = data.getOrigin(layerId);
            if (originId == null) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " has no origin in layer " + layerId), false);
            } else {
                var origin = OriginDataManager.INSTANCE.getOrigin(originId);
                String name = origin != null ? origin.name().getString() : originId.toString();
                ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + "'s origin in " + layerId + ": " + name + " (" + originId + ")"), false);
            }
        } else {
            var origins = data.getOrigins();
            if (origins.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " has no origins selected."), false);
            } else {
                StringBuilder sb = new StringBuilder(player.getName().getString() + "'s origins:\n");
                new TreeMap<>(origins).forEach((layer, origin) -> {
                    var originData = OriginDataManager.INSTANCE.getOrigin(origin);
                    String name = originData != null ? originData.name().getString() : origin.toString();
                    sb.append("  ").append(layer).append(": ").append(name).append("\n");
                });
                ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            }
        }
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier layerId = IdentifierArgument.getId(ctx, "layer");
        Identifier originId = IdentifierArgument.getId(ctx, "origin");

        if (!LayerDataManager.INSTANCE.hasLayer(layerId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown layer: " + layerId));
            return 0;
        }
        if (!OriginDataManager.INSTANCE.hasOrigin(originId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown origin: " + originId));
            return 0;
        }

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        Identifier oldOrigin = data.getOrigin(layerId);
        ActiveOriginService.applyOriginPowers(player, layerId, oldOrigin, originId);
        data.setOrigin(layerId, originId);
        NeoOriginsNetwork.syncToPlayer(player);

        var origin = OriginDataManager.INSTANCE.getOrigin(originId);
        String name = origin != null ? origin.name().getString() : originId.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Set " + player.getName().getString() + "'s origin in " + layerId + " to " + name), true);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, Identifier layerId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        ActiveOriginService.revokeAllPowers(player);
        if (layerId != null) {
            data.removeOrigin(layerId);
        } else {
            data.clear();
        }

        NeoOriginsNetwork.syncToPlayer(player);
        NeoOriginsNetwork.openSelectionScreen(player, false);

        String scope = layerId != null ? "layer " + layerId : "all layers";
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Reset " + player.getName().getString() + "'s origin for " + scope), true);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx, Identifier layerId) {
        if (layerId != null) {
            var layer = LayerDataManager.INSTANCE.getLayer(layerId);
            if (layer == null) {
                ctx.getSource().sendFailure(Component.literal("Unknown layer: " + layerId));
                return 0;
            }
            StringBuilder sb = new StringBuilder("Origins in layer " + layerId + ":\n");
            for (var condOrigin : layer.origins()) {
                var origin = OriginDataManager.INSTANCE.getOrigin(condOrigin.origin());
                String name = origin != null ? origin.name().getString() : condOrigin.origin().toString();
                sb.append("  ").append(condOrigin.origin()).append(" - ").append(name).append("\n");
            }
            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        } else {
            StringBuilder sb = new StringBuilder("All registered origins:\n");
            new TreeMap<>(OriginDataManager.INSTANCE.getOrigins()).forEach((id, origin) -> {
                sb.append("  ").append(id).append(" - ").append(origin.name().getString()).append("\n");
            });
            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        }
        return 1;
    }

    private static int executeHas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier powerId = IdentifierArgument.getId(ctx, "power");

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        boolean hasPower = false;
        for (var entry : data.getOrigins().entrySet()) {
            var origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin != null && origin.powers().contains(powerId)) {
                hasPower = true;
                break;
            }
        }
        final boolean result = hasPower;
        ctx.getSource().sendSuccess(() -> Component.literal(
            player.getName().getString() + (result ? " has" : " does not have") + " power: " + powerId), false);
        return result ? 1 : 0;
    }

    private static int executeGui(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        ServerPlayer target = player != null ? player : ctx.getSource().getPlayerOrException();
        NeoOriginsNetwork.openSelectionScreen(target, false, true);
        if (player != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Opened origin selection for " + target.getName().getString()), true);
        }
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Use /reload to reload datapacks (origins reload automatically)."), false);
        return 1;
    }
}
