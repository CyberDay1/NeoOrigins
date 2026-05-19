package com.cyberday1.neoorigins.screen.mobcreator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** One page of the {@link MobOriginCreatorScreen} — mob-side {@code CreatorTab}. */
public interface MobCreatorTab {
    Component title();
    default Component help() { return Component.empty(); }
    default void renderBackdrop(GuiGraphics g) {}
    void init(MobOriginCreatorScreen parent, int x, int y, int w, int h);
    void render(GuiGraphics g, int mouseX, int mouseY, float partial, int x, int y, int w, int h);
    default void pullFromDraft() {}
    default void pushToDraft() {}
    default boolean mouseScrolled(double mx, double my, double sx, double sy) { return false; }
}
