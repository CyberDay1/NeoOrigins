package net.minecraft.client.gui.screens.recipebook;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RecipeButton extends AbstractWidget {
    private static final ResourceLocation SLOT_MANY_CRAFTABLE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_many_craftable");
    private static final ResourceLocation SLOT_CRAFTABLE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_craftable");
    private static final ResourceLocation SLOT_MANY_UNCRAFTABLE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_many_uncraftable");
    private static final ResourceLocation SLOT_UNCRAFTABLE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_uncraftable");
    private static final float ANIMATION_TIME = 15.0F;
    private static final int BACKGROUND_SIZE = 25;
    public static final int TICKS_TO_SWAP = 30;
    private static final Component MORE_RECIPES_TOOLTIP = Component.translatable("gui.recipebook.moreRecipes");
    private RecipeBookMenu<?, ?> menu;
    private RecipeBook book;
    private RecipeCollection collection;
    private float time;
    private float animationTime;
    private int currentIndex;

    public RecipeButton() {
        super(0, 0, 25, 25, CommonComponents.EMPTY);
    }

    public void init(RecipeCollection p_100480_, RecipeBookPage p_100481_) {
        this.collection = p_100480_;
        this.menu = (RecipeBookMenu<?, ?>)p_100481_.getMinecraft().player.containerMenu;
        this.book = p_100481_.getRecipeBook();
        List<RecipeHolder<?>> list = p_100480_.getRecipes(this.book.isFiltering(this.menu));

        for (RecipeHolder<?> recipeholder : list) {
            if (this.book.willHighlight(recipeholder)) {
                p_100481_.recipesShown(list);
                this.animationTime = 15.0F;
                break;
            }
        }
    }

    public RecipeCollection getCollection() {
        return this.collection;
    }

    @Override
    public void renderWidget(GuiGraphics p_281385_, int p_282779_, int p_282744_, float p_282439_) {
        if (!Screen.hasControlDown()) {
            this.time += p_282439_;
        }

        ResourceLocation resourcelocation;
        if (this.collection.hasCraftable()) {
            if (this.collection.getRecipes(this.book.isFiltering(this.menu)).size() > 1) {
                resourcelocation = SLOT_MANY_CRAFTABLE_SPRITE;
            } else {
                resourcelocation = SLOT_CRAFTABLE_SPRITE;
            }
        } else if (this.collection.getRecipes(this.book.isFiltering(this.menu)).size() > 1) {
            resourcelocation = SLOT_MANY_UNCRAFTABLE_SPRITE;
        } else {
            resourcelocation = SLOT_UNCRAFTABLE_SPRITE;
        }

        boolean flag = this.animationTime > 0.0F;
        if (flag) {
            float f = 1.0F + 0.1F * (float)Math.sin((double)(this.animationTime / 15.0F * (float) Math.PI));
            p_281385_.pose().pushPose();
            p_281385_.pose().translate((float)(this.getX() + 8), (float)(this.getY() + 12), 0.0F);
            p_281385_.pose().scale(f, f, 1.0F);
            p_281385_.pose().translate((float)(-(this.getX() + 8)), (float)(-(this.getY() + 12)), 0.0F);
            this.animationTime -= p_282439_;
        }

        p_281385_.blitSprite(resourcelocation, this.getX(), this.getY(), this.width, this.height);
        List<RecipeHolder<?>> list = this.getOrderedRecipes();
        this.currentIndex = Mth.floor(this.time / 30.0F) % list.size();
        ItemStack itemstack = list.get(this.currentIndex).value().getResultItem(this.collection.registryAccess());
        int i = 4;
        if (this.collection.hasSingleResultItem() && this.getOrderedRecipes().size() > 1) {
            p_281385_.renderItem(itemstack, this.getX() + i + 1, this.getY() + i + 1, 0, 10);
            i--;
        }

        p_281385_.renderFakeItem(itemstack, this.getX() + i, this.getY() + i);
        if (flag) {
            p_281385_.pose().popPose();
        }
    }

    private List<RecipeHolder<?>> getOrderedRecipes() {
        List<RecipeHolder<?>> list = this.collection.getDisplayRecipes(true);
        if (!this.book.isFiltering(this.menu)) {
            list.addAll(this.collection.getDisplayRecipes(false));
        }

        return list;
    }

    public boolean isOnlyOption() {
        return this.getOrderedRecipes().size() == 1;
    }

    public RecipeHolder<?> getRecipe() {
        List<RecipeHolder<?>> list = this.getOrderedRecipes();
        return list.get(this.currentIndex);
    }

    public List<Component> getTooltipText() {
        ItemStack itemstack = this.getOrderedRecipes().get(this.currentIndex).value().getResultItem(this.collection.registryAccess());
        List<Component> list = Lists.newArrayList(Screen.getTooltipFromItem(Minecraft.getInstance(), itemstack));
        if (this.collection.getRecipes(this.book.isFiltering(this.menu)).size() > 1) {
            list.add(MORE_RECIPES_TOOLTIP);
        }

        return list;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput p_170060_) {
        ItemStack itemstack = this.getOrderedRecipes().get(this.currentIndex).value().getResultItem(this.collection.registryAccess());
        p_170060_.add(NarratedElementType.TITLE, Component.translatable("narration.recipe", itemstack.getHoverName()));
        if (this.collection.getRecipes(this.book.isFiltering(this.menu)).size() > 1) {
            p_170060_.add(
                NarratedElementType.USAGE, Component.translatable("narration.button.usage.hovered"), Component.translatable("narration.recipe.usage.more")
            );
        } else {
            p_170060_.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.hovered"));
        }
    }

    @Override
    public int getWidth() {
        return 25;
    }

    @Override
    protected boolean isValidClickButton(int p_100473_) {
        return p_100473_ == 0 || p_100473_ == 1;
    }
}
