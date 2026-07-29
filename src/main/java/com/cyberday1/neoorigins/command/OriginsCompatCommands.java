package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.LegacyCommandRewriter;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.resources.ResourceLocation;
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
            .requires(cs -> cs.hasPermission(2))
            .then(Commands.literal("has")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", ResourceLocationArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .executes(OriginsCompatCommands::executeResourceHas))))
            .then(Commands.literal("get")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("resource", ResourceLocationArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .executes(OriginsCompatCommands::executeResourceGet))))
            .then(Commands.literal("set")
                .then(Commands.argument("target", EntityArgument.players())
                    .then(Commands.argument("resource", ResourceLocationArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                            .executes(OriginsCompatCommands::executeResourceSet)))))
            .then(Commands.literal("change")
                .then(Commands.argument("target", EntityArgument.players())
                    .then(Commands.argument("resource", ResourceLocationArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                            .executes(OriginsCompatCommands::executeResourceChange)))))
            .then(Commands.literal("operation")
                .then(Commands.argument("target", EntityArgument.players())
                    .then(Commands.argument("resource", ResourceLocationArgument.id())
                        .suggests(SUGGEST_TARGET_RESOURCES)
                        .then(Commands.argument("operation", StringArgumentType.word())
                            .suggests(SUGGEST_OPERATIONS)
                            .then(Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                .then(Commands.argument("objective", ObjectiveArgument.objective())
                                    .executes(OriginsCompatCommands::executeResourceOperation))))))));

        // /power grant  <targets> <power> [<source>]
        // /power revoke <targets> <power> [<source>]
        // /power remove <targets> <power> [<source>]  (alias for revoke — see below)
        // /power has    <target>  <power>             -> 1/0 conditional
        //
        // Apoli's real /power grant|revoke syntax takes an OPTIONAL trailing
        // <source> power — the power that granted this one, used by Apoli for
        // multi-source reference counting (a power stays until every source
        // revokes it). NeoOrigins tracks grants as a flat set, so we accept the
        // source token and ignore it. But the node must EXIST: without it,
        // Brigadier fails to parse a line like
        //   power grant @e[..] flowerman:wind_hold flowerman:mysterious_wind
        // and the whole mcfunction silently never loads (same trap as the
        // /power remove alias and the /scale shim). Seen in flowerman:mysterious_wind.
        dispatcher.register(Commands.literal("power")
            .requires(cs -> cs.hasPermission(2))
            .then(Commands.literal("grant")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerGrant)
                        .then(Commands.argument("source", ResourceLocationArgument.id())
                            .suggests(SUGGEST_POWERS)
                            .executes(OriginsCompatCommands::executePowerGrant)))))
            .then(Commands.literal("revoke")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerRevoke)
                        .then(Commands.argument("source", ResourceLocationArgument.id())
                            .suggests(SUGGEST_POWERS)
                            .executes(OriginsCompatCommands::executePowerRevoke)))))
            // `remove` alias for `revoke`. Some community packs (e.g. "wou") call
            // `/power remove`, which was never a real Apoli/Origins subcommand —
            // the real verb is `revoke`. Without this literal Brigadier fails to
            // parse the whole mcfunction at datapack load, so every OTHER line in
            // that function silently never runs (same failure mode as the /scale
            // shim below). Accept `remove` and route it to the revoke handler.
            .then(Commands.literal("remove")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerRevoke)
                        .then(Commands.argument("source", ResourceLocationArgument.id())
                            .suggests(SUGGEST_POWERS)
                            .executes(OriginsCompatCommands::executePowerRevoke)))))
            // `has` — Apoli's conditional. Single target and no <source>, which
            // is Apoli's own shape. Origins++ calls it from
            // origins-plus-plus:witch-of-ink/brush/{red,green}; those two
            // functions never loaded at all, because an unregistered command
            // fails the whole .mcfunction at compile time.
            .then(Commands.literal("has")
                .then(Commands.argument("target", EntityArgument.entity())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .suggests(SUGGEST_POWERS)
                        .executes(OriginsCompatCommands::executePowerHas)))));

        // /scale shim (Pehkui parity) — ONLY when Pehkui itself is absent.
        //
        // Origins/Apoli packs that depend on Pehkui (e.g. the "seer" astral
        // origin) call `scale set pehkui:flight 0.75` from their init
        // mcfunctions. Without a registered `/scale` command, Brigadier fails
        // to PARSE the entire function at datapack-load time, so EVERY other
        // line in that function silently never runs — including the actual
        // teleport into the pack's pocket dimension. (NeoOrigins' PehkuiBridge
        // is API-only via reflection and never registers the command.)
        //
        // We register `scale` with a greedy-string tail so ANY argument grammar
        // parses regardless of Pehkui's exact Brigadier tree; the executor then
        // parses `<sub> <type> <value> [targets]` loosely and does best-effort
        // application. Correctness of e.g. pehkui:flight is NOT required — the
        // point is that the function loads and its OTHER commands run.
        //
        // Guarded on !isLoaded("pehkui"): if Pehkui is present it registers its
        // own real /scale and a second literal would be a Brigadier conflict.
        if (!net.neoforged.fml.ModList.get().isLoaded("pehkui")) {
            dispatcher.register(Commands.literal("scale")
                .requires(cs -> cs.hasPermission(2))
                .executes(OriginsCompatCommands::executeScaleBare)
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(OriginsCompatCommands::executeScale)));
        }
    }

    // ── /scale (Pehkui shim — see register() for rationale) ────────────────

    /** Bare {@code /scale} with no args: succeed silently so functions parse. */
    private static int executeScaleBare(CommandContext<CommandSourceStack> ctx) {
        return 1;
    }

    /**
     * Best-effort {@code /scale <sub> <type> <value> [targets]} shim.
     *
     * <p>Loose by design: we only need to (a) make the command parse so the
     * containing mcfunction loads, and (b) apply the scale when we can map it.
     * Unrecognised forms still return success so the function keeps running.
     */
    private static int executeScale(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "args").trim();
        String[] parts = raw.split("\\s+");
        // Recognised vanilla-mappable form: `set|reset <type> [value] [targets]`.
        // pehkui:base/width/height → vanilla minecraft:scale attribute (mirrors
        // SizeScalingPower). Other types (e.g. pehkui:flight) have no vanilla
        // equivalent → clean no-op that still reports success.
        try {
            if (parts.length >= 3) {
                String sub = parts[0];
                String type = parts[1];
                float value;
                try {
                    value = Float.parseFloat(parts[2]);
                } catch (NumberFormatException nfe) {
                    return 1; // unparseable value — still succeed so the function continues
                }
                if (("set".equals(sub) || "add".equals(sub) || "reset".equals(sub))
                        && isVanillaScalableType(type)) {
                    applyVanillaScale(ctx.getSource(), value);
                }
            }
        } catch (Exception ignored) {
            // Never let the shim break the function's execution flow.
        }
        return 1;
    }

    /** Pehkui scale types that map cleanly onto the vanilla {@code minecraft:scale} attribute. */
    private static boolean isVanillaScalableType(String type) {
        return "pehkui:base".equals(type)
            || "pehkui:width".equals(type)
            || "pehkui:height".equals(type)
            || "pehkui:model_width".equals(type)
            || "pehkui:model_height".equals(type);
    }

    /**
     * Apply a body scale to the command source's executing player via the
     * vanilla {@code minecraft:scale} attribute (a transient compat modifier)
     * and best-effort mirror to Pehkui's BASE (no-ops when Pehkui is absent —
     * which it is whenever this shim is registered, but harmless either way).
     */
    private static void applyVanillaScale(CommandSourceStack source, float value) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (attr != null) {
            ResourceLocation modId =
                ResourceLocation.fromNamespaceAndPath("neoorigins", "compat_scale_shim");
            attr.removeModifier(modId);
            // scale attribute uses ADD_VALUE on a base of 1.0, so delta = value - 1.0
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                modId, value - 1.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        com.cyberday1.neoorigins.compat.pehkui.PehkuiBridge.applyOriginScale(player, value);
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
    private static String requireResource(ServerPlayer player, ResourceLocation resource)
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
        ResourceLocation resource = ResourceLocationArgument.getId(ctx, "resource");
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
        ResourceLocation resource = ResourceLocationArgument.getId(ctx, "resource");
        String key = requireResource(player, resource);
        int value = player.getData(CompatAttachments.resourceState()).get(key, 0);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
            "commands.scoreboard.players.get.success", player.getDisplayName(), value, key), false);
        return value;
    }

    // set / change / operation take MULTIPLE targets (Apoli allows e.g.
    // `resource change @a[team=fm_recruits] flowerman:count -1`). Players that
    // don't currently have the resource are skipped rather than erroring, so a
    // multi-target call never aborts the enclosing mcfunction (fail-open, same
    // spirit as the /power and /scale shims). Return the last affected value,
    // or 0 if no target had the resource.
    private static int executeResourceSet(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "target");
        ResourceLocation resource = ResourceLocationArgument.getId(ctx, "resource");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        String key = resource.toString();
        int last = 0;
        int affected = 0;
        for (ServerPlayer player : players) {
            var state = player.getData(CompatAttachments.resourceState());
            if (!state.has(key)) continue;
            int newValue = clampToMeta(key, value);
            state.set(key, newValue);
            CompatAttachments.syncResourceValuesToClient(player);
            last = newValue;
            affected++;
            final int fv = newValue;
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.scoreboard.players.set.success.single", key, player.getDisplayName(), fv), true);
        }
        return affected == 0 ? 0 : last;
    }

    private static int executeResourceChange(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "target");
        ResourceLocation resource = ResourceLocationArgument.getId(ctx, "resource");
        int delta = IntegerArgumentType.getInteger(ctx, "value");
        String key = resource.toString();
        int last = 0;
        int affected = 0;
        for (ServerPlayer player : players) {
            var state = player.getData(CompatAttachments.resourceState());
            if (!state.has(key)) continue;
            var meta = CompatAttachments.getResourceMeta(key);
            int min = meta != null ? meta.min() : 0;
            int max = meta != null ? meta.max() : Integer.MAX_VALUE;
            state.clampedAdd(key, delta, min, max);
            int newValue = state.get(key, 0);
            CompatAttachments.syncResourceValuesToClient(player);
            last = newValue;
            affected++;
            final int fv = newValue;
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.scoreboard.players.add.success.single", delta, key, player.getDisplayName(), fv), true);
        }
        return affected == 0 ? 0 : last;
    }

    private static int executeResourceOperation(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "target");
        ResourceLocation resource = ResourceLocationArgument.getId(ctx, "resource");
        String op = StringArgumentType.getString(ctx, "operation");
        net.minecraft.world.scores.ScoreHolder sourceHolder = ScoreHolderArgument.getName(ctx, "source");
        net.minecraft.world.scores.Objective objective = ObjectiveArgument.getObjective(ctx, "objective");
        String key = resource.toString();

        net.minecraft.world.scores.Scoreboard scoreboard = ctx.getSource().getServer().getScoreboard();
        net.minecraft.world.scores.ScoreAccess sourceScore = scoreboard.getOrCreatePlayerScore(sourceHolder, objective);

        int last = 0;
        int affected = 0;
        for (ServerPlayer player : players) {
            var state = player.getData(CompatAttachments.resourceState());
            if (!state.has(key)) continue;
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
            last = newValue;
            affected++;
            final int fv = newValue;
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.scoreboard.players.operation.success.single", key, player.getDisplayName(), fv), true);
        }
        return affected == 0 ? 0 : last;
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
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        List<ResourceLocation> targets = resolvePowerTargets(power);

        int count = 0;
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) {
                var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                boolean changed = false;
                for (ResourceLocation id : targets) {
                    if (data.hasDynamicGrant(id)) continue;
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(id);
                    if (holder == null) continue;
                    // Origin-inherent powers: record the dynamic flag but don't
                    // re-fire onGranted (the origin already applied it).
                    if (isFromOrigin(data, id)) {
                        if (data.addDynamicGrant(id)) changed = true;
                        continue;
                    }
                    if (data.addDynamicGrant(id)) {
                        holder.onGranted(player);
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new com.cyberday1.neoorigins.api.event.PowerGrantedEvent(player, id));
                        changed = true;
                    }
                }
                if (changed) {
                    com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
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
        List<ResourceLocation> targets = resolvePowerTargets(power);

        int count = 0;
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) {
                var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                boolean changed = false;
                for (ResourceLocation id : targets) {
                    if (!data.hasDynamicGrant(id)) continue;
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(id);
                    if (data.removeDynamicGrant(id) && holder != null) {
                        // Only tear the power down if it isn't still provided by
                        // the player's origin — otherwise revoking the dynamic
                        // grant would strip an origin-inherent power's effects.
                        if (!isFromOrigin(data, id)) {
                            holder.onRevoked(player);
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                new com.cyberday1.neoorigins.api.event.PowerRevokedEvent(player, id));
                        }
                        changed = true;
                    }
                }
                if (changed) {
                    com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
                }
                count++;
            }
        }
        return Math.max(count, 1);
    }

    /**
     * {@code /power has <target> <power>} — Apoli's conditional subcommand.
     *
     * <p>Returns 1/0 rather than a count, so both {@code execute if} and
     * {@code execute store result} read it the way Apoli's does — Origins++
     * uses the {@code store result} form. The check covers every way NeoOrigins
     * can hold a power (origin-inherent, dynamic grant, global power set) and
     * expands a {@code multiple} parent through {@link #resolvePowerTargets}
     * exactly as grant/revoke do, so asking about the parent id answers about
     * its synthetic sub-powers too.
     *
     * <p>A non-player target is answered 0 rather than errored, matching how
     * grant/revoke silently skip non-players: NeoOrigins has no mob power system.
     */
    private static int executePowerHas(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var entity = EntityArgument.getEntity(ctx, "target");
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");

        boolean has = false;
        if (entity instanceof ServerPlayer player) {
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            List<ResourceLocation> candidates = new ArrayList<>();
            candidates.add(power);                       // the id as written
            candidates.addAll(resolvePowerTargets(power)); // and its leaves, if `multiple`
            for (ResourceLocation id : candidates) {
                if (data.hasDynamicGrant(id) || data.hasGlobalGrant(id) || isFromOrigin(data, id)) {
                    has = true;
                    break;
                }
            }
        }

        if (has) {
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.execute.conditional.pass"), false);
            return 1;
        }
        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable(
            "commands.execute.conditional.fail"));
        return 0;
    }

    /**
     * Whether {@code id} is granted by one of the player's current origins
     * (as opposed to a purely dynamic grant). Mirrors the origin-source check
     * used by the {@code grant_power}/{@code revoke_power} actions so command
     * and action paths agree on origin-inherent vs dynamic-grant semantics.
     */
    private static boolean isFromOrigin(
            com.cyberday1.neoorigins.attachment.PlayerOriginData data, ResourceLocation id) {
        for (var entry : data.getOrigins().entrySet()) {
            var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin != null && origin.powers().contains(id)) return true;
        }
        return false;
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
    private static List<ResourceLocation> resolvePowerTargets(ResourceLocation power) {
        var map = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP;
        if (!map.containsKey(power)) return List.of(power);

        List<ResourceLocation> leaves = new ArrayList<>();
        Set<ResourceLocation> visited = new HashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>(map.get(power));
        while (!pending.isEmpty()) {
            ResourceLocation id = pending.pop();
            if (!visited.add(id)) continue;          // cycle / diamond guard
            List<ResourceLocation> children = map.get(id);
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

    /**
     * The GitHub #92 gate, exposed for callers that hold a raw command string
     * rather than a {@link CommandEvent}: true when {@code command} already
     * parses to a complete, executable command against {@code source}.
     *
     * <p>Rewriting one of those is what broke vanilla attribute commands
     * pack-wide, so every site that feeds pack-authored text to
     * {@link LegacyCommandRewriter#rewrite} has to check this first — see
     * {@code BuiltinActions}' {@code execute_command}.
     *
     * <p>Leading {@code /} is stripped because that is what
     * {@code Commands#performPrefixedCommand} does before dispatch, and the
     * string tested here must be the one that would actually be run.
     */
    public static boolean parsesCleanly(CommandSourceStack source, String command) {
        if (source.getServer() == null) return false;
        String stripped = command.startsWith("/") ? command.substring(1) : command;
        try {
            return parsesCleanly(source.getServer().getCommands().getDispatcher()
                .parse(stripped, source));
        } catch (Exception e) {
            return false;
        }
    }
}
