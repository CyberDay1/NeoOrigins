package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.client.theme.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Parchment-skinned single-line text input. Paints its own background and
 * border in the {@link UITheme} palette, then defers to the vanilla
 * {@link EditBox} for caret / cursor / text rendering — vanilla's text path
 * picks up the {@link net.minecraft.network.chat.Style#withFont} on the
 * placeholder/value Components, so wrapping setHint() / setValue() callers
 * with {@code themed()} is what gets the typed text into Newsreader.
 *
 * <p>Border tinted with the theme accent on focus.
 */
public class ParchmentEditBox extends EditBox {

    public ParchmentEditBox(Font font, int x, int y, int w, int h, Component label) {
        super(font, x, y, w, h, label);
        // Vanilla paints a 1px outer border + 1px inner light fill; we want
        // neither because we draw our own. Setting bordered=false disables
        // both. We'll paint a themed border in renderWidget below.
        setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UITheme theme = UITheme.current();

        // Warm parchment fill — match unselected OriginButton tones at idle and
        // bump to selected-button strength when focused so the search box reads
        // as part of the same control column as the origin list below it. In the
        // flat classic skin, match the dark high-contrast OriginButton tones.
        int bg = theme.flat()
            ? (isFocused() ? 0xFF1E3A6E : 0xFF14141F)
            : (isFocused() ? 0xCCB58040 : 0x66B58040);
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

        // Burnt-edge border — accent when focused.
        int border = isFocused() ? theme.accentColor() : theme.borderColor();
        g.renderOutline(getX(), getY(), getWidth(), getHeight(), border);

        // Vanilla EditBox with setBordered(false) draws text/caret at getY()
        // (top of the box) instead of vertically centered. Translate the
        // matrix down so vanilla's text rendering ends up centered in our
        // visual box. Mouse hit-testing still uses the unshifted getY/getX
        // so clicks land where the user expects.
        int textShift = Math.max(0, (getHeight() - 8) / 2);
        g.pose().pushPose();
        g.pose().translate(2.0f, (float) textShift, 0.0f);
        super.renderWidget(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    /**
     * Convenience: wrap a Component with the theme font so callers don't have
     * to import UITheme themselves. Mirrors OriginSelectionScreen.themed().
     */
    public static Component themed(Component c) {
        ResourceLocation fid = UITheme.current().font();
        return fid != null ? c.copy().withStyle(s -> s.withFont(fid)) : c;
    }
}
