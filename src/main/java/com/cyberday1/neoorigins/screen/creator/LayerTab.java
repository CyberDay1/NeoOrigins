package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Layer tab — target layer / class-vs-origin selection. Phase 4. */
public final class LayerTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.layer");

    @Override public Component title() { return TITLE; }

    @Override public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        StubText.draw(g, TITLE, x, y, w, h);
    }
}
