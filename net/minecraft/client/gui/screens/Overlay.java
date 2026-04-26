package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.components.Renderable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class Overlay implements Renderable {
    public boolean isPauseScreen() {
        return true;
    }
}
