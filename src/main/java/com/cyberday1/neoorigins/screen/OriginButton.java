package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OriginButton extends Button {

    private final Origin origin;
    private boolean selected;

    public OriginButton(int x, int y, int width, int height, Origin origin, OnPress onPress) {
        super(x, y, width, height, origin.name(), onPress, DEFAULT_NARRATION);
        this.origin = origin;
    }

    public Origin getOrigin() { return origin; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int color = isSelected() ? 0xFF4A90D9 : (isHovered() ? 0xFF555555 : 0xFF333333);

        // Background
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
        // Border
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), isSelected() ? 0xFFFFFFFF : 0xFF888888);

        // Item icon (8x8 area on left)
        if (!origin.icon().isEmpty()) {
            graphics.renderItem(origin.icon(), getX() + 4, getY() + (getHeight() - 16) / 2);
        }

        // Origin name
        int textX = getX() + 26;
        int textY = getY() + 4;
        graphics.drawString(mc.font, origin.name(), textX, textY, 0xFFFFFF, true);

        // Description (truncated)
        String desc = origin.description().getString();
        if (desc.length() > 40) desc = desc.substring(0, 37) + "...";
        graphics.drawString(mc.font, desc, textX, textY + 12, 0xAAAAAA, false);

        // Impact dots
        drawImpactDots(graphics, getX() + getWidth() - 30, getY() + 8, origin.impact());
    }

    private void drawImpactDots(GuiGraphics graphics, int x, int y, Impact impact) {
        int maxDots = 4; // HIGH = 4 dots
        int filledDots = impact.getDotCount();
        for (int i = 0; i < maxDots; i++) {
            int dotColor = i < filledDots ? 0xFFFF8800 : 0xFF666666;
            graphics.fill(x + i * 7, y, x + i * 7 + 5, y + 5, dotColor);
        }
    }
}
