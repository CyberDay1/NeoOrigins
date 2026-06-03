package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
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

    // ---- Apoli resource_bar.png sprite-sheet geometry (mirrors PowerHudRenderer) ----
    private static final Identifier RESOURCE_BAR_TEX =
        Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "textures/gui/resource_bar.png");
    private static final int TEX_SIZE = 256;
    private static final int BAR_WIDTH = 71;
    private static final int BAR_HEIGHT = 8;          // fill row height in the sheet
    private static final int FRAME_HEIGHT = 5;        // background frame height in the sheet
    private static final int ICON_SIZE = 8;
    private static final int BAR_INDEX_OFFSET = BAR_HEIGHT + 2;   // 10 — vertical stride between fill rows
    private static final int ICON_INDEX_OFFSET = ICON_SIZE + 1;   // 9  — horizontal stride between icons

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

            float frac = res.fraction();
            int fillW = Math.round(BAR_WIDTH * frac);

            if (res.barIndex() >= 0) {
                // Faithful Apoli render. Honour a pack-declared sprite_location (community
                // sheets are restyled bars at the same coordinates); fall back to our
                // vendored default sheet when none is given or the id is malformed.
                Identifier tex = resolveSheet(res.spriteLocation());
                int barV = BAR_HEIGHT + res.barIndex() * BAR_INDEX_OFFSET;          // fill row v
                int iconU = (BAR_WIDTH + 2) + res.iconIndex() * ICON_INDEX_OFFSET;  // icon column u
                // Background frame — top 71x5 strip of the sheet.
                g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 0.0f,
                    BAR_WIDTH, FRAME_HEIGHT, BAR_WIDTH, FRAME_HEIGHT, TEX_SIZE, TEX_SIZE);
                // Fill portion — overhangs the frame upward by 2px, exactly as Apoli draws it.
                if (fillW > 0) {
                    g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y - 2, 0.0f, (float) barV,
                        fillW, BAR_HEIGHT, fillW, BAR_HEIGHT, TEX_SIZE, TEX_SIZE);
                }
                // Icon — to the left of the bar, on the same row as the fill.
                g.blit(RenderPipelines.GUI_TEXTURED, tex, x - ICON_SIZE - 2, y - 2,
                    (float) iconU, (float) barV, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, TEX_SIZE, TEX_SIZE);
            } else {
                // Native NeoOrigins resource (no Apoli sprite index): color-tinted fill in a plain frame.
                g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + FRAME_HEIGHT + 1, BORDER_COLOR);
                g.fill(x, y, x + BAR_WIDTH, y + FRAME_HEIGHT, BG_COLOR);
                if (fillW > 0) {
                    g.fill(x, y, x + fillW, y + FRAME_HEIGHT, res.color());
                }
            }

            // Label above bar
            String label = res.label();
            int labelW = mc.font.width(label);
            g.text(mc.font, label, x + (BAR_WIDTH - labelW) / 2, y - 9, LABEL_COLOR, false);

            // Value readout below bar
            String readout = res.value() + "/" + res.max();
            int readoutW = mc.font.width(readout);
            g.text(mc.font, readout, x + (BAR_WIDTH - readoutW) / 2, y + FRAME_HEIGHT + 2, LABEL_COLOR, false);

            defaultIdx++;
        }
    }

    /**
     * Resolves the sprite sheet to render a bar against. An empty/blank id (native
     * resources, or Apoli resources with no {@code sprite_location}) uses the vendored
     * default sheet. A pack-declared id is passed through verbatim — the texture is
     * normally provided by the source mod/datapack; a malformed id falls back to default.
     */
    private static Identifier resolveSheet(String spriteLocation) {
        if (spriteLocation == null || spriteLocation.isBlank()) return RESOURCE_BAR_TEX;
        Identifier parsed = Identifier.tryParse(spriteLocation);
        return parsed != null ? parsed : RESOURCE_BAR_TEX;
    }
}
