package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.theme.PanelRenderer;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.client.theme.UIThemeUtils;
import com.cyberday1.neoorigins.screen.detail.OriginDetailPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Tall origin card — panel body, large icon, wrapped name and impact dots.
 * The portrait counterpart to {@link OriginButton}, which is hard-wired to a
 * wide, short row.
 *
 * <p>Painted with {@link PanelRenderer} rather than {@code ScrollButtonRenderer}:
 * the scroll art three-slices a 120x25 texture with fixed 12px end-caps, which
 * stretch beyond recognition at card proportions. {@code drawPanel} already
 * falls back to fill + outline on a flat theme, so {@code classic_picker_style}
 * needs no extra code here.
 */
public class OriginCardButton extends Button {

    private static final int ICON_TOP  = 8;
    private static final int ICON_SIZE = 32;
    private static final int NAME_GAP  = 5;
    private static final int LINE_H    = 10;
    private static final int MAX_NAME_LINES = 2;
    private static final int DOTS_BOTTOM_PAD = 12;
    private static final int TEXT_MARGIN = 8;

    private final Origin origin;
    private final Font font;
    /** Wrapped once at construction — a grid draws ~30 cards per frame. */
    private final List<FormattedCharSequence> nameLines;
    private boolean selected;

    public OriginCardButton(int x, int y, int w, int h, Origin origin, OnPress onPress) {
        super(x, y, w, h, origin.name(), onPress, DEFAULT_NARRATION);
        this.origin = origin;
        this.font = Minecraft.getInstance().font;
        this.nameLines = wrapName(Math.max(1, w - TEXT_MARGIN));
    }

    public Origin getOrigin()                 { return origin; }
    public boolean isSelected()               { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    /** Up to {@link #MAX_NAME_LINES} lines, the last ellipsised when it overflows. */
    private List<FormattedCharSequence> wrapName(int maxW) {
        List<FormattedCharSequence> lines = font.split(UIThemeUtils.themedBold(origin.name()), maxW);
        if (lines.size() <= MAX_NAME_LINES) return lines;
        String raw = origin.name().getString();
        while (raw.length() > 1) {
            raw = raw.substring(0, raw.length() - 1);
            var trimmed = font.split(
                UIThemeUtils.themedBold(Component.literal(raw.stripTrailing() + "...")), maxW);
            if (trimmed.size() <= MAX_NAME_LINES) return trimmed;
        }
        return lines.subList(0, MAX_NAME_LINES);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        UITheme theme = UITheme.current();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        PanelRenderer.drawPanel(g, theme, x, y, w, h);
        if (selected) {
            g.outline(x, y, w, h, theme.accentColor());
        } else if (isHovered()) {
            g.outline(x, y, w, h, theme.borderColor());
        }

        // renderIcon is fixed at 16x16 (g.renderItem takes no scale), so double it
        // through the matrix stack to fill the card's 32x32 icon block.
        int cx = x + w / 2;
        int iconY = y + ICON_TOP;
        g.pose().pushMatrix();
        g.pose().translate(cx - ICON_SIZE / 2f, iconY);
        g.pose().scale(2f, 2f);
        OriginButton.renderIcon(g, origin.icon(), 0, 0);
        g.pose().popMatrix();

        int nameColor = selected ? theme.nameColor() : theme.descriptionColor();
        int ty = iconY + ICON_SIZE + NAME_GAP;
        for (FormattedCharSequence line : nameLines) {
            g.text(font, line, cx - font.width(line) / 2, ty, nameColor, false);
            ty += LINE_H;
        }

        OriginDetailPanel.drawImpactDots(g, cx, y + h - DOTS_BOTTOM_PAD, origin.impact());
    }
}
