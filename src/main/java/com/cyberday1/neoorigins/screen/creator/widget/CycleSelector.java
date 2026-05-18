package com.cyberday1.neoorigins.screen.creator.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

/**
 * A {@link Button}-backed {@code &lt; value &gt;} cycler over a fixed list —
 * the creator's stand-in for a dropdown (vanilla has no combo box, matching the
 * in-repo widget style). Left/right is a single click that advances (wraps);
 * the button label shows the current value.
 *
 * @param <T> the value type the selector cycles through
 */
public final class CycleSelector<T> {

    private final List<T> values;
    private final Function<T, String> labeler;
    private int index;
    private Button button;

    public CycleSelector(List<T> values, Function<T, String> labeler) {
        if (values.isEmpty()) throw new IllegalArgumentException("CycleSelector needs values");
        this.values = values;
        this.labeler = labeler;
    }

    /** Build the backing button; register the returned widget with the screen. */
    public Button build(int x, int y, int w, int h) {
        button = Button.builder(label(), b -> {
            index = (index + 1) % values.size();
            button.setMessage(label());
        }).bounds(x, y, w, h).build();
        return button;
    }

    public T value() { return values.get(index); }

    /** Select {@code v} (no-op if absent); refreshes the label if already built. */
    public void setValue(T v) {
        int i = values.indexOf(v);
        if (i >= 0) {
            index = i;
            if (button != null) button.setMessage(label());
        }
    }

    private Component label() {
        return Component.literal(labeler.apply(values.get(index)));
    }
}
