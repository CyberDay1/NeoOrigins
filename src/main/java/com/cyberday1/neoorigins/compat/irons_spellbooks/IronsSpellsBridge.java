package com.cyberday1.neoorigins.compat.irons_spellbooks;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.action.EntityAction;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;

import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
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
            Identifier spellRl;
            try {
                spellRl = Identifier.parse(spellId);
            } catch (IdentifierException e) {
                NeoOrigins.LOGGER.warn(
                    "[Iron's Spells] cast_iron_spell in '{}' has a malformed spell id '{}' — power does nothing",
                    contextId, spellId);
                return;
            }

            AbstractSpell spell = SpellRegistry.getSpell(spellRl);
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

    // ── neoorigins:resource `backing: irons_spellbooks:mana` support ─────────
    // A native resource power can declare its value be backed by the player's
    // Iron's mana pool. These two helpers are the ONLY place that touches the
    // MagicData mana API; callers reach them by FQN behind the
    // ModList.isLoaded("irons_spellbooks") gate, exactly like castSpell. The
    // mana pool stays AUTHORITATIVE: reads observe it, writes ADD a delta — we
    // never call setMana (an absolute overwrite would fight Iron's own regen /
    // cast bookkeeping).

    /**
     * Current mana for the player, or {@code 0} when no MagicData exists yet
     * (e.g. very early in the join handshake). Cast float→int by the caller for
     * the integer resource value / bar.
     */
    public static float getMana(Player player) {
        MagicData md = MagicData.getPlayerMagicData(player);
        return md != null ? md.getMana() : 0f;
    }

    /**
     * The player's LIVE maximum mana. Iron's max mana is not a static number: it
     * is the {@code irons_spellbooks:max_mana} ATTRIBUTE, so it moves with gear,
     * level, and effects. Iron's own mana bar ({@code ManaBarOverlay}) reads it as
     * {@code player.getAttributeValue(AttributeRegistry.MAX_MANA)} then truncates
     * to int — we match that (double→float here; the caller int-truncates for the
     * bar denominator), so a mana-backed {@code neoorigins:resource} bar auto-scales
     * to exactly what Iron's shows.
     *
     * <p>The MAX_MANA attribute is registered on players by Iron's, so
     * {@code getAttributeValue} resolves whenever Iron's is loaded; there is no
     * MagicData dependency for the max (unlike {@link #getMana}). If the attribute
     * is somehow absent, vanilla {@code getAttributeValue} throws rather than
     * returning a default, which would only happen with a broken Iron's install —
     * the router keeps this behind the {@code ModList.isLoaded} gate + warn-once and
     * falls back to the author max, so a genuinely broken state degrades gracefully.
     *
     * @return the player's current max mana, cast to float
     */
    public static float getMaxMana(Player player) {
        return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
    }

    /**
     * Add {@code delta} mana (negative to spend/drain). Iron's own
     * {@code addMana} clamps only the UPPER bound (against the MAX_MANA
     * attribute) — verified against irons_spellbooks-1.21.1-3.14.0, where
     * {@code addMana(f)} is literally {@code setMana(mana + f)} and
     * {@code setMana} has no lower clamp. So a raw negative delta could drive
     * mana below 0. We compute a floor-safe negative delta here from
     * {@code getMana()} alone (never letting the result go below 0) and hand
     * that to Iron's, leaving the upper clamp to Iron's. Positive deltas pass
     * through untouched so Iron's caps them at max.
     *
     * @return the delta actually applied (may be smaller in magnitude than the
     *         requested drain when the pool would have gone negative)
     */
    public static float addMana(Player player, float delta) {
        MagicData md = MagicData.getPlayerMagicData(player);
        if (md == null) return 0f;
        float applied = delta;
        if (delta < 0f) {
            float cur = md.getMana();
            // Clamp the drain so cur + delta >= 0 (i.e. delta >= -cur).
            if (-delta > cur) applied = -cur;
        }
        if (applied != 0f) md.addMana(applied);
        return applied;
    }
}
