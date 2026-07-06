package com.cyberday1.neoorigins.compat.irons_spellbooks;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.action.EntityAction;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The one and only class that references Iron's Spells 'n Spellbooks
 * ({@code irons_spellbooks}) types. Every symbol here resolves against the
 * compile-only {@code irons_spellbooks} API stub (see
 * {@code src/apistubs/java/io/redspace/ironsspellbooks}); the class is isolated
 * behind a {@code ModList.isLoaded("irons_spellbooks")} gate in
 * {@link com.cyberday1.neoorigins.compat.action.BuiltinActions} — it is never
 * class-loaded when Iron's Spells is absent, which keeps
 * {@code NoClassDefFoundError} off the table on servers without the mod.
 *
 * <p>The {@code neoorigins:cast_iron_spell} action delegates here. Unlike the
 * Build A Spell bridge (which builds a reusable POJO at parse time), the Iron's
 * spell is resolved fresh per dispatch from {@link SpellRegistry} — a spell id is
 * a lightweight registry lookup and the {@link AbstractSpell} instance is a shared
 * singleton, so there's nothing to cache.
 *
 * <p>Mana handling: origin-routed casts default to {@code consumeMana = false}
 * (cost is charged on the NeoOrigins power — resource / cooldown), so the Iron's
 * mana pool is untouched and the cast uses {@link CastSource#NONE}. When the author
 * opts in with {@code consume_mana: true}, we cast via {@link CastSource#SPELLBOOK}
 * (which consumes mana + respects cooldown) and gate on the player's mana ourselves,
 * because {@link AbstractSpell#castSpell} clamps mana to {@code >= 0} rather than
 * refusing an under-funded cast.
 */
public final class IronsSpellsBridge {

    private IronsSpellsBridge() {}

    /**
     * Build the cast action. Called at parse time (datapack load), already behind
     * the {@code ModList.isLoaded("irons_spellbooks")} gate. The returned action
     * resolves + casts the spell each time it fires.
     *
     * @param spellId         the {@code irons_spellbooks:<name>} spell id
     * @param level           requested spell level (clamped to the spell's max)
     * @param consumeMana     charge the player's Iron's mana pool + gate on it
     * @param triggerCooldown apply the spell's cooldown after casting
     * @param mode            {@code "instant"} (direct one-shot) or {@code "channel"}
     *                        (full animated/channeled cast)
     * @param contextId       the owning power id, for diagnostics
     */
    public static EntityAction castSpell(String spellId, int level, boolean consumeMana,
                                         boolean triggerCooldown, String mode, String contextId) {
        return player -> {
            AbstractSpell spell = SpellRegistry.getSpell(ResourceLocation.parse(spellId));
            if (spell == null || spell == SpellRegistry.none()) {
                NeoOrigins.LOGGER.warn(
                    "[Iron's Spells] cast_iron_spell in '{}' references unknown spell '{}' — power does nothing",
                    contextId, spellId);
                return;
            }

            int lvl = Math.max(1, Math.min(level, spell.getMaxLevel()));
            CastSource source = consumeMana ? CastSource.SPELLBOOK : CastSource.NONE;

            // Real mana gate: castSpell alone won't refuse an under-funded cast (it
            // just clamps mana to >= 0). Creative players bypass the check.
            if (consumeMana && !player.isCreative()) {
                MagicData md = MagicData.getPlayerMagicData(player);
                if (md != null && md.getMana() < spell.getManaCost(lvl)) {
                    NeoOrigins.LOGGER.warn(
                        "[Iron's Spells] cast_iron_spell in '{}': not enough mana for '{}' (need {}, have {}) — power does nothing",
                        contextId, spellId, spell.getManaCost(lvl), md.getMana());
                    return;
                }
            }

            if ("channel".equals(mode)) {
                spell.attemptInitiateCast(ItemStack.EMPTY, lvl, player.level(), player, source, triggerCooldown, "mainhand");
            } else {
                spell.castSpell(player.level(), lvl, player, source, triggerCooldown);
            }
        };
    }
}
