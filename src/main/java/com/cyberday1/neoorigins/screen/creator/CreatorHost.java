package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;

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
    <T extends AbstractWidget> T register(T widget);

    Font font();

    /** Rebuild the active tab's widgets (after a state change alters them). */
    void requestRebuild();

    /** Screen width / height (Screen's public fields, exposed via the seam). */
    int hostWidth();

    int hostHeight();

    /** Queue a tooltip to be drawn topmost after all widgets this frame. */
    void queueTooltip(java.util.List<String> lines, int mx, int my);
}
