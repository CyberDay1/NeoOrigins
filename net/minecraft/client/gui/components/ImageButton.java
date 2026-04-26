package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ImageButton extends Button {
    protected final WidgetSprites sprites;

    public ImageButton(int p_94256_, int p_94257_, int p_94258_, int p_94259_, WidgetSprites p_295874_, Button.OnPress p_94266_) {
        this(p_94256_, p_94257_, p_94258_, p_94259_, p_295874_, p_94266_, CommonComponents.EMPTY);
    }

    public ImageButton(int p_169011_, int p_169012_, int p_169013_, int p_169014_, WidgetSprites p_294960_, Button.OnPress p_169018_, Component p_294919_) {
        super(p_169011_, p_169012_, p_169013_, p_169014_, p_294919_, p_169018_, DEFAULT_NARRATION);
        this.sprites = p_294960_;
    }

    public ImageButton(int p_94269_, int p_94270_, WidgetSprites p_295749_, Button.OnPress p_94277_, Component p_294787_) {
        this(0, 0, p_94269_, p_94270_, p_295749_, p_94277_, p_294787_);
    }

    @Override
    public void renderWidget(GuiGraphics p_283502_, int p_281473_, int p_283021_, float p_282518_) {
        ResourceLocation resourcelocation = this.sprites.get(this.isActive(), this.isHoveredOrFocused());
        p_283502_.blitSprite(resourcelocation, this.getX(), this.getY(), this.width, this.height);
    }
}
