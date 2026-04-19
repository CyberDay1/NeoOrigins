package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.client.ClientPowerCache;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.EditorTogglePowerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 2.0 phase 4: in-game origin editor &amp; power tester.
 *
 * <p>Two panels:
 * <ul>
 *   <li>Top — list of origin layers with the current origin name and a "Change" button
 *       that opens the standard {@link OriginSelectionScreen} with {@code forceReselect=true}.</li>
 *   <li>Bottom — list of granted toggle powers with an on/off switch that sends
 *       {@link EditorTogglePowerPayload} to the server.</li>
 * </ul>
 *
 * <p>All data is pulled from already-synced client state ({@link ClientOriginState},
 * {@link ClientActivePowers}, {@link ClientPowerCache}), so opening the editor has no
 * server round-trip until the user actually makes a change.
 */
public class OriginEditorScreen extends Screen {

    private static final int PANEL_TOP = 32;
    private static final int PANEL_BTM_MARGIN = 32;
    private static final int PAD = 10;
    private static final int ROW_H = 22;

    private final Screen parent;

    private int panelX, panelW, panelBottom;
    private int splitY;
    private int powersScrollOffset = 0;

    public OriginEditorScreen(Screen parent) {
        super(Component.translatable("screen.neoorigins.origin_editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 40, 440);
        panelX = (width - panelW) / 2;
        panelBottom = height - PANEL_BTM_MARGIN;
        splitY = PANEL_TOP + (panelBottom - PANEL_TOP) / 2;

        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();

        int y = PANEL_TOP + 22;
        Map<ResourceLocation, ResourceLocation> origins = ClientOriginState.getOrigins();
        List<ResourceLocation> orderedLayers = new ArrayList<>();
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!layer.hidden()) orderedLayers.add(layer.id());
        }
        int layersPanelBottom = splitY - 4;
        for (ResourceLocation layerId : orderedLayers) {
            if (y + ROW_H > layersPanelBottom) break;
            ResourceLocation originId = origins.get(layerId);
            String btnLabel = originId != null
                ? Component.translatable("gui.neoorigins.editor.change").getString()
                : Component.translatable("gui.neoorigins.editor.pick").getString();
            int btnW = 70;
            int btnX = panelX + panelW - PAD - btnW;
            int btnY = y;
            addRenderableWidget(Button.builder(Component.literal(btnLabel),
                    b -> openOriginPicker())
                .bounds(btnX, btnY, btnW, 18).build());
            y += ROW_H;
        }

        int powersTop = splitY + 22;
        int powersBottom = panelBottom - 8;
        int py = powersTop - powersScrollOffset;
        List<ResourceLocation> togglePowers = collectTogglePowers();
        for (ResourceLocation powerId : togglePowers) {
            if (py + 18 > powersBottom) break;
            if (py < powersTop - 18) { py += ROW_H; continue; }
            boolean on = ClientActivePowers.isActive(powerId);
            String btnLabel = on
                ? Component.translatable("gui.neoorigins.editor.on").getString()
                : Component.translatable("gui.neoorigins.editor.off").getString();
            int btnW = 44;
            int btnX = panelX + panelW - PAD - btnW;
            addRenderableWidget(Button.builder(Component.literal(btnLabel),
                    b -> toggle(powerId))
                .bounds(btnX, py, btnW, 18).build());
            py += ROW_H;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.close"), b -> onClose())
            .bounds(width / 2 - 40, height - 24, 80, 20).build());
    }

    private void openOriginPicker() {
        Minecraft.getInstance().setScreen(new OriginSelectionScreen(false, true));
    }

    private void toggle(ResourceLocation powerId) {
        PacketDistributor.sendToServer(new EditorTogglePowerPayload(powerId));
        refreshWidgets();
    }

