package com.cyberday1.neoorigins.compat.irons_spellbooks;

import com.cyberday1.neoorigins.api.resource.ResourceBacking;
import net.minecraft.server.level.ServerPlayer;

/**
 * The built-in {@code irons_spellbooks:mana} backing. Additive-only: an absolute set is
 * refused. {@link IronsSpellsBridge} is reached only after {@link #available()} passes, so
 * it is never class-loaded without Iron's Spells installed.
 */
public final class IronsManaBacking implements ResourceBacking {

    @Override
    public boolean available() {
        return net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks");
    }

    @Override
    public int read(ServerPlayer player) {
        return (int) IronsSpellsBridge.getMana(player);
    }

    @Override
    public void add(ServerPlayer player, int delta) {
        IronsSpellsBridge.addMana(player, (float) delta);
    }

    @Override
    public int max(ServerPlayer player) {
        return (int) IronsSpellsBridge.getMaxMana(player);
    }
}
