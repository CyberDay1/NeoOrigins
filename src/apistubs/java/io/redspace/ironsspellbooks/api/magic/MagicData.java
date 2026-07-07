package io.redspace.ironsspellbooks.api.magic;

import net.minecraft.world.entity.LivingEntity;

/**
 * Compile-only API stub for Iron's Spells' {@code MagicData} (per-player mana
 * pool). Minimal signature stand-in (see
 * {@link io.redspace.ironsspellbooks.api.spells.AbstractSpell}) carrying ONLY the
 * mana accessors the bridge uses, with the EXACT real descriptors
 * ({@code javap}-verified against 3.14.0). Never bundled; the real class loads at
 * runtime when {@code irons_spellbooks} is present.
 */
public class MagicData {

    public static MagicData getPlayerMagicData(LivingEntity entity) {
        return null;
    }

    public float getMana() {
        return 0f;
    }

    public void setMana(float mana) {
    }

    public void addMana(float mana) {
    }
}
