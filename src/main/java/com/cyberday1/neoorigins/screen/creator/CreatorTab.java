package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One page of the {@link OriginCreatorScreen}. The screen owns the tab strip,
 * the shared {@code OriginDraft}, and widget lifecycle; a tab supplies its
 * title, registers its widgets in the content rectangle it is handed, draws
 * its body, and syncs the draft.
 *
 * <p>Lifecycle per activation: {@link #init} (register widgets via
 * {@code parent.register(...)}) → {@link #pullFromDraft} → repeated
 * {@link #render}. On switching away or saving, {@link #pushToDraft} flushes
 * edits back into the shared model.
 *
 * <p>Phase 1: tabs are framework stubs (title + placeholder body); real
 * content arrives in Phase 4.
 */
public interface CreatorTab {

    /** Tab-strip label. */
    Component title();

    /**
     * Register this tab's widgets. The content rectangle excludes the tab strip
     * and the bottom button bar.
     */
    void init(OriginCreatorScreen parent, int x, int y, int w, int h);

    /** Draw the tab body (widgets are rendered by the screen's super extract). */
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, int x, int y, int w, int h);

    /** Load the shared draft into this tab's widgets. */
    default void pullFromDraft() {}

    /** Flush this tab's widget state back into the shared draft. */
    default void pushToDraft() {}

    /** Optional scroll handling for tabs with overflow content. */
    default boolean mouseScrolled(double mx, double my, double sx, double sy) { return false; }
}
