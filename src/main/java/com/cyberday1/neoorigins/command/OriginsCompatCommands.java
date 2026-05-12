package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.LegacyCommandRewriter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.Collection;

/**
 * Registers top-level {@code /resource} and {@code /power} commands that
 * mirror the Origins-mod (Fabric) command API. Origins++ mcfunctions use
 * these commands extensively ({@code resource change @s namespace:power/bar -1}).
 *
 * <p>Also hooks into {@link CommandEvent} to rewrite legacy 1.20 attribute
 * names in commands dispatched from mcfunctions.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class OriginsCompatCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_POWERS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            PowerDataManager.INSTANCE.getPowers().keySet(), builder);

    /**
     * Register the Origins-mod compat commands. Called from
     * {@link OriginCommand#register}.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /resource change <target> <power> <amount>
        // /resource set <target> <power> <amount>
        // /resource get <target> <power>
        dispatcher.register(Commands.literal("resource")
            .requires(cs -> cs.hasPermission(2))
            .then(Commands.literal("change")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                            .executes(OriginsCompatCommands::executeResourceChange)))))
            .then(Commands.literal("set")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                            .executes(OriginsCompatCommands::executeResourceSet)))))
            .then(Commands.literal("get")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executeResourceGet)))));

        // /power grant <target> <power>
        // /power revoke <target> <power>
        dispatcher.register(Commands.literal("power")
            .requires(cs -> cs.hasPermission(2))
            .then(Commands.literal("grant")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerGrant))))
            .then(Commands.literal("revoke")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerRevoke)))));
    }

    // ── resource change ───────────────────────────────────────────────────

    private static int executeResourceChange(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        String key = power.toString();

        int count = 0;
        for (ServerPlayer player : players) {
            var state = player.getData(CompatAttachments.resourceState());
            var meta = CompatAttachments.getResourceMeta(key);
            int min = meta != null ? meta.min() : 0;
            int max = meta != null ? meta.max() : Integer.MAX_VALUE;
            state.clampedAdd(key, amount, min, max);
            CompatAttachments.syncResourcesToClient(player);
            count++;
        }
        return count;
    }

    private static int executeResourceSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        String key = power.toString();

        int count = 0;
        for (ServerPlayer player : players) {
            var state = player.getData(CompatAttachments.resourceState());
            state.set(key, amount);
            CompatAttachments.syncResourcesToClient(player);
            count++;
        }
        return count;
    }

    private static int executeResourceGet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        String key = power.toString();

        int total = 0;
        for (ServerPlayer player : players) {
            var state = player.getData(CompatAttachments.resourceState());
            int value = state.get(key, 0);
            total += value;
        }
        return total;
    }

    // ── power grant / revoke ──────────────────────────────────────────────
    //
    // Origins-mod's /power command targets entities (including non-players
    // for mob powers). On NeoOrigins only players have origin data, so we
    // silently skip non-player targets rather than erroring.

    private static int executePowerGrant(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var entities = EntityArgument.getEntities(ctx, "targets");
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");

        int count = 0;
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) {
                var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                if (data.hasDynamicGrant(power)) continue;
                var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(power);
                if (holder != null && data.addDynamicGrant(power)) {
                    holder.onGranted(player);
                }
                count++;
            }
            // Non-player entities: Origins-mod applies powers to mobs via
            // Apoli's entity power system. NeoOrigins doesn't have a mob
            // power system, so these are silently ignored. The commands
            // still succeed (return count > 0) for mcfunction flow.
        }
        return Math.max(count, 1);
    }

    private static int executePowerRevoke(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var entities = EntityArgument.getEntities(ctx, "targets");
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");

        int count = 0;
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) {
                var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                if (data.removeDynamicGrant(power)) {
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(power);
                    if (holder != null) holder.onRevoked(player);
                }
                count++;
            }
        }
        return Math.max(count, 1);
    }

    // ── CommandEvent listener — legacy command rewriting ───────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        if (!LegacyCommandRewriter.needsRewrite(input)) return;

        String rewritten = LegacyCommandRewriter.rewrite(input);
        if (rewritten.equals(input)) return;

        // Re-parse the rewritten command through the dispatcher
        var dispatcher = event.getParseResults().getContext().getDispatcher();
        var source = event.getParseResults().getContext().getSource();
        event.setParseResults(dispatcher.parse(rewritten, source));
    }
}
