package io.redspace.ironsspellbooks.spells;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;

/**
 * Compile-only API stub for Iron's Spells' {@code NoneSpell} — the sentinel
 * returned by {@code SpellRegistry.getSpell} / {@code SpellRegistry.none()} for
 * unknown ids. Present here ONLY so {@code SpellRegistry.none()} can keep its real
 * return descriptor ({@code io.redspace.ironsspellbooks.spells.NoneSpell}) exact,
 * which the bridge's {@code spell == SpellRegistry.none()} identity check relies on.
 * Never bundled; the real class loads at runtime when {@code irons_spellbooks} is
 * present.
 */
public class NoneSpell extends AbstractSpell {

    @Override
    public CastType getCastType() {
        return CastType.NONE;
    }
}
