package com.cyberday1.neoorigins.power.schemaform;

import java.util.List;
import java.util.Map;

/**
 * Curated overlay of value sets for fields that are a fixed vocabulary in JSON
 * but appear as free {@code String} (or wrong-cased enum) to
 * {@link CodecFieldSpecExtractor}. Record reflection can't see a {@code String}
 * codec that only accepts {@code "mainhand"}/{@code "offhand"}/…, so the
 * creator would render a plain text box where a dropdown is correct.
 *
 * <p>Deliberately small and conservative: a wrong hint (offering values the
 * codec rejects) is worse than no hint, so only well-established vocabularies
 * are listed. Keyed {@code "<powerId>|<jsonField>"} for a power-specific
 * override, or {@code "*|<jsonField>"} for a field name that means the same
 * thing across powers. The long-term maintenance process is still open
 * (see the 2.1 plan); extend this table as concrete gaps surface in testing.
 */
public final class EnumHints {

    private static final List<String> EQUIPMENT_SLOTS =
        List.of("mainhand", "offhand", "head", "chest", "legs", "feet");

    /** JSON tokens the attribute-modifier codec maps to {@code AttributeModifier.Operation}. */
    private static final List<String> ATTRIBUTE_OPERATIONS =
        List.of("add_value", "add_multiplied_base", "add_multiplied_total");

    /**
     * JSON tokens {@code TickActionPower.ActionType.CODEC} accepts. The enum's
     * own constant names are UPPERCASE ({@code TELEPORT_ON_DAMAGE}/{@code NONE}),
     * but its {@code xmap} serializes/parses the LOWERCASE form, so the reflection
     * path (which would surface the raw constant names) must be pinned to these.
     */
    private static final List<String> TICK_ACTION_TYPES =
        List.of("teleport_on_damage", "none");

    private static final Map<String, List<String>> HINTS = Map.of(
        "*|slot", EQUIPMENT_SLOTS,
        "*|equipment_slot", EQUIPMENT_SLOTS,
        "*|operation", ATTRIBUTE_OPERATIONS,
        "neoorigins:attribute_modifier|operation", ATTRIBUTE_OPERATIONS,
        "*|action_type", TICK_ACTION_TYPES
    );

    private EnumHints() {}

    /**
     * Curated value set for {@code (powerId, jsonField)}, or an empty list when
     * none. A power-specific entry wins over the {@code *} wildcard.
     */
    public static List<String> valuesFor(String powerId, String jsonField) {
        List<String> specific = HINTS.get(powerId + "|" + jsonField);
        if (specific != null) return specific;
        List<String> wildcard = HINTS.get("*|" + jsonField);
        return wildcard != null ? wildcard : List.of();
    }
}
