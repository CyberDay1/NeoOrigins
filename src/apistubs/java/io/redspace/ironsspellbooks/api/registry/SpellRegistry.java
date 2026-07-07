package io.redspace.ironsspellbooks.api.registry;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.spells.NoneSpell;
import net.minecraft.resources.Identifier;

/**
 * Compile-only API stub for Iron's Spells' {@code SpellRegistry}. Minimal
 * signature stand-in (see {@link AbstractSpell}) carrying ONLY the lookups the
 * bridge uses, with the EXACT real descriptors ({@code javap}-verified against
 * 3.14.0) — note {@code none()} really returns {@code NoneSpell}, not
 * {@code AbstractSpell}. Never bundled; the real class loads at runtime when
 * {@code irons_spellbooks} is present.
 */
public final class SpellRegistry {

    private SpellRegistry() {}

    public static AbstractSpell getSpell(Identifier rl) {
        return null;
    }

    public static AbstractSpell getSpell(String id) {
        return null;
    }

    public static NoneSpell none() {
        return null;
    }
}
