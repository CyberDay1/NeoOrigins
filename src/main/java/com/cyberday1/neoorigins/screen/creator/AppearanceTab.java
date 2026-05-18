package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Appearance tab — Tier-A visual powers + asset-path hints. Phase 4. */
public final class AppearanceTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.appearance");

    @Override public Component title() { return TITLE; }

    @Override public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {}

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        StubText.draw(g, TITLE, x, y, w, h);
    }
}
