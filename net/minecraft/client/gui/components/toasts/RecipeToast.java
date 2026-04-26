package net.minecraft.client.gui.components.toasts;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RecipeToast implements Toast {
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/recipe");
    private static final long DISPLAY_TIME = 5000L;
    private static final Component TITLE_TEXT = Component.translatable("recipe.toast.title");
    private static final Component DESCRIPTION_TEXT = Component.translatable("recipe.toast.description");
    private final List<RecipeHolder<?>> recipes = Lists.newArrayList();
    private long lastChanged;
    private boolean changed;

    public RecipeToast(RecipeHolder<?> p_301250_) {
        this.recipes.add(p_301250_);
    }

    @Override
    public Toast.Visibility render(GuiGraphics p_281667_, ToastComponent p_281321_, long p_281779_) {
        if (this.changed) {
            this.lastChanged = p_281779_;
            this.changed = false;
        }

        if (this.recipes.isEmpty()) {
            return Toast.Visibility.HIDE;
        } else {
            p_281667_.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
            p_281667_.drawString(p_281321_.getMinecraft().font, TITLE_TEXT, 30, 7, -11534256, false);
            p_281667_.drawString(p_281321_.getMinecraft().font, DESCRIPTION_TEXT, 30, 18, -16777216, false);
            RecipeHolder<?> recipeholder = this.recipes
                .get(
                    (int)(
                        (double)p_281779_
                            / Math.max(1.0, 5000.0 * p_281321_.getNotificationDisplayTimeMultiplier() / (double)this.recipes.size())
                            % (double)this.recipes.size()
                    )
                );
            ItemStack itemstack = recipeholder.value().getToastSymbol();
            p_281667_.pose().pushPose();
            p_281667_.pose().scale(0.6F, 0.6F, 1.0F);
            p_281667_.renderFakeItem(itemstack, 3, 3);
            p_281667_.pose().popPose();
            p_281667_.renderFakeItem(recipeholder.value().getResultItem(p_281321_.getMinecraft().level.registryAccess()), 8, 8);
            return (double)(p_281779_ - this.lastChanged) >= 5000.0 * p_281321_.getNotificationDisplayTimeMultiplier()
                ? Toast.Visibility.HIDE
                : Toast.Visibility.SHOW;
        }
    }

    private void addItem(RecipeHolder<?> p_301083_) {
        this.recipes.add(p_301083_);
        this.changed = true;
    }

    public static void addOrUpdate(ToastComponent p_94818_, RecipeHolder<?> p_301027_) {
        RecipeToast recipetoast = p_94818_.getToast(RecipeToast.class, NO_TOKEN);
        if (recipetoast == null) {
            p_94818_.addToast(new RecipeToast(p_301027_));
        } else {
            recipetoast.addItem(p_301027_);
        }
    }
}
