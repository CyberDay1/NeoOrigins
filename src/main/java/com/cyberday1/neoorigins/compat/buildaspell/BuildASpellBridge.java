package com.cyberday1.neoorigins.compat.buildaspell;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import cyberday.cybersbuildaspell.api.BuildASpellAPI;
import cyberday.cybersbuildaspell.spell.Spell;

import java.util.List;

/**
 * The one and only class that references Build A Spell ({@code cybersbuildaspell})
 * types. Every symbol here resolves against the compile-only {@code cybersbuildaspell}
 * jar, so the class is isolated behind a {@code ModList.isLoaded("cybersbuildaspell")}
 * gate in {@link com.cyberday1.neoorigins.compat.action.BuiltinActions} — it is never
 * class-loaded when BaS is absent, which keeps {@code NoClassDefFoundError} off the
 * table on servers without the mod.
 *
 * <p>The {@code neoorigins:cast_spell} action delegates here. We build the
 * {@link Spell} once at datapack-load time via {@link BuildASpellAPI#createSpell}
 * (a {@code Spell} is a reusable, level-/thread-agnostic POJO per the BaS contract)
 * and capture it in the returned {@link EntityAction}, so each dispatch is just a
 * {@link BuildASpellAPI#cast} call. Origin-routed casts always pass
 * {@code consumeMana = false}: the BaS mana pool is never touched, because cost is
 * charged on the NeoOrigins power (resource / hunger / cooldown). Config
 * enable/disable toggles for the delivery and components are still honored by BaS.
 */
public final class BuildASpellBridge {

    private BuildASpellBridge() {}

    /**
     * Build the cast action for an inline author-baked spell. Called at parse time
     * (datapack load), already behind the {@code ModList.isLoaded} gate.
     *
     * <p>{@code createSpell} validates every id against the live delivery / effect /
     * modifier vocabularies ({@code "compat:"} ids pass through verbatim) and returns
     * {@code null} on the first bad id, naming it in the BaS log. We fail fast here:
     * a bad spell yields a no-op action plus a NeoOrigins-side warning that names the
     * power, so the author sees which power to fix rather than a silent dead ability.
     *
     * @param delivery   one of {@link BuildASpellAPI#deliveryIds()}
     * @param components the single EFFECT-FIRST ordered component list (effects, then
     *                   their modifiers; {@code "compat:*"} ids allowed)
     * @param contextId  the owning power id, for diagnostics
     */
    public static EntityAction castSpell(String delivery, List<String> components, String contextId) {
        Spell spell = BuildASpellAPI.createSpell(delivery, components);
        if (spell == null) {
            NeoOrigins.LOGGER.warn(
                "[BaS] cast_spell in '{}' built no spell (bad delivery '{}' or component id) — power does nothing",
                contextId, delivery);
            return EntityAction.noop();
        }
        return player -> BuildASpellAPI.cast(player, spell, false);
    }
}
