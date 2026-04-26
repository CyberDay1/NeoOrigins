package net.minecraft.client.gui.spectator;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SpectatorMenuListener {
    void onSpectatorMenuClosed(SpectatorMenu p_101843_);
}
