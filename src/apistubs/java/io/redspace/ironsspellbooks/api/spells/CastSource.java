package io.redspace.ironsspellbooks.api.spells;

/**
 * Compile-only API stub for Iron's Spells' {@code CastSource} enum. Minimal
 * signature stand-in (see {@link AbstractSpell}); never bundled, real enum loads
 * at runtime when {@code irons_spellbooks} is present. Constants + method
 * descriptors mirror the real enum exactly ({@code javap}-verified against 3.14.0).
 */
public enum CastSource {
    SPELLBOOK,
    SCROLL,
    SWORD,
    MOB,
    COMMAND,
    NONE;

    public boolean consumesMana() {
        return false;
    }

    public boolean respectsCooldown() {
        return false;
    }
}
