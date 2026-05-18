package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * JSON-preview / raw-edit tab — live serialized draft + escape hatch. Wired to
 * the shared Phase 2 serializer in Phase 4 (kept a stub here to respect the
 * phase boundary; the serializer does not exist yet).
 */
public final class JsonPreviewTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.json");

    @Override public Component title() { return TITLE; }

    @Override public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {}

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        StubText.draw(g, TITLE, x, y, w, h);
    }
}
