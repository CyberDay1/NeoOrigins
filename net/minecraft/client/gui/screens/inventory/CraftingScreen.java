package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CraftingScreen extends AbstractContainerScreen<CraftingMenu> implements RecipeUpdateListener {
    private static final ResourceLocation CRAFTING_TABLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
    private boolean widthTooNarrow;

    public CraftingScreen(CraftingMenu p_98448_, Inventory p_98449_, Component p_98450_) {
        super(p_98448_, p_98449_, p_98450_);
    }

    @Override
    protected void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
        this.addRenderableWidget(new ImageButton(this.leftPos + 5, this.height / 2 - 49, 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, p_313433_ -> {
            this.recipeBookComponent.toggleVisibility();
            this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
            p_313433_.setPosition(this.leftPos + 5, this.height / 2 - 49);
        }));
        this.addWidget(this.recipeBookComponent);
        this.titleLabelX = 29;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.recipeBookComponent.tick();
    }

    @Override
    public void render(GuiGraphics p_282508_, int p_98480_, int p_98481_, float p_98482_) {
        if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.renderBackground(p_282508_, p_98480_, p_98481_, p_98482_);
            this.recipeBookComponent.render(p_282508_, p_98480_, p_98481_, p_98482_);
        } else {
            super.render(p_282508_, p_98480_, p_98481_, p_98482_);
            this.recipeBookComponent.render(p_282508_, p_98480_, p_98481_, p_98482_);
            this.recipeBookComponent.renderGhostRecipe(p_282508_, this.leftPos, this.topPos, true, p_98482_);
        }

        this.renderTooltip(p_282508_, p_98480_, p_98481_);
        this.recipeBookComponent.renderTooltip(p_282508_, this.leftPos, this.topPos, p_98480_, p_98481_);
    }

    @Override
    protected void renderBg(GuiGraphics p_283540_, float p_282132_, int p_283078_, int p_283647_) {
        int i = this.leftPos;
        int j = (this.height - this.imageHeight) / 2;
        p_283540_.blit(CRAFTING_TABLE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public boolean keyPressed(int p_320488_, int p_320952_, int p_320483_) {
        return this.recipeBookComponent.keyPressed(p_320488_, p_320952_, p_320483_) ? true : super.keyPressed(p_320488_, p_320952_, p_320483_);
    }

    @Override
    public boolean charTyped(char p_320706_, int p_320696_) {
        return this.recipeBookComponent.charTyped(p_320706_, p_320696_) ? true : super.charTyped(p_320706_, p_320696_);
    }

    @Override
    protected boolean isHovering(int p_98462_, int p_98463_, int p_98464_, int p_98465_, double p_98466_, double p_98467_) {
        return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible()) && super.isHovering(p_98462_, p_98463_, p_98464_, p_98465_, p_98466_, p_98467_);
    }

    @Override
    public boolean mouseClicked(double p_98452_, double p_98453_, int p_98454_) {
        if (this.recipeBookComponent.mouseClicked(p_98452_, p_98453_, p_98454_)) {
            this.setFocused(this.recipeBookComponent);
            return true;
        } else {
            return this.widthTooNarrow && this.recipeBookComponent.isVisible() ? true : super.mouseClicked(p_98452_, p_98453_, p_98454_);
        }
    }

    @Override
    protected boolean hasClickedOutside(double p_98456_, double p_98457_, int p_98458_, int p_98459_, int p_98460_) {
        boolean flag = p_98456_ < (double)p_98458_
            || p_98457_ < (double)p_98459_
            || p_98456_ >= (double)(p_98458_ + this.imageWidth)
            || p_98457_ >= (double)(p_98459_ + this.imageHeight);
        return this.recipeBookComponent.hasClickedOutside(p_98456_, p_98457_, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, p_98460_) && flag;
    }

    @Override
    protected void slotClicked(Slot p_98469_, int p_98470_, int p_98471_, ClickType p_98472_) {
        super.slotClicked(p_98469_, p_98470_, p_98471_, p_98472_);
        this.recipeBookComponent.slotClicked(p_98469_);
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
