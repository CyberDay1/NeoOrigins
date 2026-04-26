package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.AbstractFurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractFurnaceScreen<T extends AbstractFurnaceMenu> extends AbstractContainerScreen<T> implements RecipeUpdateListener {
    public final AbstractFurnaceRecipeBookComponent recipeBookComponent;
    private boolean widthTooNarrow;
    private final ResourceLocation texture;
    private final ResourceLocation litProgressSprite;
    private final ResourceLocation burnProgressSprite;

    public AbstractFurnaceScreen(
        T p_97825_,
        AbstractFurnaceRecipeBookComponent p_97826_,
        Inventory p_97827_,
        Component p_97828_,
        ResourceLocation p_97829_,
        ResourceLocation p_296257_,
        ResourceLocation p_295751_
    ) {
        super(p_97825_, p_97827_, p_97828_);
        this.recipeBookComponent = p_97826_;
        this.texture = p_97829_;
        this.litProgressSprite = p_296257_;
        this.burnProgressSprite = p_295751_;
    }

    @Override
    public void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
        this.addRenderableWidget(new ImageButton(this.leftPos + 20, this.height / 2 - 49, 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, p_313431_ -> {
            this.recipeBookComponent.toggleVisibility();
            this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
            p_313431_.setPosition(this.leftPos + 20, this.height / 2 - 49);
        }));
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.recipeBookComponent.tick();
    }

    @Override
    public void render(GuiGraphics p_282573_, int p_97859_, int p_97860_, float p_97861_) {
        if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.renderBackground(p_282573_, p_97859_, p_97860_, p_97861_);
            this.recipeBookComponent.render(p_282573_, p_97859_, p_97860_, p_97861_);
        } else {
            super.render(p_282573_, p_97859_, p_97860_, p_97861_);
            this.recipeBookComponent.render(p_282573_, p_97859_, p_97860_, p_97861_);
            this.recipeBookComponent.renderGhostRecipe(p_282573_, this.leftPos, this.topPos, true, p_97861_);
        }

        this.renderTooltip(p_282573_, p_97859_, p_97860_);
        this.recipeBookComponent.renderTooltip(p_282573_, this.leftPos, this.topPos, p_97859_, p_97860_);
    }

    @Override
    protected void renderBg(GuiGraphics p_282928_, float p_281631_, int p_281252_, int p_281891_) {
        int i = this.leftPos;
        int j = this.topPos;
        p_282928_.blit(this.texture, i, j, 0, 0, this.imageWidth, this.imageHeight);
        if (this.menu.isLit()) {
            int k = 14;
            int l = Mth.ceil(this.menu.getLitProgress() * 13.0F) + 1;
            p_282928_.blitSprite(this.litProgressSprite, 14, 14, 0, 14 - l, i + 56, j + 36 + 14 - l, 14, l);
        }

        int i1 = 24;
        int j1 = Mth.ceil(this.menu.getBurnProgress() * 24.0F);
        p_282928_.blitSprite(this.burnProgressSprite, 24, 16, 0, 0, i + 79, j + 34, j1, 16);
    }

    @Override
    public boolean mouseClicked(double p_97834_, double p_97835_, int p_97836_) {
        if (this.recipeBookComponent.mouseClicked(p_97834_, p_97835_, p_97836_)) {
            return true;
        } else {
            return this.widthTooNarrow && this.recipeBookComponent.isVisible() ? true : super.mouseClicked(p_97834_, p_97835_, p_97836_);
        }
    }

    @Override
    protected void slotClicked(Slot p_97848_, int p_97849_, int p_97850_, ClickType p_97851_) {
        super.slotClicked(p_97848_, p_97849_, p_97850_, p_97851_);
        this.recipeBookComponent.slotClicked(p_97848_);
    }

    @Override
    public boolean keyPressed(int p_97844_, int p_97845_, int p_97846_) {
        return this.recipeBookComponent.keyPressed(p_97844_, p_97845_, p_97846_) ? true : super.keyPressed(p_97844_, p_97845_, p_97846_);
    }

    @Override
    protected boolean hasClickedOutside(double p_97838_, double p_97839_, int p_97840_, int p_97841_, int p_97842_) {
        boolean flag = p_97838_ < (double)p_97840_
            || p_97839_ < (double)p_97841_
            || p_97838_ >= (double)(p_97840_ + this.imageWidth)
            || p_97839_ >= (double)(p_97841_ + this.imageHeight);
        return this.recipeBookComponent.hasClickedOutside(p_97838_, p_97839_, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, p_97842_) && flag;
    }

    @Override
    public boolean charTyped(char p_97831_, int p_97832_) {
        return this.recipeBookComponent.charTyped(p_97831_, p_97832_) ? true : super.charTyped(p_97831_, p_97832_);
    }

    @Override
    public void recipesUpdated() {
        this.recipeBookComponent.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent() {
        return this.recipeBookComponent;
    }
}
