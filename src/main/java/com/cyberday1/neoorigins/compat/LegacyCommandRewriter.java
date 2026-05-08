package com.cyberday1.neoorigins.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites 1.20-era Minecraft command syntax to 1.21+ equivalents.
 * Handles attribute name changes and legacy entity data paths used by
 * Origins++ mcfunctions.
 *
 * <p>Called from two places:
 * <ul>
 *   <li>{@code ActionParser.parseExecuteCommand()} — for inline commands in power JSON</li>
 *   <li>{@code OriginsCompatCommands.onCommand()} — CommandEvent for mcfunction commands</li>
 * </ul>
 */
public final class LegacyCommandRewriter {

    private LegacyCommandRewriter() {}

    // ── Attribute name migration ──────────────────────────────────────────
    //
    // 1.21 dropped the "generic." prefix from most attribute IDs:
    //   minecraft:generic.max_health       → minecraft:max_health
    //   minecraft:generic.movement_speed   → minecraft:movement_speed
    //   minecraft:generic.attack_damage    → minecraft:attack_damage
    //   minecraft:generic.armor            → minecraft:armor
    //   etc.
    //
    // Also in NBT paths: Attributes[{Name:"generic.X"}] → Attributes[{id:"minecraft:X"}]

    /** Matches minecraft:generic.xxx or generic.xxx attribute references. */
    private static final Pattern GENERIC_ATTR = Pattern.compile(
        "(minecraft:)?generic\\.(\\w+)");

    /** Matches Attributes[{Name:"xxx"}] NBT path selectors. */
    private static final Pattern ATTR_NAME_PATH = Pattern.compile(
        "Attributes\\[\\{Name:\"((?:minecraft:)?generic\\.(\\w+))\"\\}\\]");

    /** Matches Item.tag.xxx data paths (1.20 custom NBT → 1.21 custom_data component). */
    private static final Pattern ITEM_TAG_PATH = Pattern.compile(
        "Item\\.tag\\.(\\w+)");

    /** Matches legacy UUID format in attribute modifier commands: 1-1-1-1-1111 */
    private static final Pattern LEGACY_MODIFIER_UUID = Pattern.compile(
        "modifier (add|remove) ([0-9a-fA-F-]+)");

    /**
     * Rewrite a command string from 1.20 to 1.21 syntax.
     * Returns the original string if no changes are needed.
     */
    public static String rewrite(String command) {
        if (command == null || command.isEmpty()) return command;

        String result = command;

        // 1. Attribute name: minecraft:generic.X → minecraft:X
        result = GENERIC_ATTR.matcher(result).replaceAll("minecraft:$2");

        // 2. Attributes[{Name:"generic.X"}] → Attributes[{id:"minecraft:X"}]
        Matcher attrPathMatcher = ATTR_NAME_PATH.matcher(result);
        if (attrPathMatcher.find()) {
            result = attrPathMatcher.replaceAll(mr -> {
                String attrName = mr.group(2); // the xxx part
                return "Attributes[{id:\"minecraft:" + attrName + "\"}]";
            });
        }

        // 3. Item.tag.X → Item.components."minecraft:custom_data".X
        result = ITEM_TAG_PATH.matcher(result).replaceAll(
            "Item.components.\"minecraft:custom_data\".$1");

        // 4. Legacy modifier UUID format: attribute ... modifier add UUID-HERE name amount op
        //    → attribute ... modifier add neoorigins:compat_modifier amount op
        //    (1.21 uses ResourceLocation IDs instead of UUIDs for modifiers)
        result = LEGACY_MODIFIER_UUID.matcher(result).replaceAll(mr -> {
            String action = mr.group(1); // add or remove
            String uuid = mr.group(2);
            // Convert UUID to a deterministic RL so add/remove pairs match
            String safeId = "neoorigins:compat_" + uuid.replace("-", "_");
            return "modifier " + action + " " + safeId;
        });

        return result;
    }

    /**
     * Check if a command looks like it uses legacy 1.20 syntax.
     * Used to avoid rewrite overhead on modern commands.
     */
    public static boolean needsRewrite(String command) {
        return command.contains("generic.") ||
               command.contains("Item.tag.") ||
               command.contains("{Name:\"") ||
               command.contains("modifier add ") ||
               command.contains("modifier remove ");
    }
}
