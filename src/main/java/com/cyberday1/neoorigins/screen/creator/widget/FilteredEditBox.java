package com.cyberday1.neoorigins.screen.creator.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/**
 * {@link EditBox} with a whole-value input filter. Replaces
 * {@code EditBox#setFilter}, which NeoForge removed in 26.2.0.52-beta.
 */
public class FilteredEditBox extends EditBox {

    private Predicate<String> filter = s -> true;

    public FilteredEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    @Override
    public void setValue(String value) {
        if (filter.test(value)) super.setValue(value);
    }

    @Override
    public void insertText(String input) {
        String before = getValue();
        super.insertText(input);
        if (!filter.test(getValue())) super.setValue(before);
    }
}
