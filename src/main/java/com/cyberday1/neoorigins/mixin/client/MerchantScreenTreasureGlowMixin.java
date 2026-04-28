package com.cyberday1.neoorigins.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders a pulsing golden glow behind wandering-trader treasure trade
 * slots injected by the Merchant class's Charisma power.
 *
 * <p>Treasure offers are identified by the {@code NeoOriginsTreasure}
 * boolean in the result item's {@code minecraft:custom_data} component.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenTreasureGlowMixin {

    @Shadow int scrollOff;

    @Shadow protected abstract boolean canScroll(int size);

    @Unique private static final int GOLD_BASE = 0xFFD700;
    @Unique private static final int BUTTON_WIDTH = 88;
    @Unique private static final int BUTTON_HEIGHT = 20;

    /**
     * Injected at the HEAD of {@code render} — draws the golden glow behind
     * each visible treasure trade button before vanilla renders the button
     * sprites and item icons on top.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void neoorigins$renderTreasureGlow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MerchantScreen self = (MerchantScreen) (Object) this;
        MerchantOffers offers = self.getMenu().getOffers();
        if (offers.isEmpty()) return;

        // Mirror the button positioning from MerchantScreen.init()
        int leftX = (self.width - 276) / 2;  // 276 = MerchantScreen.imageWidth
        int topY = (self.height - 166) / 2;   // 166 = imageHeight
        int buttonStartY = topY + 16 + 2;

        // Pulsing alpha: oscillates between ~0.25 and ~0.65
        float pulse = (float) (0.45 + 0.2 * Math.sin(System.currentTimeMillis() / 400.0));
        int innerAlpha = (int) (pulse * 255) << 24;
        int outerAlpha = (int) (pulse * 128) << 24;

        int visibleIndex = 0;
        for (int i = 0; i < offers.size(); i++) {
            if (this.canScroll(offers.size()) && (i < this.scrollOff || i >= 7 + this.scrollOff)) {
                continue;
            }

            if (neoorigins$isTreasure(offers.get(i))) {
                int bx = leftX + 5;
                int by = buttonStartY + visibleIndex * BUTTON_HEIGHT;

                // Outer glow — 1px border, softer
                graphics.fill(bx - 1, by - 1, bx + BUTTON_WIDTH + 1, by + BUTTON_HEIGHT + 1,
                    outerAlpha | GOLD_BASE);
                // Inner glow — fills the button area
                graphics.fill(bx, by, bx + BUTTON_WIDTH, by + BUTTON_HEIGHT,
                    innerAlpha | GOLD_BASE);
            }

            visibleIndex++;
        }
    }

    @Unique
    private static boolean neoorigins$isTreasure(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        CustomData customData = result.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBooleanOr("NeoOriginsTreasure", false);
    }
}
