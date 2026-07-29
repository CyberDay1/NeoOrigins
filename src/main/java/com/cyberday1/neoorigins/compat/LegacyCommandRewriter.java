package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites 1.20-era Minecraft command syntax to 1.21+ equivalents.
 * Handles attribute name changes, legacy entity data paths, item NBT and
 * particle arguments as used by legacy Origins/Apoli mcfunctions.
 *
 * <p>Called from three places:
 * <ul>
 *   <li>{@code ActionParser.parseExecuteCommand()} — for inline commands in power JSON</li>
 *   <li>{@code OriginsCompatCommands.onCommand()} — CommandEvent for mcfunction commands</li>
 *   <li>{@link LegacyFolderPackResources} — every line of every {@code .mcfunction}
 *       served out of a compat pack, at pack-read time</li>
 * </ul>
 *
 * <p>The rules split into two tiers, and the split matters:
 * <ul>
 *   <li>Rules 0-4 are <em>semantic</em> repairs. They only ever run behind a
 *       failed parse (see {@code OriginsCompatCommands#onCommand}), because they
 *       are heuristics that can mis-fire on text that was already fine — rule 1
 *       used to rewrite any {@code generic.} it could see, which was
 *       catastrophic for a sound id like
 *       {@code minecraft:entity.generic.extinguish_fire}; it is now anchored to
 *       attribute contexts, but the tier split still holds.</li>
 *   <li>Rules 5 and 6 are <em>syntactic</em> repairs for forms 1.21 deleted
 *       outright. Those stop a function <em>compiling</em>, so a function
 *       carrying them never reaches the CommandEvent hook at all and nothing but
 *       the resource layer can save it.</li>
 * </ul>
 *
 * <p>So the resource layer calls {@link #rewriteForCompile} — tier two only,
 * applied unconditionally — and never the full chain. Both tier-two rules stay
 * registry-free, because pack-read time is long before {@code RegistryAccess}
 * exists.
 */
public final class LegacyCommandRewriter {

    private LegacyCommandRewriter() {}

    // ── Attribute name migration ──────────────────────────────────────────
    //
    // 26.x dropped the "generic." prefix from most attribute IDs:
    //   minecraft:generic.max_health       → minecraft:max_health
    //   minecraft:generic.movement_speed   → minecraft:movement_speed
    //   minecraft:generic.attack_damage    → minecraft:attack_damage
    //   minecraft:generic.armor            → minecraft:armor
    //   etc.
    //
    // Verified against the decompiled 26.x Attributes class, which registers the
    // bare names. The 1.21.1 branch deliberately carries NO rule 1: 1.21.1 still
    // registers "generic.armor" / "generic.max_health", because the prefix drop
    // landed in 1.21.2. Do not port this rule back to it.
    //
    // The rewrite is ANCHORED to the places an attribute id can legally sit. It
    // used to be a bare `(minecraft:)?generic\.(\w+)` swept over the whole line,
    // which also ate
    //   playsound minecraft:entity.generic.extinguish_fire …
    //     → playsound minecraft:entity.minecraft:extinguish_fire
    // and any `generic.` sitting in tellraw/say prose.
    //
    // Also in NBT paths: Attributes[{Name:"generic.X"}] → Attributes[{id:"minecraft:X"}]

    /**
     * The attribute-id argument of {@code /attribute <target> <id> …}. The verb
     * is matched anywhere on the line rather than only at its start, so
     * {@code execute … run attribute …} is covered by the same pattern. The
     * target alternation tries a bracketed selector first, because
     * {@code @e[type=x, tag=y]} carries spaces that {@code \S+} would stop at.
     */
    private static final Pattern ATTRIBUTE_CMD_ID = Pattern.compile(
        "\\battribute\\s+(@[a-zA-Z]\\[[^\\]]*\\]|\\S+)\\s+(?:minecraft:)?generic\\.(\\w+)");

    /**
     * An attribute id written into NBT: the {@code AttributeName} of a 1.20
     * modifier compound, and the {@code id}/{@code Name} of an entry in an
     * entity's attribute list. The <em>value</em> has to be {@code generic.}-
     * prefixed to match, so a modifier whose display {@code Name} is ordinary
     * prose is left alone.
     */
    private static final Pattern ATTRIBUTE_NBT_ID = Pattern.compile(
        "\\b(AttributeName|Name|id)(\\s*:\\s*)\"(?:minecraft:)?generic\\.(\\w+)\"");

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
     * Matches a leading {@code origin} verb so we can rewrite it to
     * {@code neoorigins}. We do this so existing mcfunctions / chat habits that
     * use {@code /origin set @p ...} keep working transparently after the
     * {@code /origin} command-tree alias was retired (v2.1.0).
     *
     * <p>The rewrite only fires when the original parse fails (see
     * {@link com.cyberday1.neoorigins.command.OriginsCompatCommands#onCommand}),
     * so if anyone else (Origins-mod itself, another mod) registers {@code /origin}
     * we don't clobber their dispatch.
     */
    private static final Pattern LEADING_ORIGIN_VERB = Pattern.compile("^origin\\s");

    /**
     * Anchors the {@code {snbt}} blob of a {@code give <target> <item>{...}}.
     * Only the opening brace is matched; the closing one is found by balanced
     * scan, because SNBT nests and may contain braces inside quoted strings.
     */
    private static final Pattern GIVE_ITEM_NBT = Pattern.compile(
        "\\bgive\\s+\\S+\\s+((?:[a-z0-9_.\\-]+:)?[a-z0-9_.\\-]+)\\{");

    /** Same, for {@code item replace|modify ... with <item>{...}}. */
    private static final Pattern ITEM_WITH_NBT = Pattern.compile(
        "\\bwith\\s+((?:[a-z0-9_.\\-]+:)?[a-z0-9_.\\-]+)\\{");

    /** Guards {@link #ITEM_WITH_NBT} so a stray "with " elsewhere can't fire it. */
    private static final Pattern ITEM_SUBCOMMAND = Pattern.compile("\\bitem\\s+(replace|modify)\\b");

    /**
     * Anchors the {@code {snbt}} blob of a {@code clear <targets> <item>{...}}.
     * Same legacy shape as {@link #GIVE_ITEM_NBT} but a different destination:
     * {@code clear} takes an {@code ItemPredicateArgument}, not an item stack, so
     * the blob becomes a {@code [minecraft:custom_data~{...}]} sub-predicate
     * rather than a component patch. See {@link #customDataPredicate}.
     */
    private static final Pattern CLEAR_ITEM_NBT = Pattern.compile(
        "\\bclear\\s+\\S+\\s+((?:[a-z0-9_.\\-]+:)?[a-z0-9_.\\-]+)\\{");

    /**
     * {@code particle <block-ish type> <block id[state]> ...} — the 1.20
     * positional form. The type must be followed by whitespace, so a command
     * already carrying inline {@code {...}} options can never match.
     */
    private static final Pattern PARTICLE_BLOCK_ARG = Pattern.compile(
        "\\bparticle\\s+((?:minecraft:)?(?:block_marker|block|falling_dust|dust_pillar))"
            + "\\s+((?:[a-z0-9_.\\-]+:)?[a-z][a-z0-9_.\\-]*)(\\[[^\\]]*\\])?(?=\\s|$)");

    /** {@code particle <item type> <item id> ...} — same shape, item argument. */
    private static final Pattern PARTICLE_ITEM_ARG = Pattern.compile(
        "\\bparticle\\s+((?:minecraft:)?item)"
            + "\\s+((?:[a-z0-9_.\\-]+:)?[a-z][a-z0-9_.\\-]*)(?=\\s|$)");

    /**
     * One 1.20 positional float. Pack authors write leading-dot forms
     * ({@code .3}, {@code .7}) freely, and 1.20's {@code StringReader.readFloat}
     * took them, so the pattern has to as well.
     */
    private static final String LEGACY_FLOAT = "([-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))";

    /**
     * {@code particle <dust> <r> <g> <b> <scale> ...} — the 1.20 positional form
     * of {@link net.minecraft.core.particles.DustParticleOptions}. The type must
     * be followed by whitespace, so neither an already-inline
     * {@code dust{color:…}} nor the unrelated {@code dust_pillar} /
     * {@code dust_color_transition} ids can match here.
     */
    private static final Pattern PARTICLE_DUST_ARG = Pattern.compile(
        "\\bparticle\\s+((?:minecraft:)?dust)" + ("\\s+" + LEGACY_FLOAT).repeat(4) + "(?=\\s|$)");

    /**
     * {@code particle <dust_color_transition> <r> <g> <b> <scale> <r2> <g2> <b2> ...}
     * — note the scale sits <em>between</em> the two colours in the 1.20 order,
     * because 1.20's deserializer read colour, then size, then the second colour.
     */
    private static final Pattern PARTICLE_DUST_TRANSITION_ARG = Pattern.compile(
        "\\bparticle\\s+((?:minecraft:)?dust_color_transition)"
            + ("\\s+" + LEGACY_FLOAT).repeat(7) + "(?=\\s|$)");

    /**
     * {@code particle <entity_effect> <x> <y> <z> ...} — 1.20's
     * {@code entity_effect} was a {@code SimpleParticleType} with no arguments at
     * all (its colour rode in on the packet's velocity fields); 1.21 moved it to
     * {@link net.minecraft.core.particles.ColorParticleOption}, whose codec
     * <em>requires</em> a {@code color} key. There is therefore no positional
     * argument to carry over — see {@link #ENTITY_EFFECT_COLOR}.
     */
    private static final Pattern PARTICLE_ENTITY_EFFECT = Pattern.compile(
        "\\bparticle\\s+((?:minecraft:)?entity_effect)(?=\\s|$)");

    /**
     * The colour we hand a legacy {@code entity_effect}. This one is a judgement
     * call, not a translation: the 1.20 line carries no colour, because 1.20 took
     * it from the particle's velocity — which the command only controls when
     * {@code count} is 0, and every legacy line in the wild passes a count. So
     * the old behaviour was already "whatever the gaussian velocity happened to
     * be", i.e. near-black noise. Opaque white keeps the function loading and
     * keeps the effect visible; nothing about the original is recoverable.
     *
     * <p>26.x reads this through {@code ExtraCodecs.ARGB_COLOR_CODEC}, whose
     * {@code VECTOR4F} alternative still takes the 1.21.1 four-float list — so
     * the emitted text is identical on both sides.
     */
    private static final String ENTITY_EFFECT_COLOR = "{color:[1.0f,1.0f,1.0f,1.0f]}";

    /**
     * Rewrite a command string from 1.20 to 1.21 syntax.
     * Returns the original string if no changes are needed.
     */
    public static String rewrite(String command) {
        if (command == null || command.isEmpty()) return command;

        String result = command;

        // 0. Leading `origin` verb: rewrite to `neoorigins` so retired-alias
        //    mcfunctions keep working. See LEADING_ORIGIN_VERB.
        result = LEADING_ORIGIN_VERB.matcher(result).replaceFirst("neoorigins ");

        // 1. Attribute name: minecraft:generic.X → minecraft:X, but ONLY where an
        //    attribute id can legally sit — the id argument of `attribute`
        //    (including under `execute … run attribute …`) and the NBT fields
        //    that hold one — and never inside a quoted string.
        result = replaceOutsideStrings(result, ATTRIBUTE_CMD_ID,
            mr -> "attribute " + mr.group(1) + " minecraft:" + mr.group(2));
        result = replaceOutsideStrings(result, ATTRIBUTE_NBT_ID,
            mr -> mr.group(1) + mr.group(2) + "\"minecraft:" + mr.group(3) + "\"");

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

        return rewriteForCompile(result);
    }

    /**
     * Apply only the rules that fix syntax 1.21 <em>deleted</em>, i.e. the forms
     * that make a function fail to compile rather than fail to run.
     *
     * <p>This is what the resource layer runs over every {@code .mcfunction} line
     * it serves, unconditionally. It is deliberately not {@link #rewrite}: the
     * semantic rules above are heuristics that assume the command is already
     * broken, and firing them at read time rewrites lines that were working —
     * {@code playsound minecraft:entity.generic.extinguish_fire} is a real
     * casualty from a pack in the wild. Everything below is anchored on a command
     * verb plus a shape that cannot parse in 1.21 at all, so it has nothing valid
     * to break.
     */
    public static String rewriteForCompile(String command) {
        if (command == null || command.isEmpty()) return command;

        String result = command;

        // 5. Item NBT → data components.
        //    1.20:  give @s minecraft:warped_fungus{display:{Name:'…'},Enchantments:[…]} 1
        //    1.21:  give @s minecraft:warped_fungus[minecraft:custom_name=…,…] 1
        //    1.21 deleted the trailing-{tag} item form outright, so the old
        //    syntax is a *parse* error ("trailing data at position N") rather
        //    than a semantic one — the function never compiles. The mapping
        //    itself lives in LegacyTagToComponents so the ItemStack path and
        //    this string path cannot drift.
        result = rewriteItemNbt(result, GIVE_ITEM_NBT, LegacyTagToComponents::toComponentString);
        if (ITEM_SUBCOMMAND.matcher(result).find()) {
            result = rewriteItemNbt(result, ITEM_WITH_NBT, LegacyTagToComponents::toComponentString);
        }

        // 5b. The same legacy blob after `clear`, which is an *item predicate*.
        //     1.20:  clear @s minecraft:potion{ChemistCraftResult:1b} 1
        //     1.21:  clear @s minecraft:potion[minecraft:custom_data~{ChemistCraftResult:1b}] 1
        //     `clear` takes ItemPredicateArgument, so the components form rule 5
        //     emits would be a *test for equality* on each component rather than
        //     the subset match the pack meant. 1.21's `<predicate>~<snbt>` term
        //     is the subset match, and legacy free-form item NBT lands in
        //     custom_data — so that pairing is the faithful translation.
        result = rewriteItemNbt(result, CLEAR_ITEM_NBT, LegacyCommandRewriter::customDataPredicate);

        // 6. Positional particle arguments → inline options.
        //    1.20:  particle minecraft:block minecraft:twisting_vines ~ ~ ~ …
        //    1.21:  particle minecraft:block{block_state:"minecraft:twisting_vines"} ~ ~ ~ …
        //    1.21 moved every parameterized particle onto a codec read from an
        //    SNBT compound directly after the type id; the trailing positional
        //    argument is gone, so a 1.20 line fails with
        //    "No key block_state in MapLike[{}]". BlockParticleOption's codec
        //    takes either a bare block id or the {Name,Properties} state map
        //    (Codec.withAlternative) on 26.x exactly as on 1.21.1;
        //    ItemParticleOption moved to ItemStackTemplate.CODEC, which keeps a
        //    bare-item-id alternative, so the emitted text is unchanged.
        result = PARTICLE_BLOCK_ARG.matcher(result).replaceAll(mr ->
            "particle " + mr.group(1) + "{block_state:" + blockStateSnbt(mr.group(2), mr.group(3)) + "}");
        result = PARTICLE_ITEM_ARG.matcher(result).replaceAll(mr ->
            "particle " + mr.group(1) + "{item:\"" + mr.group(2) + "\"}");

        // 6b. The dust family, whose positional arguments were numbers rather
        //     than ids: 1.21 reads them off the same inline SNBT compound.
        //       1.20:  particle minecraft:dust .3 .3 1 1 ~ ~ ~ …
        //       1.21:  particle minecraft:dust{color:[0.3f,0.3f,1.0f],scale:1.0f} ~ ~ ~ …
        //     26.x moved the colour field from ExtraCodecs.VECTOR3F to
        //     ExtraCodecs.RGB_COLOR_CODEC — a packed int — but that is
        //     `Codec.withAlternative(Codec.INT, VECTOR3F, …)`, so the three-float
        //     list still decodes and the emitted text stays identical to 1.21.1's.
        //     Emitting through snbtFloat() is not cosmetic: NBT lists are
        //     homogeneous, so the verbatim [0,0.8,1] of a legacy line would be a
        //     mixed int/double list that TagParser rejects outright.
        result = PARTICLE_DUST_TRANSITION_ARG.matcher(result).replaceAll(mr ->
            "particle " + mr.group(1)
                + "{from_color:" + colorSnbt(mr.group(2), mr.group(3), mr.group(4))
                + ",to_color:" + colorSnbt(mr.group(6), mr.group(7), mr.group(8))
                + ",scale:" + scaleSnbt(mr.group(5)) + "}");
        result = PARTICLE_DUST_ARG.matcher(result).replaceAll(mr ->
            "particle " + mr.group(1)
                + "{color:" + colorSnbt(mr.group(2), mr.group(3), mr.group(4))
                + ",scale:" + scaleSnbt(mr.group(5)) + "}");

        // 6c. entity_effect gained a required `color` with nothing to fill it
        //     from. See ENTITY_EFFECT_COLOR for why the value is invented.
        result = PARTICLE_ENTITY_EFFECT.matcher(result).replaceAll(mr ->
            "particle " + mr.group(1) + ENTITY_EFFECT_COLOR);

        return result;
    }

    /** The three colour components as the float list 1.21/26.x both still accept. */
    private static String colorSnbt(String r, String g, String b) {
        return "[" + snbtFloat(r) + "," + snbtFloat(g) + "," + snbtFloat(b) + "]";
    }

    /**
     * The dust scale. {@code ScalableParticleOptionsBase} clamps to [0.01, 4.0]
     * in its constructor but its codec <em>validates</em> the same range first,
     * so an out-of-range legacy size would fail to parse instead of clamping.
     * Clamping here reproduces what 1.21 would have done had the codec let it.
     */
    private static String scaleSnbt(String raw) {
        return snbtFloat(Math.max(0.01F, Math.min(4.0F, Float.parseFloat(raw))));
    }

    /**
     * Canonicalise a legacy positional float into explicit SNBT float syntax.
     * {@code TagParser} does accept the leading-dot form ({@code .3}), but it
     * would type it as a double — and a list mixing those with bare-integer
     * components is a parse error, so every component has to be emitted with the
     * same explicit {@code f} suffix.
     */
    private static String snbtFloat(String raw) {
        return snbtFloat(Float.parseFloat(raw));
    }

    private static String snbtFloat(float value) {
        return value + "f";
    }

    /**
     * Render a legacy item-NBT blob as the {@code clear} item predicate that
     * means the same thing, or {@code null} to leave the command alone.
     *
     * <p>Only free-form keys convert. Anything {@link LegacyTagToComponents}
     * recognises ({@code Enchantments}, {@code Potion}, {@code display}, …) moved
     * to a real component on 1.21 and would <em>not</em> be found under
     * custom_data, so a blob carrying one of those is left as it was rather than
     * rewritten into a predicate that silently matches nothing.
     */
    @Nullable
    private static String customDataPredicate(CompoundTag tag) {
        if (tag.isEmpty()) return null;
        for (String key : tag.keySet()) {
            if (LegacyTagToComponents.recognisedKeys().contains(key)) {
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyCommandRewriter: clear predicate '{}' carries component-backed key '{}' — left as-is",
                    tag, key);
                return null;
            }
        }
        return "[minecraft:custom_data~" + tag + "]";
    }

    /**
     * Emit the {@code block_state} value for a legacy positional block argument.
     * A bare id rides through as a string (the codec's byNameCodec alternative);
     * an id carrying {@code [prop=value]} becomes the full state map, whose
     * Properties values are strings on both sides of 1.21.
     */
    private static String blockStateSnbt(String id, String properties) {
        if (properties == null || properties.length() <= 2) return "\"" + id + "\"";
        StringBuilder props = new StringBuilder();
        for (String pair : properties.substring(1, properties.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            if (props.length() > 0) props.append(',');
            props.append(pair, 0, eq).append(":\"").append(pair.substring(eq + 1).trim()).append('"');
        }
        if (props.length() == 0) return "\"" + id + "\"";
        return "{Name:\"" + id + "\",Properties:{" + props + "}}";
    }

    /**
     * Replace every {@code <item>{snbt}} anchored by {@code anchor} with whatever
     * {@code renderer} makes of the parsed blob — the components patch for an
     * item stack, the predicate term for {@code clear}. Scans for the matching
     * close brace rather than regexing it, so nested compounds and braces inside
     * quoted strings survive. A blob that will not parse, or that the renderer
     * declines by returning {@code null}, is left exactly as it was — a wrong
     * rewrite is worse than the original error.
     */
    private static String rewriteItemNbt(String command, Pattern anchor,
                                         Function<CompoundTag, String> renderer) {
        Matcher m = anchor.matcher(command);
        StringBuilder out = null;
        int cursor = 0;   // how much of `command` has been copied into `out`
        int from = 0;     // where the next anchor search starts
        while (from <= command.length() && m.find(from)) {
            int open = m.end() - 1; // the '{' the anchor ended on
            int close = matchingBrace(command, open);
            if (close < 0) {
                from = open + 1;
                continue;
            }
            String snbt = command.substring(open, close + 1);
            CompoundTag parsed;
            try {
                // 26.1: TagParser.parseTag is gone; use parseCompoundFully.
                parsed = TagParser.parseCompoundFully(snbt);
            } catch (Exception e) {
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyCommandRewriter: unparseable item SNBT '{}' — left as-is", snbt);
                from = close + 1;
                continue;
            }
            String rendered = renderer.apply(parsed);
            if (rendered == null) {
                from = close + 1;
                continue;
            }
            if (out == null) out = new StringBuilder();
            out.append(command, cursor, open).append(rendered);
            cursor = close + 1;
            from = cursor;
        }
        return out == null ? command : out.append(command.substring(cursor)).toString();
    }

    /**
     * Apply {@code pattern} everywhere it matches <em>outside</em> a quoted
     * string. A command line carries pack prose as well as syntax —
     * {@code tellraw} components, {@code say} text, item display names — and
     * editing that text is corruption rather than repair, so a match whose start
     * sits inside single or double quotes is skipped.
     */
    private static String replaceOutsideStrings(String s, Pattern pattern,
                                                Function<MatchResult, String> replacer) {
        Matcher m = pattern.matcher(s);
        if (!m.find()) return s;
        boolean[] quoted = quotedMask(s);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        do {
            if (quoted[m.start()]) continue;
            out.append(s, cursor, m.start()).append(replacer.apply(m));
            cursor = m.end();
        } while (m.find());
        return out.append(s, cursor, s.length()).toString();
    }

    /**
     * Per-character "this is inside a quoted string" flags. Both quote
     * characters open a string, because SNBT uses either and legacy packs nest
     * JSON in {@code '…'} freely; a backslash escapes the next character so an
     * embedded {@code \"} does not close the run early.
     */
    private static boolean[] quotedMask(String s) {
        boolean[] mask = new boolean[s.length()];
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                mask[i] = true;
                if (c == '\\') {
                    if (i + 1 < s.length()) mask[i + 1] = true;
                    i++;
                } else if (c == quote) {
                    inString = false;
                }
            } else if (c == '"' || c == '\'') {
                mask[i] = true;
                inString = true;
                quote = c;
            }
        }
        return mask;
    }

    /** Index of the brace closing the one at {@code open}, or -1 if unbalanced. */
    private static int matchingBrace(String s, int open) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == quote) inString = false;
            } else if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (--depth == 0) return i;
            }
        }
        return -1;
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
               command.contains("modifier remove ") ||
               command.startsWith("origin ") ||
               GIVE_ITEM_NBT.matcher(command).find() ||
               CLEAR_ITEM_NBT.matcher(command).find() ||
               PARTICLE_BLOCK_ARG.matcher(command).find() ||
               PARTICLE_ITEM_ARG.matcher(command).find() ||
               PARTICLE_DUST_ARG.matcher(command).find() ||
               PARTICLE_DUST_TRANSITION_ARG.matcher(command).find() ||
               PARTICLE_ENTITY_EFFECT.matcher(command).find();
    }
}
