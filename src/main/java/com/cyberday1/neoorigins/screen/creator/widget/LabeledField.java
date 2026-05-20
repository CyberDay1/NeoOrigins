package com.cyberday1.neoorigins.screen.creator.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/**
 * A label + {@link EditBox} pair. The tab draws the label (via
 * {@link #drawLabel}); the {@link EditBox} is a registered widget so the
 * screen renders/handles it. Optional input filter for numeric fields.
 */
public final class LabeledField {

    private final String label;
    private final Predicate<String> filter; // nullable
    private EditBox box;

    public LabeledField(String label) { this(label, null); }

    public LabeledField(String label, Predicate<String> filter) {
        this.label = label;
        this.filter = filter;
    }

    /** Build the box; register the returned widget with the screen. */
    public EditBox build(Font font, int x, int y, int w, int h) {
        box = new EditBox(font, x, y, w, h, Component.literal(label));
        box.setMaxLength(256);
        if (filter != null) box.setFilter(filter);
        return box;
    }

    public String value() { return box == null ? "" : box.getValue(); }

    public void setValue(String v) { if (box != null) box.setValue(v == null ? "" : v); }

    /** Draw the label to the left of the box (call from the tab's render). */
    public void drawLabel(GuiGraphics g, Font font, int x, int y) {
        g.drawString(font,
            com.cyberday1.neoorigins.screen.creator.CreatorStyle.title(label), x, y,
            com.cyberday1.neoorigins.screen.creator.CreatorStyle.LABEL, false);
    }

    /** Integer-only filter (allows empty and leading '-'). */
    public static Predicate<String> intFilter() {
        return s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+");
    }

    /** Double filter — allows empty, leading '-', and any decimal-shaped prefix. */
    public static Predicate<String> doubleFilter() {
        return s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")
            || s.matches("-?\\d*\\.?\\d*");
    }
}
