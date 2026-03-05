package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class OriginButton extends Button {

    private static final int IMPACT_MAX_DOTS = 4;
    private static final int IMPACT_DOT_SIZE = 5;
    private static final int IMPACT_DOT_SPACING = 7;

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
    public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int color = isSelected() ? 0xFF4A90D9 : (isHovered() ? 0xFF555555 : 0xFF333333);

        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), isSelected() ? 0xFFFFFFFF : 0xFF888888);

        var iconItem = BuiltInRegistries.ITEM.getValue(origin.icon());
        if (iconItem != null) {
            graphics.renderItem(new ItemStack(iconItem), getX() + 4, getY() + (getHeight() - 16) / 2);
        }

        int textX = getX() + 26;
        int textY = getY() + 4;
        graphics.drawString(mc.font, origin.name(), textX, textY, 0xFFFFFFFF, true);

        int maxDescWidth = getWidth() - 62; // account for icon (26) and impact dots (36)
        List<FormattedCharSequence> descLines = mc.font.split(origin.description(), maxDescWidth);
        for (int i = 0; i < Math.min(descLines.size(), 2); i++) {
            graphics.drawString(mc.font, descLines.get(i), textX, textY + 12 + i * 10, 0xFFAAAAAA, false);
        }

        drawImpactDots(graphics, getImpactX(), getImpactY(), origin.impact());
    }

    public boolean isMouseOverImpact(int mouseX, int mouseY) {
        int x0 = getImpactX();
        int y0 = getImpactY();
        int width = (IMPACT_MAX_DOTS - 1) * IMPACT_DOT_SPACING + IMPACT_DOT_SIZE;
        return mouseX >= x0 && mouseX <= x0 + width && mouseY >= y0 && mouseY <= y0 + IMPACT_DOT_SIZE;
    }

    public Component getImpactTooltip() {
        return Component.translatable("origins.gui.impact.impact")
            .append(": ")
            .append(switch (origin.impact()) {
                case NONE -> Component.translatable("origins.gui.impact.none");
                case LOW -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH -> Component.translatable("origins.gui.impact.high");
            });
    }

    private int getImpactX() {
        return getX() + getWidth() - 30;
    }

    private int getImpactY() {
        return getY() + 8;
    }

    private void drawImpactDots(GuiGraphics graphics, int x, int y, Impact impact) {
        int filledDots = impact.getDotCount();
        for (int i = 0; i < IMPACT_MAX_DOTS; i++) {
            int dotColor = i < filledDots ? 0xFFFF8800 : 0xFF666666;
            graphics.fill(x + i * IMPACT_DOT_SPACING, y, x + i * IMPACT_DOT_SPACING + IMPACT_DOT_SIZE, y + IMPACT_DOT_SIZE, dotColor);
        }
    }
}