    private List<ResourceLocation> collectTogglePowers() {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : ClientActivePowers.all().keySet()) {
            ClientPowerCache.Entry entry = ClientPowerCache.get(id);
            if (entry != null && entry.toggle()) out.add(id);
        }
        out.sort((a, b) -> a.toString().compareTo(b.toString()));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);

        g.drawCenteredString(font, Component.translatable("screen.neoorigins.origin_editor"),
            width / 2, 12, 0xFFFFFFFF);

        g.fill(panelX, PANEL_TOP, panelX + panelW, splitY - 4, 0xFF09091A);
        g.renderOutline(panelX - 1, PANEL_TOP - 1, panelW + 2, (splitY - 4) - PANEL_TOP + 2, 0xFF252540);
        g.drawString(font, Component.translatable("gui.neoorigins.editor.layers_header"),
            panelX + PAD, PAD + PANEL_TOP - 6, 0xFFCCCCDD, false);

        g.fill(panelX, splitY, panelX + panelW, panelBottom, 0xFF09091A);
        g.renderOutline(panelX - 1, splitY - 1, panelW + 2, panelBottom - splitY + 2, 0xFF252540);
        g.drawString(font, Component.translatable("gui.neoorigins.editor.powers_header"),
            panelX + PAD, splitY + PAD - 2, 0xFFCCCCDD, false);

        drawLayerLabels(g);
        drawPowerLabels(g);

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawLayerLabels(GuiGraphics g) {
        int y = PANEL_TOP + 22;
        int layersBottom = splitY - 4;
        Map<ResourceLocation, ResourceLocation> origins = ClientOriginState.getOrigins();
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (layer.hidden()) continue;
            if (y + ROW_H > layersBottom) break;
            ResourceLocation layerId = layer.id();
            String layerName = layerId.getPath();
            String originName;
            ResourceLocation originId = origins.get(layerId);
            if (originId != null) {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
                originName = origin != null ? origin.name().getString() : originId.toString();
            } else {
                originName = Component.translatable("gui.neoorigins.editor.not_chosen").getString();
            }
            g.drawString(font, Component.literal(layerName + ":"), panelX + PAD, y + 5, 0xFF9999BB, false);
            g.drawString(font, Component.literal(originName), panelX + PAD + 70, y + 5, 0xFFDDDDEE, false);
            y += ROW_H;
        }
    }

    private void drawPowerLabels(GuiGraphics g) {
        int powersTop = splitY + 22;
        int powersBottom = panelBottom - 8;
        g.enableScissor(panelX + 1, powersTop - 4, panelX + panelW - 1, powersBottom);
        int py = powersTop - powersScrollOffset;
        List<ResourceLocation> togglePowers = collectTogglePowers();
        for (ResourceLocation powerId : togglePowers) {
            if (py + 18 > powersBottom && py > powersTop) break;
            ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
            String label = entry != null && entry.name() != null
                ? entry.name().getString()
                : powerId.toString();
            boolean on = ClientActivePowers.isActive(powerId);
            int dotColor = on ? 0xFF44CC66 : 0xFF886644;
            g.fill(panelX + PAD, py + 7, panelX + PAD + 3, py + 10, dotColor);
            g.drawString(font, Component.literal(label), panelX + PAD + 8, py + 5, on ? 0xFFDDDDEE : 0xFF888899, false);
            py += ROW_H;
        }
        g.disableScissor();

        int contentH = togglePowers.size() * ROW_H;
        int viewH = powersBottom - powersTop;
        if (contentH > viewH) {
            int barX = panelX + panelW - 4;
            int maxScroll = contentH - viewH;
            int thumbH = Math.max(12, viewH * viewH / contentH);
            int thumbY = powersTop + (int) ((long) powersScrollOffset * (viewH - thumbH) / Math.max(1, maxScroll));
            g.fill(barX, powersTop, barX + 2, powersBottom, 0xFF1A1A30);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF4A90D9);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= panelX && mx <= panelX + panelW && my >= splitY && my <= panelBottom) {
            int powersTop = splitY + 22;
            int powersBottom = panelBottom - 8;
            int viewH = powersBottom - powersTop;
            int contentH = collectTogglePowers().size() * ROW_H;
            int maxScroll = Math.max(0, contentH - viewH);
            powersScrollOffset = Mth.clamp(powersScrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            refreshWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
