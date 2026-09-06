package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.service.EventPowerIndex;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Alternate spellings for the {@code event} key of
 * {@link com.cyberday1.neoorigins.power.builtin.ActionOnEventPower}.
 *
 * <p>The event-level counterpart of {@link com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases},
 * which does the same job one level up for power <em>type</em> ids. An unknown
 * event name is a hard {@code DataResult.error} in the codec, so before this
 * existed a pack that guessed a plausible name did not merely misbehave — the
 * whole power failed to load.
 *
 * <p><b>An alias may only be added when the target event already fires at the
 * same moment.</b> This is a spelling table, not a place to fake a capability:
 * pointing a name at an event that fires at a different time buys a power that
 * loads and then does the wrong thing, which is worse than the load error it
 * replaced. When no equivalent exists, add a real event instead — that is what
 * {@code MOD_FOOD_NUTRITION} is.
 *
 * <p>Aliases are also published in the {@code action_on_event} schema enum. If
 * they were accepted by the loader but withheld from the schema, the editors
 * would mark a pack invalid that the game loads perfectly well.
 */
public final class CompatEventAliases {

    private CompatEventAliases() {}

    /**
     * Alias → canonical event.
     *
     * <p>{@code item_use_start}: {@code ITEM_USE} is already dispatched from
     * {@code LivingEntityUseItemEvent.Start} for anything with a use duration
     * (see {@link CompatEventPowers#onItemUseStart}), so for a shield, bow or
     * food the two names denote the same instant. The only difference is that
     * {@code ITEM_USE} additionally covers instant-use items, which have no
     * "start" distinct from their use.
     */
    private static final Map<String, EventPowerIndex.Event> ALIASES = Map.of(
        "item_use_start", EventPowerIndex.Event.ITEM_USE
    );

    /** The canonical event for an alias, or {@code null} when not an alias. */
    public static EventPowerIndex.Event resolve(String name) {
        if (name == null) return null;
        return ALIASES.get(name.toLowerCase(Locale.ROOT));
    }

    /** Every alias spelling, for the schema enum and its parity test. */
    public static Set<String> names() {
        return ALIASES.keySet();
    }
}
