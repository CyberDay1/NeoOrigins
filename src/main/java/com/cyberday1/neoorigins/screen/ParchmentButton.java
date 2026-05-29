package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.client.theme.UITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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

    public ParchmentButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    /**
     * Static factory. Named {@code parchment} (not {@code builder}) because the
     * inherited {@link Button#builder} returns a vanilla {@code Button.Builder}
     * and Java cannot hide a static method with a different return type.
     */
    public static Builder parchment(Component label, OnPress onPress) {
        return new Builder(label, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UITheme theme = UITheme.current();
        boolean enabled = this.active;
        boolean hovered = isHoveredOrFocused() && enabled;

        // Warm parchment fill — slightly brighter on hover, muted when disabled.
        // High alpha so buttons stay legible against the dark scrim (the
        // Random / Back / Confirm row sits outside the parchment panels).
        int bg = !enabled
            ? 0x88806030
            : (hovered ? 0xFFD8A04C : 0xDDB58040);
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

        // Burnt-edge border — accent on hover, theme border otherwise.
        int border = !enabled
            ? (theme.borderColor() & 0x40FFFFFF)
            : (hovered ? theme.accentColor() : theme.borderColor());
        g.renderOutline(getX(), getY(), getWidth(), getHeight(), border);

        // Centered label in themed font.
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation fid = theme.font();
        Component label = fid != null ? getMessage().copy().withStyle(s -> s.withFont(fid)) : getMessage();
        int textColor = !enabled ? theme.mutedColor() : theme.nameColor();
        int textX = getX() + (getWidth() - mc.font.width(label)) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        g.drawString(mc.font, label, textX, textY, textColor, false);
    }

    /** Lightweight builder mirroring {@link Button.Builder} so callsites read uniformly. */
    public static final class Builder {
        private final Component label;
        private final OnPress onPress;
        private int x, y, w = 80, h = 20;

        private Builder(Component label, OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        public ParchmentButton build() {
            return new ParchmentButton(x, y, w, h, label, onPress);
        }
    }
}
