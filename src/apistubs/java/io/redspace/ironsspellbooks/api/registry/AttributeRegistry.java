package io.redspace.ironsspellbooks.api.registry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Compile-only API stub for Iron's Spells' {@code AttributeRegistry}. Carries
 * ONLY the {@code MAX_MANA} holder the bridge reads, with the EXACT real
 * descriptor ({@code javap}-verified against irons_spellbooks-1.21.1-3.14.0):
 * {@code public static final DeferredHolder<Attribute, Attribute> MAX_MANA}
 * (field descriptor {@code Lnet/neoforged/neoforge/registries/DeferredHolder;}).
 *
 * <p>Iron's own mana bar ({@code ManaBarOverlay}) resolves the player's max mana
 * via {@code player.getAttributeValue(AttributeRegistry.MAX_MANA)} — this is the
 * authoritative dynamic value (it moves with gear / level / effects), so the
 * mana-backed {@code neoorigins:resource} bar reads the same holder to auto-scale.
 *
 * <p>Never bundled; the real class loads at runtime when {@code irons_spellbooks}
 * is present. The stub value is {@code null} because it is never dereferenced on
 * the stub classpath — the real holder is resolved at runtime behind the
 * {@code ModList.isLoaded("irons_spellbooks")} gate.
 */
public class AttributeRegistry {

    public static final DeferredHolder<Attribute, Attribute> MAX_MANA = null;
}
