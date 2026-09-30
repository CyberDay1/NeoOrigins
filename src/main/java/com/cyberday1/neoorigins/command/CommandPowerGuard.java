package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.LegacyCommandRewriter;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security guard for commands run on behalf of datapack powers.
 *
 * <p>The {@code command}/{@code execute_command} actions, the raycast
 * {@code command_along_ray} / {@code command_at_hit} extensions, and the
 * {@code command} condition all execute pack-supplied command strings at
 * permission level 2. That is a privilege-escalation vector: a pack could ship
 * {@code /op @s}. Every such call site routes through {@link #isBlocked} first
 * and skips execution when the command's effective root is on the
 * {@link com.cyberday1.neoorigins.config.AdminConfig#COMMAND_POWER_BLACKLIST}.
 * The same sites call {@link #checkParses} so a command that cannot parse is logged.
 */
// hub: neoorigins/pack-command-parse-warning.md
public final class CommandPowerGuard {

    /** Commands already seen to parse, so a per-tick condition parses once. */
    private static final Set<String> KNOWN_GOOD = ConcurrentHashMap.newKeySet();
    /** Power, site and command of each parse warning already logged. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CommandPowerGuard() {}

    /**
     * True if the command must not be run by a power. Checks the effective
     * command root against the server-config blacklist.
     */
    public static boolean isBlocked(String command) {
        return AdminConfig.isCommandPowerBlocked(extractRoot(command));
    }

    /** Emit a standardized warning when a power's command is refused. */
    public static void warnBlocked(String command, String contextId) {
        NeoOrigins.LOGGER.warn(
            "[security] refused blacklisted command power: root '{}'{} — full command was '{}'",
            extractRoot(command),
            contextId == null || contextId.isEmpty() ? "" : " [" + contextId + "]",
            command);
    }

    /**
     * True when {@code command} parses against {@code source}. When it does not,
     * logs it once per power and site, because the suppressed-output source every
     * power uses drops vanilla's own error. Set {@code viaCommandEvent} for sites
     * that run through {@code Commands#performPrefixedCommand}, whose CommandEvent
     * can still repair legacy syntax. Never stops the command from being run.
     */
    public static boolean checkParses(CommandSourceStack source, String command,
                                      String contextId, String site, boolean viaCommandEvent) {
        if (command == null || command.isBlank()) return true;
        String stripped = command.startsWith("/") ? command.substring(1) : command;
        if (KNOWN_GOOD.contains(stripped)) return true;
        String failure = OriginsCompatCommands.parseFailure(source, stripped);
        if (failure == null) {
            KNOWN_GOOD.add(stripped);
            return true;
        }
        // Not cached: this text is good only where the CommandEvent rewrite runs.
        if (viaCommandEvent && LegacyCommandRewriter.needsRewrite(stripped)
                && OriginsCompatCommands.parsesCleanly(source, LegacyCommandRewriter.rewrite(stripped))) {
            return true;
        }
        warnUnparseable(command, contextId, site, failure);
        return false;
    }

    /** Emit a standardized warning, once, for a power's command that does not parse. */
    public static void warnUnparseable(String command, String contextId, String site, String reason) {
        ResourceLocation dispatching = PowerHolder.currentDispatchId();
        String power = dispatching != null ? dispatching.toString() : contextId;
        String where = power == null || power.isEmpty() ? site : power + " " + site;
        if (!WARNED.add(where + " | " + command)) return;
        NeoOrigins.LOGGER.warn(
            "[command] power command does not parse and will not run [{}]: {} — full command was '{}'",
            where, reason, command);
    }

    /** Forget which commands parsed and which were reported; a reload can change both. */
    public static void clearParseMemory() {
        KNOWN_GOOD.clear();
        WARNED.clear();
    }

    /**
     * The effective command root: leading slash stripped, lowercased. When the
     * command is an {@code execute} chain, the token after the LAST {@code run}
     * is returned so a blacklisted command can't hide behind
     * {@code execute ... run <cmd>} (nesting included). An {@code execute} with
     * no {@code run} subcommand resolves to {@code "execute"} itself.
     */
    static String extractRoot(String command) {
        if (command == null) return null;
        String c = command.strip();
        if (c.startsWith("/")) c = c.substring(1).strip();
        if (c.isEmpty()) return null;
        String[] tokens = c.split("\\s+");
        if (tokens[0].equalsIgnoreCase("execute")) {
            int lastRun = -1;
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].equalsIgnoreCase("run")) lastRun = i;
            }
            if (lastRun >= 0 && lastRun + 1 < tokens.length) {
                return tokens[lastRun + 1].toLowerCase(Locale.ROOT);
            }
            return "execute";
        }
        return tokens[0].toLowerCase(Locale.ROOT);
    }
}
