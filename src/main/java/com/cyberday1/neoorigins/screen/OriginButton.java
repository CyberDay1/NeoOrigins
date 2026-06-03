package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.theme.UITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
/**
 * Compact origin list item for the left panel of OriginSelectionScreen.
 * Renders a 16×16 icon and the origin name only — detail is shown in the right panel.
 */
public class OriginButton extends Button {

    private final Origin origin;
    private boolean selected;

    public OriginButton(int x, int y, int width, int height, Origin origin, OnPress onPress) {
        super(x, y, width, height, origin.name(), onPress, DEFAULT_NARRATION);
        this.origin = origin;
    }

    public Origin getOrigin()                    { return origin; }
    public boolean isSelected()                  { return selected; }
    public void setSelected(boolean selected)    { this.selected = selected; }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UITheme theme = UITheme.current();

        // Parchment scroll skin: rolled (closed) when resting, unrolled (open)
        // when selected or hovered, with a warm glow on hover. Replaces the old
        // flat fill + outline.
        boolean active = isSelected() || isHovered();
        ScrollButtonRenderer.draw(g, getX(), getY(), getWidth(), getHeight(),
            active, isHovered(), 1.0f, true);

        // 16×16 icon
        renderIcon(g, origin.icon(), getX() + 3, getY() + (getHeight() - 16) / 2);

        // Name — themed font, theme text colors.
        int nameColor = isSelected() ? theme.nameColor() : theme.descriptionColor();
        Minecraft mc = Minecraft.getInstance();
        int textY = getY() + (getHeight() - 8) / 2;
        ResourceLocation fid = theme.font();
        Component label = fid != null ? origin.name().copy().withStyle(s -> s.withFont(fid)) : origin.name();
        // Centre the name across the full button width, but never let it slide
        // left under the 16×16 icon (x+3..x+19) — long names fall back to a
        // left-aligned x+22 so they stay clear of the icon and the right edge.
        int textX = Math.max(getX() + 22, getX() + (getWidth() - mc.font.width(label)) / 2);
        g.drawString(mc.font, label, textX, textY, nameColor, false);
    }

    /**
     * Renders a 16×16 origin icon. If the stack is non-empty it is rendered
     * directly (preserving data components like custom model data); otherwise
     * falls back to blitting the texture at assets/&lt;ns&gt;/textures/item/&lt;path&gt;.png.
     */
    static void renderIcon(GuiGraphics g, ItemStack icon, int x, int y) {
        if (!icon.isEmpty()) {
            g.renderItem(icon, x, y);
            return;
        }
        ResourceLocation iconId = BuiltInRegistries.ITEM.getKey(icon.getItem());
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
            iconId.getNamespace(), "textures/item/" + iconId.getPath() + ".png");
        g.blit(texture, x, y, 0.0f, 0.0f, 16, 16, 16, 16);
    }
}
