package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;

import java.util.Locale;

/**
 * Security guard for commands run on behalf of datapack powers.
 *
 * <p>The {@code command}/{@code execute_command} actions, the raycast
 * {@code command_along_ray} / {@code command_at_hit} extensions, and the
 * {@code command} condition all execute pack-supplied command strings at
 * permission level 2. That is a privilege-escalation vector: a pack could ship
 * {@code /op @s}. Every such call site routes through {@link #isBlocked} first
 * and skips execution when the command's effective root is on the
 * {@link NeoOriginsConfig#COMMAND_POWER_BLACKLIST}.
 */
public final class CommandPowerGuard {

    private CommandPowerGuard() {}

    /**
     * True if the command must not be run by a power. Checks the effective
     * command root against the server-config blacklist.
     */
    public static boolean isBlocked(String command) {
        return NeoOriginsConfig.isCommandPowerBlocked(extractRoot(command));
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
