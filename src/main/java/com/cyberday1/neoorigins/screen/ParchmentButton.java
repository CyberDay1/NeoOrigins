package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.client.theme.UITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Themed button that paints itself in the active {@link UITheme} parchment
 * palette instead of the vanilla button sprite. Label is wrapped in
 * {@code Style.withFont(theme.font())} so the Newsreader-backed font is used.
 *
 * <p>Used for the sort cycle, Random / Back / Confirm buttons in
 * {@link OriginSelectionScreen} so all in-screen controls share the parchment
 * look.
 */
public class ParchmentButton extends Button {

    /** When true, paints with the compact {@code *_short} scroll art instead of {@code *_long}. */
    private boolean shortStyle = false;

    public ParchmentButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    public ParchmentButton shortStyle(boolean v) { this.shortStyle = v; return this; }

    /**
     * Static factory. Named {@code parchment} (not {@code builder}) because the
     * inherited {@link Button#builder} returns a vanilla {@code Button.Builder}
     * and Java cannot hide a static method with a different return type.
     */
    public static Builder parchment(Component label, OnPress onPress) {
        return new Builder(label, onPress);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        UITheme theme = UITheme.current();
        boolean enabled = this.active;
        boolean hovered = isHoveredOrFocused() && enabled;

        // Parchment scroll skin — unrolled (open) on hover with a warm glow,
        // rolled (closed) at rest, dimmed when disabled. Replaces the old flat
        // fill + outline so all in-screen controls share the scroll look.
        ScrollButtonRenderer.draw(g, getX(), getY(), getWidth(), getHeight(),
            hovered, hovered, enabled ? 1.0f : 0.5f, shortStyle);

        // Centered label in themed font.
        Minecraft mc = Minecraft.getInstance();
        Identifier fid = theme.font();
        Component label = fid != null
            ? getMessage().copy().withStyle(s -> s.withFont(new net.minecraft.network.chat.FontDescription.Resource(fid)))
            : getMessage();
        int textColor = !enabled ? theme.mutedColor() : theme.nameColor();
        int textX = getX() + (getWidth() - mc.font.width(label)) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        g.text(mc.font, label, textX, textY, textColor, false);
    }

    /** Lightweight builder mirroring {@link Button.Builder} so callsites read uniformly. */
    public static final class Builder {
        private final Component label;
        private final OnPress onPress;
        private int x, y, w = 80, h = 20;
        private boolean shortStyle = false;

        private Builder(Component label, OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        public Builder shortStyle(boolean v) { this.shortStyle = v; return this; }

        public ParchmentButton build() {
            return new ParchmentButton(x, y, w, h, label, onPress).shortStyle(shortStyle);
        }
    }
}
