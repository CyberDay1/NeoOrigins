package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/**
 * The minimal surface the reusable creator widgets ({@code SearchPickerOverlay},
 * {@code ItemPickerOverlay}, {@code PowerFormPanel}, {@code FieldWidgetFactory})
 * need from their host screen. Implemented by both the player
 * {@link OriginCreatorScreen} and the
 * {@link com.cyberday1.neoorigins.screen.mobcreator.MobOriginCreatorScreen},
 * so those widgets are shared verbatim instead of cloned.
 */
public interface CreatorHost {

    /** Register a widget with the screen (delegates to addRenderableWidget).
     *  Bound matches OriginCreatorScreen's existing method exactly so it is a
     *  true override; Button/EditBox satisfy all three on both branches. */
    <T extends GuiEventListener & Renderable & NarratableEntry> T register(T widget);

    /** Register a widget for INPUT ONLY (delegates to {@code addWidget}, NOT
     *  {@code addRenderableWidget}). The screen will route clicks/keys to it but
     *  will NOT auto-render it — the caller owns drawing it, e.g. clipped inside
     *  a scroll viewport. Used by {@link PowerFormPanel} so its field widgets
     *  render inside the scissor instead of bleeding past the panel. */
    <T extends GuiEventListener & Renderable & NarratableEntry> T registerInputOnly(T widget);

    Font font();

    /** Rebuild the active tab's widgets (after a state change alters them). */
    void requestRebuild();

    /** Screen width / height (Screen's public fields, exposed via the seam). */
    int hostWidth();

    int hostHeight();

    /** Queue a tooltip to be drawn topmost after all widgets this frame. */
    void queueTooltip(java.util.List<String> lines, int mx, int my);
}
