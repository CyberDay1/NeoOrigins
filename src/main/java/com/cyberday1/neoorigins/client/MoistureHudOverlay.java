package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Renders the Slime moisture bar on the HUD, positioned above the hotbar
 * on the right side (mirroring the air bubble bar position).
 *
 * <p>The bar is a horizontal strip that fills from left to right.
 * Colour shifts based on moisture level:
 * <ul>
 *   <li>75%+ — bright green (healthy/regen active)</li>
 *   <li>10–75% — blue (normal)</li>
 *   <li>0–10% — red (danger/armor penalty)</li>
 * </ul>
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class MoistureHudOverlay {

    private static final int BAR_WIDTH = 81;  // matches vanilla health/armor bar width
    private static final int BAR_HEIGHT = 5;
    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int COLOR_HIGH = 0xFF44DD66;    // >75% — green (regen)
    private static final int COLOR_NORMAL = 0xFF3399DD;  // 10-75% — blue
    private static final int COLOR_LOW = 0xFFDD4444;     // <10% — red (danger)
    private static final int LABEL_COLOR = 0xFFCCCCCC;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientMoistureState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float moisture = ClientMoistureState.get();

        // Position: right side above hotbar, same Y as air bubbles
        int x = screenW / 2 + 10;
        int y = screenH - 49;

        // Background
        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BG_COLOR);

        // Fill bar
        int fillW = Math.round(BAR_WIDTH * Math.max(0, Math.min(1, moisture)));
        if (fillW > 0) {
            int color;
            if (moisture > 0.75F) color = COLOR_HIGH;
            else if (moisture > 0.10F) color = COLOR_NORMAL;
            else color = COLOR_LOW;

            g.fill(x, y, x + fillW, y + BAR_HEIGHT, color);
        }

        // Label
        String label = "Moisture";
        int labelW = mc.font.width(label);
        g.text(mc.font, label, x + (BAR_WIDTH - labelW) / 2, y - 9, LABEL_COLOR, false);

        // Percentage text
        String pct = Math.round(moisture * 100) + "%";
        int pctW = mc.font.width(pct);
        g.text(mc.font, pct, x + (BAR_WIDTH - pctW) / 2, y + BAR_HEIGHT + 2, LABEL_COLOR, false);
    }
}
