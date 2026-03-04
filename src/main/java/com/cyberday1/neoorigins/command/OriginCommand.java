package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.event.OriginEventHandler;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class OriginCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("origin")
                .requires(src -> src.hasPermission(0)) // all players can /origin get themselves
                .then(Commands.literal("get")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeGet(ctx, null))
                        .then(Commands.argument("layer", ResourceLocationArgument.id())
                            .executes(ctx -> executeGet(ctx, ResourceLocationArgument.getId(ctx, "layer"))))))
                .then(Commands.literal("set")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("layer", ResourceLocationArgument.id())
                            .then(Commands.argument("origin", ResourceLocationArgument.id())
                                .executes(ctx -> executeSet(ctx))))))
                .then(Commands.literal("reset")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeReset(ctx, null))
                        .then(Commands.argument("layer", ResourceLocationArgument.id())
                            .executes(ctx -> executeReset(ctx, ResourceLocationArgument.getId(ctx, "layer"))))))
                .then(Commands.literal("list")
                    .executes(ctx -> executeList(ctx, null))
                    .then(Commands.argument("layer", ResourceLocationArgument.id())
                        .executes(ctx -> executeList(ctx, ResourceLocationArgument.getId(ctx, "layer")))))
                .then(Commands.literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeReload(ctx)))
        );
    }

    private static int executeGet(CommandContext<CommandSourceStack> ctx, ResourceLocation layerId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        if (layerId != null) {
            ResourceLocation originId = data.getOrigin(layerId);
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
                origins.forEach((layer, origin) -> {
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
        ResourceLocation layerId = ResourceLocationArgument.getId(ctx, "layer");
        ResourceLocation originId = ResourceLocationArgument.getId(ctx, "origin");

        if (!LayerDataManager.INSTANCE.hasLayer(layerId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown layer: " + layerId));
            return 0;
        }
        if (!OriginDataManager.INSTANCE.hasOrigin(originId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown origin: " + originId));
            return 0;
        }

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        ResourceLocation oldOrigin = data.getOrigin(layerId);
        OriginEventHandler.applyOriginPowers(player, layerId, oldOrigin, originId);
        data.setOrigin(layerId, originId);
        NeoOriginsNetwork.syncToPlayer(player);

        var origin = OriginDataManager.INSTANCE.getOrigin(originId);
        String name = origin != null ? origin.name().getString() : originId.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Set " + player.getName().getString() + "'s origin in " + layerId + " to " + name), true);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, ResourceLocation layerId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        OriginEventHandler.revokeAllPowers(player);
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

    private static int executeList(CommandContext<CommandSourceStack> ctx, ResourceLocation layerId) {
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
            OriginDataManager.INSTANCE.getOrigins().forEach((id, origin) -> {
                sb.append("  ").append(id).append(" - ").append(origin.name().getString()).append("\n");
            });
            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        }
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Use /reload to reload datapacks (origins reload automatically)."), false);
        return 1;
    }
}
