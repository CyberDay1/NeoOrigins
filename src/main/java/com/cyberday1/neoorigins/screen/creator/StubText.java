package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared placeholder body for the Phase 1 tab stubs — centred "&lt;tab&gt; tab
 * — implemented in a later 2.1 phase" text. Removed as each tab gets real
 * content in Phase 4.
 */
final class StubText {
    private StubText() {}

    static void draw(GuiGraphicsExtractor g, Component tabTitle, int x, int y, int w, int h) {
        var font = Minecraft.getInstance().font;
        Component msg = Component.translatable(
            "gui.neoorigins.creator.stub", tabTitle.getString());
        g.centeredText(font, msg, x + w / 2, y + h / 2 - 4, 0xFF8888AA);
    }
}
