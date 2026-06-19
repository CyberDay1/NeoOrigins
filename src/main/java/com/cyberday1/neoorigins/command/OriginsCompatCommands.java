package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.LegacyCommandRewriter;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
            PowerDataManager.INSTANCE.getAllPowers().keySet(), builder);

    /**
     * Register the Origins-mod compat commands. Called from
     * {@link OriginCommand#register}.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Mirrors Apoli's /resource command (apace100/apoli ResourceCommand):
        //   has       <target> <resource>                              -> 1/0 conditional
        //   get       <target> <resource>                              -> current value
        //   set       <target> <resource> <value>                      -> new value
        //   change    <target> <resource> <value>                      -> new value
        //   operation <target> <resource> <op> <source> <objective>    -> new value
        // Single-target like Apoli; <resource> must already be granted to the
        // target (a power applied it), otherwise the command fails the way
        // Apoli's POWER_NOT_GRANTED does. Return values mirror vanilla
        // /scoreboard so /execute store result works.
        dispatcher.register(Commands.literal("resource")
            .requires(Commands.hasPermission(new net.minecraft.server.permissions.PermissionCheck.Require(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)))
            .then(Commands.literal("has")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", IdentifierArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .executes(OriginsCompatCommands::executeResourceHas))))
            .then(Commands.literal("get")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", IdentifierArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .executes(OriginsCompatCommands::executeResourceGet))))
            .then(Commands.literal("set")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", IdentifierArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                            .executes(OriginsCompatCommands::executeResourceSet)))))
            .then(Commands.literal("change")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", IdentifierArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                            .executes(OriginsCompatCommands::executeResourceChange)))))
            .then(Commands.literal("operation")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", IdentifierArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .then(Commands.argument("operation", StringArgumentType.word())
                            .suggests(SUGGEST_OPERATIONS)
                            .then(Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                .then(Commands.argument("objective", ObjectiveArgument.objective())
                                    .executes(OriginsCompatCommands::executeResourceOperation))))))));

        // /power grant <target> <power>
        // /power revoke <target> <power>
        dispatcher.register(Commands.literal("power")
            .requires(Commands.hasPermission(new net.minecraft.server.permissions.PermissionCheck.Require(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)))
            .then(Commands.literal("grant")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", IdentifierArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerGrant))))
            .then(Commands.literal("revoke")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", IdentifierArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerRevoke)))));
    }

    // ── /resource (Apoli parity) ───────────────────────────────────────────

    /** Thrown when the target player doesn't have the named resource granted. */
    private static final com.mojang.brigadier.exceptions.DynamicCommandExceptionType RESOURCE_NOT_PRESENT =
        new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(res ->
            net.minecraft.network.chat.Component.literal("That player has no resource '" + res + "'"));

    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType ERROR_DIVIDE_BY_ZERO =
        new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
            net.minecraft.network.chat.Component.literal("Cannot divide by zero"));

    private static final com.mojang.brigadier.exceptions.DynamicCommandExceptionType ERROR_INVALID_OPERATION =
        new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(op ->
            net.minecraft.network.chat.Component.literal("Invalid operation '" + op + "'"));

    /** Suggest the scoreboard-style operators Apoli's /resource operation accepts. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_OPERATIONS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            java.util.List.of("=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><"), builder);

    /** Suggest only the resources the resolved target currently has (Apoli parity). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TARGET_RESOURCES =
        (ctx, builder) -> {
            try {
                ServerPlayer p = EntityArgument.getPlayer(ctx, "target");
                return SharedSuggestionProvider.suggest(
                    p.getData(CompatAttachments.resourceState()).getAll().keySet(), builder);
            } catch (Exception ignored) {
                return SharedSuggestionProvider.suggestResource(
                    PowerDataManager.INSTANCE.getAllPowers().keySet(), builder);
            }
        };

    /** Resolve the resource key for a player, failing like Apoli if it isn't granted. */
    private static String requireResource(ServerPlayer player, Identifier resource)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String key = resource.toString();
        if (!player.getData(CompatAttachments.resourceState()).has(key)) {
            throw RESOURCE_NOT_PRESENT.create(key);
        }
        return key;
    }

    /** Clamp a value into the resource's registered [min, max] (no-op if meta unknown). */
    private static int clampToMeta(String key, int value) {
        var meta = CompatAttachments.getResourceMeta(key);
        if (meta == null) return value;
        return Math.max(meta.min(), Math.min(meta.max(), value));
    }

    private static int executeResourceHas(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        Identifier resource = IdentifierArgument.getId(ctx, "resource");
        boolean has = player.getData(CompatAttachments.resourceState()).has(resource.toString());
        if (has) {
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.execute.conditional.pass"), false);
            return 1;
        }
        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable(
            "commands.execute.conditional.fail"));
        return 0;
    }

    private static int executeResourceGet(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        Identifier resource = IdentifierArgument.getId(ctx, "resource");
        String key = requireResource(player, resource);
        int value = player.getData(CompatAttachments.resourceState()).get(key, 0);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
            "commands.scoreboard.players.get.success", player.getDisplayName(), value, key), false);
        return value;
    }

    private static int executeResourceSet(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        Identifier resource = IdentifierArgument.getId(ctx, "resource");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        String key = requireResource(player, resource);
        int newValue = clampToMeta(key, value);
        player.getData(CompatAttachments.resourceState()).set(key, newValue);
        CompatAttachments.syncResourceValuesToClient(player);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
            "commands.scoreboard.players.set.success.single", key, player.getDisplayName(), newValue), true);
        return newValue;
    }

    private static int executeResourceChange(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        Identifier resource = IdentifierArgument.getId(ctx, "resource");
        int delta = IntegerArgumentType.getInteger(ctx, "value");
        String key = requireResource(player, resource);
        var state = player.getData(CompatAttachments.resourceState());
        var meta = CompatAttachments.getResourceMeta(key);
        int min = meta != null ? meta.min() : 0;
        int max = meta != null ? meta.max() : Integer.MAX_VALUE;
        state.clampedAdd(key, delta, min, max);
        int newValue = state.get(key, 0);
        CompatAttachments.syncResourceValuesToClient(player);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
            "commands.scoreboard.players.add.success.single", delta, key, player.getDisplayName(), newValue), true);
        return newValue;
    }

    private static int executeResourceOperation(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        Identifier resource = IdentifierArgument.getId(ctx, "resource");
        String op = StringArgumentType.getString(ctx, "operation");
        net.minecraft.world.scores.ScoreHolder sourceHolder = ScoreHolderArgument.getName(ctx, "source");
        net.minecraft.world.scores.Objective objective = ObjectiveArgument.getObjective(ctx, "objective");
        String key = requireResource(player, resource);

        var state = player.getData(CompatAttachments.resourceState());
        net.minecraft.world.scores.Scoreboard scoreboard = ctx.getSource().getServer().getScoreboard();
        net.minecraft.world.scores.ScoreAccess sourceScore = scoreboard.getOrCreatePlayerScore(sourceHolder, objective);

        int cur = state.get(key, 0);
        int src = sourceScore.get();
        int result;
        if (op.equals("><")) {
            // swap: resource takes the source score, source score takes the old resource value
            result = src;
            sourceScore.set(cur);
        } else {
            result = applyOperation(op, cur, src);
        }
        int newValue = clampToMeta(key, result);
        state.set(key, newValue);
        CompatAttachments.syncResourceValuesToClient(player);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
            "commands.scoreboard.players.operation.success.single", key, player.getDisplayName(), newValue), true);
        return newValue;
    }

    private static int applyOperation(String op, int cur, int src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        switch (op) {
            case "=":  return src;
            case "+=": return cur + src;
            case "-=": return cur - src;
            case "*=": return cur * src;
            case "/=":
                if (src == 0) throw ERROR_DIVIDE_BY_ZERO.create();
                return Math.floorDiv(cur, src);
            case "%=":
                if (src == 0) throw ERROR_DIVIDE_BY_ZERO.create();
                return Math.floorMod(cur, src);
            case "<":  return Math.min(cur, src);
            case ">":  return Math.max(cur, src);
            default:   throw ERROR_INVALID_OPERATION.create(op);
        }
    }

    // ── power grant / revoke ──────────────────────────────────────────────
    //
    // Origins-mod's /power command targets entities (including non-players
    // for mob powers). On NeoOrigins only players have origin data, so we
    // silently skip non-player targets rather than erroring.

    private static int executePowerGrant(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var entities = EntityArgument.getEntities(ctx, "targets");
        Identifier power = IdentifierArgument.getId(ctx, "power");
        List<Identifier> targets = resolvePowerTargets(power);

        int count = 0;
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) {
                var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                for (Identifier id : targets) {
                    if (data.hasDynamicGrant(id)) continue;
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(id);
                    if (holder != null && data.addDynamicGrant(id)) {
                        holder.onGranted(player);
                    }
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
        Identifier power = IdentifierArgument.getId(ctx, "power");
        List<Identifier> targets = resolvePowerTargets(power);

        int count = 0;
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) {
                var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                for (Identifier id : targets) {
                    if (data.removeDynamicGrant(id)) {
                        var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(id);
                        if (holder != null) holder.onRevoked(player);
                    }
                }
                count++;
            }
        }
        return Math.max(count, 1);
    }

    /**
     * Resolves a {@code /power grant|revoke} target id to the concrete
     * registered power holders it should act on.
     *
     * <p>A {@code multiple} power is never itself a registered holder —
     * {@link OriginsMultipleExpander} records it only in
     * {@link OriginsMultipleExpander#MULTIPLE_EXPANSION_MAP} and registers its
     * synthetic sub-powers as the real holders. So {@code getPower(parent)}
     * returns {@code null} and granting/revoking the parent id silently
     * no-ops. Expand it to its sub-power ids — recursively, since a
     * {@code multiple} may nest other {@code multiple}s — down to the leaf
     * holders. A non-{@code multiple} id resolves to itself (unchanged
     * single-power behavior).
     */
    private static List<Identifier> resolvePowerTargets(Identifier power) {
        var map = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP;
        if (!map.containsKey(power)) return List.of(power);

        List<Identifier> leaves = new ArrayList<>();
        Set<Identifier> visited = new HashSet<>();
        ArrayDeque<Identifier> pending = new ArrayDeque<>(map.get(power));
        while (!pending.isEmpty()) {
            Identifier id = pending.pop();
            if (!visited.add(id)) continue;          // cycle / diamond guard
            List<Identifier> children = map.get(id);
            if (children != null) {
                pending.addAll(children);            // nested multiple — expand further
            } else {
                leaves.add(id);                       // real registered holder
            }
        }
        return leaves;
    }

    // ── CommandEvent listener — legacy command rewriting ───────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        var original = event.getParseResults();
        String input = original.getReader().getString();
        if (!LegacyCommandRewriter.needsRewrite(input)) return;

        // Never touch a command that already parses to an executable command.
        // The rewriter exists only to repair genuinely legacy Origins++
        // mcfunction syntax that vanilla can't resolve; rewriting valid
        // commands (e.g. a player's `/attribute @p minecraft:armor ...`)
        // corrupted vanilla attribute commands for the whole pack — see
        // GitHub #92.
        if (parsesCleanly(original)) return;

        String rewritten = LegacyCommandRewriter.rewrite(input);
        if (rewritten.equals(input)) return;

        var dispatcher = original.getContext().getDispatcher();
        var source = original.getContext().getSource();
        var reparsed = dispatcher.parse(rewritten, source);

        // Only substitute when the rewrite actually yields a valid command.
        // If it still fails, leave the original so vanilla reports its own
        // error rather than a confusing rewritten one.
        if (parsesCleanly(reparsed)) {
            event.setParseResults(reparsed);
        }
    }

    /** True when the parse resolved to a complete, executable command. */
    private static boolean parsesCleanly(
            com.mojang.brigadier.ParseResults<net.minecraft.commands.CommandSourceStack> pr) {
        return pr.getExceptions().isEmpty()
            && pr.getContext().getCommand() != null
            && !pr.getReader().canRead();
    }
}
