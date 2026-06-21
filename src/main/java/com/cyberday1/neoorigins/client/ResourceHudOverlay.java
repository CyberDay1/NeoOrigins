package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.util.Util;
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
    // Animated FX bars are narrower than native/Apoli bars (71). 60 is chosen so a
    // 0-100 resource's every-5 ticks land on exact 3px spacing (round(v*0.6)) — an
    // even ruler instead of the jagged 2/3px comb a non-dividing width produces.
    private static final int FX_BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 8;          // fill row height in the sheet
    private static final int FRAME_HEIGHT = 5;        // background frame height in the sheet
    private static final int FX_BAR_HEIGHT = 10;      // taller, self-contained box for animated FX bars
    private static final int ICON_SIZE = 8;
    private static final int BAR_INDEX_OFFSET = BAR_HEIGHT + 2;   // 10 — vertical stride between fill rows
    private static final int ICON_INDEX_OFFSET = ICON_SIZE + 1;   // 9  — horizontal stride between icons

    private static final int BAR_SPACING = 16;  // vertical gap between stacked bars (default layout)
    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int LABEL_COLOR = 0xFFCCCCCC;
    // Value scale ticks on animated bars: a major tick every 25 units, a minor every 5.
    private static final int TICK_MAJOR_STEP = 25;
    private static final int TICK_MINOR_STEP = 5;
    private static final int TICK_MAJOR_COLOR = 0xE6FFFFFF;  // bright — clear quarter markers
    private static final int TICK_MINOR_COLOR = 0x4DFFFFFF;   // faint — subtle fifths, recedes

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (ContentTogglesConfig.isResourceBarsDisabled()) return;
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

            // Hide when full — unless the bar opted into always_render, which
            // keeps a regenerating meter on-screen even when topped off.
            if (!res.alwaysShow() && res.fraction() >= 1.0f) {
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

            BarFxManager.BarFx fx = BarFxManager.get(res.animated());
            // Animated bars use a slightly narrower box than native/Apoli bars.
            int barW = (fx != null) ? FX_BAR_WIDTH : BAR_WIDTH;

            float frac = res.fraction();
            int fillW = Math.round(barW * frac);

            if (fx != null) {
                // Animated FX preset takes precedence over both the Apoli sprite
                // and the native color fill.
                renderAnimatedBar(g, fx, x, y, fillW, res.tint(), res.min(), res.max());
            } else if (res.barIndex() >= 0) {
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
            g.text(mc.font, label, x + (barW - labelW) / 2, y - 9, LABEL_COLOR, false);

            // Value readout below bar — animated FX bars are taller, so drop the
            // readout below their box rather than the standard frame height.
            String readout = res.value() + "/" + res.max();
            int readoutW = mc.font.width(readout);
            int barBottom = (fx != null ? FX_BAR_HEIGHT : FRAME_HEIGHT);
            g.text(mc.font, readout, x + (barW - readoutW) / 2, y + barBottom + 2, LABEL_COLOR, false);

            defaultIdx++;
        }
    }

    /**
     * Renders an animated FX-preset bar: a taller, self-contained box ({@link
     * #FX_BAR_HEIGHT}) with a dark element track across the full width, a brighter
     * {@code levelColor} backing under the filled portion, the preset's wisp strip
     * scrolling horizontally inside the filled portion (scissor-clipped to the box
     * and to {@code fillW}), and value scale ticks on top. An optional {@code tint}
     * (ARGB, 0 = none) multiplies the strip so one piece of art can be recoloured
     * per origin. {@code min}/{@code max} drive the tick spacing.
     *
     * <p>26.1 port: the old {@code GuiGraphics.setColor} multiply no longer exists;
     * the tint is passed as the ARGB color argument of the 13-arg
     * {@code blit(pipeline, ...)} overload instead (same multiply semantics).
     */
    private static void renderAnimatedBar(GuiGraphicsExtractor g, BarFxManager.BarFx fx,
                                          int x, int y, int fillW, int tint, int min, int max) {
        int top = y;
        int h = FX_BAR_HEIGHT;

        // Border + empty track across the full width.
        g.fill(x - 1, top - 1, x + FX_BAR_WIDTH + 1, top + h + 1, BORDER_COLOR);
        g.fill(x, top, x + FX_BAR_WIDTH, top + h, fx.trackColor());

        if (fillW > 0) {
            // Brighter backing under the filled portion so the level reads clearly
            // even in the transparent gaps between wisps.
            g.fill(x, top, x + fillW, top + h, fx.levelColor());

            // Scroll the strip at the box height, preserving its aspect ratio.
            int drawW = Math.max(1, Math.round(fx.tileW() * (float) h / fx.tileH()));
            // Scroll offset advances with wall-clock time, wrapping every drawW px so
            // the (seamless) strip loops. Time-based, NOT frame-based, so the drift
            // speed is independent of FPS.
            int off = Math.round((Util.getMillis() / 1000.0f) * fx.scrollSpeed()) % drawW;

            // tint==0 means "no tint" — draw the strip art as-is (opaque white).
            int color = (tint == 0) ? 0xFFFFFFFF : (0xFF000000 | (tint & 0xFFFFFF));

            // Clip to the filled window AND the box so the wisps stay inside the edges.
            g.enableScissor(x, top, x + fillW, top + h);
            // Tile copies across the filled window; start one tile left of the window
            // so the scroll never reveals a gap at the leading edge.
            for (int dx = -drawW; dx < fillW + drawW; dx += drawW) {
                g.blit(RenderPipelines.GUI_TEXTURED, fx.texture(), x + dx - off, top, 0.0f, 0.0f,
                    drawW, h, fx.tileW(), fx.tileH(), fx.tileW(), fx.tileH(), color);
            }
            g.disableScissor();
        }

        // Value scale ticks anchored to the TOP edge: minor every TICK_MINOR_STEP
        // units (very short notch), major every TICK_MAJOR_STEP units (a bit taller).
        int majorTickH = Math.max(1, Math.round(h * 0.3f));   // 3 @ h=10 (the old minor height)
        int minorTickH = Math.max(1, Math.round(h * 0.15f));  // 2 @ h=10 (~half the major)
        drawTicks(g, x, top, min, max, TICK_MINOR_STEP, minorTickH, TICK_MINOR_COLOR);
        drawTicks(g, x, top, min, max, TICK_MAJOR_STEP, majorTickH, TICK_MAJOR_COLOR);
    }

    /**
     * Draws interior value-scale ticks: a 1px vertical line every {@code step} value
     * units, {@code tickH} tall, anchored to the TOP of the bar. Endpoints
     * (min/max) are skipped — they coincide with the border. Skips the whole series
     * when the ticks would be denser than ~2px apart (unreadable at this bar width).
     */
    private static void drawTicks(GuiGraphicsExtractor g, int x, int top,
                                  int min, int max, int step, int tickH, int color) {
        int range = max - min;
        if (range <= 0 || step <= 0) return;
        float pxPerUnit = (float) FX_BAR_WIDTH / range;
        if (pxPerUnit * step < 2.0f) return; // too dense to read
        for (int v = step; v < range; v += step) {
            int tx = x + Math.round(v * pxPerUnit);
            g.fill(tx, top, tx + 1, top + tickH, color);
        }
    }

    // Memoised sprite-sheet resolutions (raw sprite_location string → sheet to use).
    // Cleared whenever a full resource sync re-applies (login / grant / reload), so
    // a texture that appears after a resource-pack reload is re-checked.
    private static final java.util.Map<String, Identifier> SHEET_CACHE = new java.util.HashMap<>();

    /** Invalidates the resolved-sheet cache; called from {@link ClientResourceState#apply}. */
    public static void clearSheetCache() { SHEET_CACHE.clear(); }

    /**
     * Resolves the sprite sheet to render a bar against. An empty/blank id (native
     * resources, or Apoli resources with no {@code sprite_location}) uses the vendored
     * default sheet. A pack-declared id is used verbatim only when its texture is
     * actually present in a loaded resource pack — community sheets (e.g.
     * {@code origins:textures/gui/community/...}) are normally shipped by the source
     * mod, so an absent one would otherwise render as the missing-texture block.
     * A malformed or absent id falls back to our default sheet, so the bar still
     * renders (and animates) at the requested {@code bar_index}.
     */
    private static Identifier resolveSheet(String spriteLocation) {
        if (spriteLocation == null || spriteLocation.isBlank()) return RESOURCE_BAR_TEX;
        Identifier cached = SHEET_CACHE.get(spriteLocation);
        if (cached != null) return cached;
        Identifier parsed = Identifier.tryParse(spriteLocation);
        Identifier resolved;
        if (parsed == null || Minecraft.getInstance().getResourceManager().getResource(parsed).isEmpty()) {
            resolved = RESOURCE_BAR_TEX;
        } else {
            resolved = parsed;
        }
        SHEET_CACHE.put(spriteLocation, resolved);
        return resolved;
    }
}
