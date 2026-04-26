package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.math.Fraction;

@OnlyIn(Dist.CLIENT)
public class ClientBundleTooltip implements ClientTooltipComponent {
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("container/bundle/background");
    private static final int MARGIN_Y = 4;
    private static final int BORDER_WIDTH = 1;
    private static final int SLOT_SIZE_X = 18;
    private static final int SLOT_SIZE_Y = 20;
    private final BundleContents contents;

    public ClientBundleTooltip(BundleContents p_331917_) {
        this.contents = p_331917_;
    }

    @Override
    public int getHeight() {
        return this.backgroundHeight() + 4;
    }

    @Override
    public int getWidth(Font p_169901_) {
        return this.backgroundWidth();
    }

    private int backgroundWidth() {
        return this.gridSizeX() * 18 + 2;
    }

    private int backgroundHeight() {
        return this.gridSizeY() * 20 + 2;
    }

    @Override
    public void renderImage(Font p_194042_, int p_194043_, int p_194044_, GuiGraphics p_282522_) {
        int i = this.gridSizeX();
        int j = this.gridSizeY();
        p_282522_.blitSprite(BACKGROUND_SPRITE, p_194043_, p_194044_, this.backgroundWidth(), this.backgroundHeight());
        boolean flag = this.contents.weight().compareTo(Fraction.ONE) >= 0;
        int k = 0;

        for (int l = 0; l < j; l++) {
            for (int i1 = 0; i1 < i; i1++) {
                int j1 = p_194043_ + i1 * 18 + 1;
                int k1 = p_194044_ + l * 20 + 1;
                this.renderSlot(j1, k1, k++, flag, p_282522_, p_194042_);
            }
        }
    }

    private void renderSlot(int p_283180_, int p_282972_, int p_282547_, boolean p_283053_, GuiGraphics p_283625_, Font p_281863_) {
        if (p_282547_ >= this.contents.size()) {
            this.blit(p_283625_, p_283180_, p_282972_, p_283053_ ? ClientBundleTooltip.Texture.BLOCKED_SLOT : ClientBundleTooltip.Texture.SLOT);
        } else {
            ItemStack itemstack = this.contents.getItemUnsafe(p_282547_);
            this.blit(p_283625_, p_283180_, p_282972_, ClientBundleTooltip.Texture.SLOT);
            p_283625_.renderItem(itemstack, p_283180_ + 1, p_282972_ + 1, p_282547_);
            p_283625_.renderItemDecorations(p_281863_, itemstack, p_283180_ + 1, p_282972_ + 1);
            if (p_282547_ == 0) {
                AbstractContainerScreen.renderSlotHighlight(p_283625_, p_283180_ + 1, p_282972_ + 1, 0);
            }
        }
    }

    private void blit(GuiGraphics p_281273_, int p_282428_, int p_281897_, ClientBundleTooltip.Texture p_281917_) {
        p_281273_.blitSprite(p_281917_.sprite, p_282428_, p_281897_, 0, p_281917_.w, p_281917_.h);
    }

    private int gridSizeX() {
        return Math.max(2, (int)Math.ceil(Math.sqrt((double)this.contents.size() + 1.0)));
    }

    private int gridSizeY() {
        return (int)Math.ceil(((double)this.contents.size() + 1.0) / (double)this.gridSizeX());
    }

    @OnlyIn(Dist.CLIENT)
    static enum Texture {
        BLOCKED_SLOT(ResourceLocation.withDefaultNamespace("container/bundle/blocked_slot"), 18, 20),
        SLOT(ResourceLocation.withDefaultNamespace("container/bundle/slot"), 18, 20);

        public final ResourceLocation sprite;
        public final int w;
        public final int h;

        private Texture(ResourceLocation p_295000_, int p_169928_, int p_169929_) {
            this.sprite = p_295000_;
            this.w = p_169928_;
            this.h = p_169929_;
        }
    }
}
