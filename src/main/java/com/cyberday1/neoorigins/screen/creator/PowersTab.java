package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Powers tab — schema/codec hybrid form renderer + raw-JSON. Phase 4. */
public final class PowersTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.powers");

    @Override public Component title() { return TITLE; }

    @Override public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {}

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        StubText.draw(g, TITLE, x, y, w, h);
    }
}
