package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
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
        // Background
        int bg = isSelected() ? 0xFF1E3A6E : (isHovered() ? 0xFF1E1E32 : 0xFF14141F);
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

        // Border — bright when selected, dim otherwise
        int border = isSelected() ? 0xFF4A90D9 : 0xFF2A2A44;
        g.renderOutline(getX(), getY(), getWidth(), getHeight(), border);

        // 16×16 icon
        renderIcon(g, origin.icon(), getX() + 3, getY() + (getHeight() - 16) / 2);

        // Name
        int nameColor = isSelected() ? 0xFFFFFFFF : (isHovered() ? 0xFFDDDDDD : 0xFFAAAAAA);
        Minecraft mc = Minecraft.getInstance();
        int textY = getY() + (getHeight() - 8) / 2;
        g.drawString(mc.font, origin.name(), getX() + 22, textY, nameColor, false);
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
