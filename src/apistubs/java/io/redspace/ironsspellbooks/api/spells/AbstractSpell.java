package io.redspace.ironsspellbooks.api.spells;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Compile-only API stub for Iron's Spells 'n Spellbooks' {@code AbstractSpell}.
 *
 * <p>This is NOT Iron's Spells' real class — it is a minimal signature stand-in
 * carrying ONLY the members {@code IronsSpellsBridge} references, with the EXACT
 * descriptors of the real API (verified via {@code javap} against
 * {@code irons_spellbooks-1.21.1-3.14.0.jar}) so the bridge's compiled bytecode
 * resolves against the real mod at runtime. Widening a parameter here would compile
 * but then throw {@code NoSuchMethodError} against the real mod, so keep these in
 * lockstep with the real {@code io.redspace.ironsspellbooks.api.spells.AbstractSpell}.
 *
 * <p>Lives in the {@code apiStubs} source set (main COMPILE classpath only, never on
 * the runtime classpath, never bundled). At runtime the real class loads when
 * {@code irons_spellbooks} is installed, gated behind
 * {@code ModList.isLoaded("irons_spellbooks")}.
 */
public abstract class AbstractSpell {

    public int getMaxLevel() {
        return 0;
    }

    public int getManaCost(int level) {
        return 0;
    }

    public abstract CastType getCastType();

    public void castSpell(Level level, int spellLevel, ServerPlayer player, CastSource source, boolean triggerCooldown) {
        throw new UnsupportedOperationException("Iron's Spells API stub — not for runtime use");
    }

    public boolean attemptInitiateCast(ItemStack itemStack, int spellLevel, Level level, Player player,
                                       CastSource source, boolean triggerCooldown, String equipmentSlot) {
        throw new UnsupportedOperationException("Iron's Spells API stub — not for runtime use");
    }
}
