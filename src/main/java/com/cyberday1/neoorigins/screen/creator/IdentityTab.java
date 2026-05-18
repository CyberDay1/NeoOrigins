package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Identity tab — name / description / icon / impact / order. Phase 4. */
public final class IdentityTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.identity");

    @Override public Component title() { return TITLE; }

    @Override public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        StubText.draw(g, TITLE, x, y, w, h);
    }
}
