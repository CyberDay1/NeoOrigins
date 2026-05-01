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
 * (mana, stamina, rage, etc.). Each bar's position can be customised
 * via the in-game HUD editor (keybind: Edit HUD).
 *
 * <p>Bars automatically hide when full and reappear when the resource
 * drops below maximum.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class ResourceHudOverlay {

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_SPACING = 16;  // vertical gap between stacked bars (default layout)
    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int LABEL_COLOR = 0xFFCCCCCC;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (com.cyberday1.neoorigins.NeoOriginsConfig.isResourceBarsDisabled()) return;
        var resources = ClientResourceState.getResources();
        if (resources.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // Don't render bars while the editor is open — the editor draws them itself
        if (mc.screen instanceof ResourceHudEditorScreen) return;

        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Default base position for bars without a saved override
        int defaultBaseX = screenW / 2 - 91;
        int defaultBaseY = screenH - 49;

        int defaultIdx = 0;
        for (var entry : resources.entrySet()) {
            var res = entry.getValue();

            // Hide when full
            if (res.fraction() >= 1.0f) {
                defaultIdx++;
                continue;
            }

            // Look up saved position, fall back to default stacked layout
            ResourceHudPositions.Pos saved = ResourceHudPositions.get(entry.getKey());
            int x, y;
            if (saved != null) {
                x = Math.round(saved.xPct() * screenW);
                y = Math.round(saved.yPct() * screenH);
            } else {
                x = defaultBaseX;
                y = defaultBaseY - (defaultIdx * BAR_SPACING);
            }

            // Border + background
            g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
            g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BG_COLOR);

            // Fill bar
            float frac = res.fraction();
            int fillW = Math.round(BAR_WIDTH * frac);
            if (fillW > 0) {
                g.fill(x, y, x + fillW, y + BAR_HEIGHT, res.color());
            }

            // Label above bar
            String label = res.label();
            int labelW = mc.font.width(label);
            g.text(mc.font, label, x + (BAR_WIDTH - labelW) / 2, y - 9, LABEL_COLOR, false);

            // Value readout below bar
            String readout = res.value() + "/" + res.max();
            int readoutW = mc.font.width(readout);
            g.text(mc.font, readout, x + (BAR_WIDTH - readoutW) / 2, y + BAR_HEIGHT + 2, LABEL_COLOR, false);

            defaultIdx++;
        }
    }
}
