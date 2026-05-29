package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /neoorigins debug …} — developer harness for the action/condition
 * compat parsers. Feeds a raw verb JSON straight into {@link ActionParser} /
 * {@link ConditionParser} so a single verb can be exercised in isolation,
 * independent of whether any shipped power happens to use it.
 *
 * <p>This is the behavioral-baseline net for the registry-refactor migration
 * (Phase 1): the dispatch path these subcommands hit ({@code parse → execute /
 * test}) is exactly the code the switch→registry migration rewrites, so a
 * before/after {@code eval} on each risky verb confirms transcription parity.
 *
 * <ul>
 *   <li>{@code eval condition <json>} — parse + {@code test()} against the
 *       running player, print the boolean.</li>
 *   <li>{@code eval action <json>} — parse + {@code execute()} against the
 *       running player (side effects land on you), print confirmation.</li>
 *   <li>{@code dump-parse condition|action <json>} — structural report:
 *       canonicalized type, {@code KNOWN_TYPES} membership, and whether the
 *       parse threw. No side effects.</li>
 * </ul>
 *
 * <p>Permission 2 (operator) — {@code eval action} mutates the running player.
 */
public final class DebugCommand {

    private DebugCommand() {}

    private static final String CTX = "neoorigins:debug";

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
            .requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
            .then(Commands.literal("eval")
                .then(Commands.literal("condition")
                    .then(Commands.argument("json", StringArgumentType.greedyString())
                        .executes(DebugCommand::evalCondition)))
                .then(Commands.literal("action")
                    .then(Commands.argument("json", StringArgumentType.greedyString())
                        .executes(DebugCommand::evalAction))))
            .then(Commands.literal("dump-parse")
                .then(Commands.literal("condition")
                    .then(Commands.argument("json", StringArgumentType.greedyString())
                        .executes(ctx -> dumpParse(ctx, false))))
                .then(Commands.literal("action")
                    .then(Commands.argument("json", StringArgumentType.greedyString())
                        .executes(ctx -> dumpParse(ctx, true)))));
    }

    // ── eval ────────────────────────────────────────────────────────────────

    private static int evalCondition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        JsonObject json = parseJson(ctx);
        if (json == null) return 0;

        String type = canonicalType(json);
        EntityCondition cond = ConditionParser.parse(json, CTX);
        boolean result;
        try {
            result = cond.test(player);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(
                "condition " + type + " threw on test(): " + e));
            return 0;
        }
        final boolean r = result;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "condition " + type + " => " + r)
            .withStyle(r ? ChatFormatting.GREEN : ChatFormatting.RED)
            .append(Component.literal("  [known=" + ConditionParser.KNOWN_TYPES.contains(type) + "]")
                .withStyle(ChatFormatting.GRAY)), false);
        return r ? 1 : 0;
    }

    private static int evalAction(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        JsonObject json = parseJson(ctx);
        if (json == null) return 0;

        String type = canonicalType(json);
        EntityAction action = ActionParser.parse(json, CTX);
        try {
            action.execute(player);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(
                "action " + type + " threw on execute(): " + e));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            "action " + type + " executed")
            .withStyle(ChatFormatting.GREEN)
            .append(Component.literal("  [known=" + ActionParser.KNOWN_TYPES.contains(type) + "]")
                .withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    // ── dump-parse ────────────────────────────────────────────────────────────

    private static int dumpParse(CommandContext<CommandSourceStack> ctx, boolean isAction) {
        JsonObject json = parseJson(ctx);
        if (json == null) return 0;

        String type = canonicalType(json);
        boolean known = isAction
            ? ActionParser.KNOWN_TYPES.contains(type)
            : ConditionParser.KNOWN_TYPES.contains(type);

        boolean parsedOk;
        String parseNote;
        try {
            if (isAction) ActionParser.parse(json, CTX);
            else ConditionParser.parse(json, CTX);
            parsedOk = true;
            parseNote = "no throw";
        } catch (Exception e) {
            parsedOk = false;
            parseNote = String.valueOf(e);
        }

        final boolean ok = parsedOk;
        final String note = parseNote;
        ctx.getSource().sendSuccess(() -> Component.literal(
            (isAction ? "action" : "condition") + " dump-parse:\n")
            .append(Component.literal("  type:    " + type + "\n").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("  known:   " + known + "\n")
                .withStyle(known ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
            .append(Component.literal("  parse:   " + (ok ? "ok" : "FAILED") + " (" + note + ")")
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        return known ? 1 : 0;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Parse the greedy {@code json} arg into an object, reporting failures to the source. */
    private static JsonObject parseJson(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "json");
        try {
            var el = JsonParser.parseString(raw);
            if (!el.isJsonObject()) {
                ctx.getSource().sendFailure(Component.literal("expected a JSON object, got: " + raw));
                return null;
            }
            return el.getAsJsonObject();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("malformed JSON: " + e.getMessage()));
            return null;
        }
    }

    /**
     * Mirror the parsers' type canonicalization (bare → {@code neoorigins:};
     * legacy {@code origins:}/{@code apace:} → {@code neoorigins:}) so the
     * reported type matches the switch arm / registry key that dispatch uses.
     */
    private static String canonicalType(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            return "neoorigins:" + type;
        } else if (type.startsWith("origins:") || type.startsWith("apace:")) {
            return "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }
        return type;
    }
}
