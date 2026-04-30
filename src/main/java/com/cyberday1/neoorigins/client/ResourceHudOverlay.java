package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Renders resource bars on the HUD for origins that declare resources
 * (mana, stamina, rage, etc.). Multiple bars stack vertically above the
 * hotbar on the left side (opposite the moisture bar).
 *
 * <p>Each bar shows a label, a filled progress strip using the resource's
 * configured color, and a numeric value readout.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class ResourceHudOverlay {

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_SPACING = 16;  // vertical gap between stacked bars
    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int LABEL_COLOR = 0xFFCCCCCC;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        var resources = ClientResourceState.getResources();
        if (resources.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Position: left side above hotbar (mirrors moisture on right)
        int baseX = screenW / 2 - 91;
        int baseY = screenH - 49;

        int idx = 0;
        for (var entry : resources.entrySet()) {
            var res = entry.getValue();
            int y = baseY - (idx * BAR_SPACING);

            // Border + background
            g.fill(baseX - 1, y - 1, baseX + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
            g.fill(baseX, y, baseX + BAR_WIDTH, y + BAR_HEIGHT, BG_COLOR);

            // Fill bar
            float frac = res.fraction();
            int fillW = Math.round(BAR_WIDTH * frac);
            if (fillW > 0) {
                g.fill(baseX, y, baseX + fillW, y + BAR_HEIGHT, res.color());
            }

            // Label above bar
            String label = res.label();
            int labelW = mc.font.width(label);
            g.text(mc.font, label, baseX + (BAR_WIDTH - labelW) / 2, y - 9, LABEL_COLOR, false);

            // Value readout below bar
            String readout = res.value() + "/" + res.max();
            int readoutW = mc.font.width(readout);
            g.text(mc.font, readout, baseX + (BAR_WIDTH - readoutW) / 2, y + BAR_HEIGHT + 2, LABEL_COLOR, false);

            idx++;
        }
    }
}
