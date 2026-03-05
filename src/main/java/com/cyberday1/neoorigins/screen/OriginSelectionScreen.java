package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class OriginSelectionScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_TOP          = 44;
    private static final int PANEL_BTM_MARGIN   = 32;
    private static final int LIST_BTN_H         = 22;
    private static final int LIST_BTN_GAP       = 2;
    private static final int LEFT_W             = 164;
    private static final int PANEL_GAP          = 8;
    private static final int DETAIL_PAD         = 10;
    // Fixed header height inside the right panel (icon + name + impact + spacing)
    private static final int HEADER_H           = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10; // 76
    // Impact dots
    private static final int DOT_SIZE           = 5;
    private static final int DOT_SPACING        = 8;
    private static final int DOT_COUNT          = 4;

    // ── Screen state ──────────────────────────────────────────────────────────
    private final boolean isOrb;
    private List<OriginLayer> pendingLayers     = new ArrayList<>();
    private int currentLayerIndex               = 0;
    private Identifier selectedOriginId         = null;
    private int listScrollOffset                = 0;
    private int detailScrollOffset              = 0;

    private final List<Identifier>   availableOriginIds = new ArrayList<>();
    private final List<OriginButton> originButtons      = new ArrayList<>();

    // Computed geometry (set in init)
    private int panelX, panelBottom, rightX, rightW, listVisibleCount;

    // Cached detail content for the currently selected origin
    private List<FormattedCharSequence> descLines  = List.of();
    private List<String>                powerNames = List.of();
    private int                         detailContentH = 0;

    // Bottom buttons
    private Button confirmButton, backButton, randomButton;

    public OriginSelectionScreen(boolean isOrb) {
        super(Component.translatable("screen.neoorigins.choose_origin"));
        this.isOrb = isOrb;
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        pendingLayers = LayerDataManager.INSTANCE.getSortedLayers().stream()
            .filter(l -> !l.hidden())
            .filter(l -> !ClientOriginState.getOrigins().containsKey(l.id()))
            .toList();

        if (pendingLayers.isEmpty()) { onClose(); return; }

        int totalW  = Math.min(width - 40, 660);
        panelX      = (width - totalW) / 2;
        panelBottom = height - PANEL_BTM_MARGIN;
        rightX      = panelX + LEFT_W + PANEL_GAP;
        rightW      = totalW - LEFT_W - PANEL_GAP;
        listVisibleCount = Math.max(1, (panelBottom - PANEL_TOP) / (LIST_BTN_H + LIST_BTN_GAP));

        rebuildLayer();
    }

    /** Full reset for a new layer — clears selection, scroll, and rebuilds everything. */
    private void rebuildLayer() {
        availableOriginIds.clear();
        listScrollOffset   = 0;
        detailScrollOffset = 0;

        if (currentLayerIndex >= pendingLayers.size()) { onClose(); return; }

        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        for (var co : layer.origins()) {
            if (OriginDataManager.INSTANCE.hasOrigin(co.origin()))
                availableOriginIds.add(co.origin());
        }

        refreshOriginWidgets();
        updateDetailCache();
    }

    /** Rebuild all widgets (called on layer change, scroll, or selection change). */
    private void refreshOriginWidgets() {
        clearWidgets();
        originButtons.clear();

        // Origin list buttons (left panel)
        int end  = Math.min(availableOriginIds.size(), listScrollOffset + listVisibleCount);
        int btnY = PANEL_TOP;
        for (int i = listScrollOffset; i < end; i++) {
            Identifier id     = availableOriginIds.get(i);
            Origin     origin = OriginDataManager.INSTANCE.getOrigin(id);
            if (origin == null) continue;
            var btn = new OriginButton(panelX, btnY, LEFT_W, LIST_BTN_H, origin, b -> selectOrigin(id));
            btn.setSelected(id.equals(selectedOriginId));
            originButtons.add(btn);
            addRenderableWidget(btn);
            btnY += LIST_BTN_H + LIST_BTN_GAP;
        }

        // Bottom action buttons
        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        int cy = height - 24;
        int cx = width / 2;

        randomButton = Button.builder(Component.translatable("button.neoorigins.random"), b -> {
            if (!layer.allowRandom() || availableOriginIds.isEmpty()) return;
            selectOrigin(availableOriginIds.get((int) (Math.random() * availableOriginIds.size())));
        }).bounds(panelX, cy, 70, 20).build();
        randomButton.visible = layer.allowRandom();
        addRenderableWidget(randomButton);

        backButton = Button.builder(Component.literal("< Back"), b -> {
            if (currentLayerIndex > 0) {
                currentLayerIndex--;
                selectedOriginId = null;
                rebuildLayer();
            }
        }).bounds(cx - 92, cy, 80, 20).build();
        backButton.active = currentLayerIndex > 0;
        addRenderableWidget(backButton);

        confirmButton = Button.builder(Component.literal("Confirm >"), b -> confirmSelection())
            .bounds(cx + 12, cy, 80, 20).build();
        confirmButton.active = selectedOriginId != null;
        addRenderableWidget(confirmButton);
    }

    // ── Selection & confirmation ───────────────────────────────────────────────

    private void selectOrigin(Identifier id) {
        selectedOriginId   = id;
        detailScrollOffset = 0;
        originButtons.forEach(b -> b.setSelected(b.getOrigin().id().equals(id)));
        if (confirmButton != null) confirmButton.active = true;
        updateDetailCache();
    }

    private void confirmSelection() {
        if (selectedOriginId == null) return;
        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        ClientPacketDistributor.sendToServer(new ChooseOriginPayload(layer.id(), selectedOriginId));
        var updated = new java.util.HashMap<>(ClientOriginState.getOrigins());
        updated.put(layer.id(), selectedOriginId);
        ClientOriginState.setOrigins(updated, false);

        currentLayerIndex++;
        selectedOriginId = null;
        if (currentLayerIndex >= pendingLayers.size()) onClose();
        else rebuildLayer();
    }

    // ── Detail cache ──────────────────────────────────────────────────────────

    private void updateDetailCache() {
        if (selectedOriginId == null) {
            descLines = List.of(); powerNames = List.of(); detailContentH = 0; return;
        }
        Origin origin = OriginDataManager.INSTANCE.getOrigin(selectedOriginId);
        if (origin == null) { descLines = List.of(); powerNames = List.of(); return; }

        int descW = rightW - DETAIL_PAD * 2 - 6; // leave room for scrollbar
        descLines = font.split(origin.description(), descW);

        powerNames = new ArrayList<>();
        for (Identifier powerId : origin.powers())
            powerNames.add(formatPowerId(powerId));

        // separator(8) + desc lines + gap(8) + "Powers:" header(9+4) + power rows + pad(6)
        detailContentH = 8
            + descLines.size() * 10 + 8
            + (powerNames.isEmpty() ? 0 : 9 + 4 + powerNames.size() * 11)
            + 6;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Screen background
        g.fill(0, 0, width, height, 0xCC060610);

        if (currentLayerIndex >= pendingLayers.size()) return;
        OriginLayer layer = pendingLayers.get(currentLayerIndex);

        // Title row
        g.drawCenteredString(font, getTitle(), width / 2, 14, 0xFFFFFFFF);
        g.drawCenteredString(font, layer.name(), width / 2, 26, 0xFF8888AA);

        // Progress indicator
        String prog = (currentLayerIndex + 1) + " / " + pendingLayers.size();
        g.drawString(font, prog, width - 10 - font.width(prog), 26, 0xFF555577, false);

        // Left panel background
        g.fill(panelX - 1, PANEL_TOP - 1, panelX + LEFT_W + 1, panelBottom + 1, 0xFF0E0E1C);
        g.renderOutline(panelX - 1, PANEL_TOP - 1, LEFT_W + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        // Scroll hint beneath left panel
        if (getMaxListScroll() > 0) {
            String hint = "scroll for more";
            g.drawString(font, hint, panelX, panelBottom + 3, 0xFF334466, false);
        }

        // Render widgets (list buttons + bottom buttons)
        super.render(g, mouseX, mouseY, partial);

        // Right detail panel (drawn after widgets so it's on top of the list bg only)
        renderDetailPanel(g);
    }

    private void renderDetailPanel(GuiGraphics g) {
        // Panel background
        g.fill(rightX, PANEL_TOP, rightX + rightW, panelBottom, 0xFF09091A);
        g.renderOutline(rightX - 1, PANEL_TOP - 1, rightW + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        if (selectedOriginId == null) {
            int cx = rightX + rightW / 2;
            int cy = PANEL_TOP + (panelBottom - PANEL_TOP) / 2;
            g.drawCenteredString(font,
                Component.literal("Select an origin to view details"),
                cx, cy - 4, 0xFF333355);
            return;
        }

        Origin origin = OriginDataManager.INSTANCE.getOrigin(selectedOriginId);
        if (origin == null) return;

        int cx = rightX + rightW / 2;
        int y  = PANEL_TOP + DETAIL_PAD;

        // ── Fixed header (always visible) ─────────────────────────────────────

        // Icon — glowing 16×16 icon centered in a 32×32 decorated panel
        var item = BuiltInRegistries.ITEM.getValue(origin.icon());
        int iconPanelX = cx - 16;
        int iconPanelY = y;
        g.fill(iconPanelX, iconPanelY, iconPanelX + 32, iconPanelY + 32, 0xFF0D1830);
        g.renderOutline(iconPanelX, iconPanelY, 32, 32, 0xFF4A90D9);
        if (item != null) {
            g.renderItem(new ItemStack(item), iconPanelX + 8, iconPanelY + 8);
        }
        y += 32 + 6;

        // Origin name
        g.drawCenteredString(font, origin.name(), cx, y, 0xFFFFFFFF);
        y += 9 + 4;

        // Impact dots + label
        drawImpactRow(g, cx, y, origin.impact());
        y += 5 + 10; // y is now PANEL_TOP + HEADER_H

        // ── Scrollable content area ───────────────────────────────────────────

        int scrollTop    = PANEL_TOP + HEADER_H;
        int scrollBottom = panelBottom - 2;
        int scrollAreaH  = scrollBottom - scrollTop;
        int maxScroll    = Math.max(0, detailContentH - scrollAreaH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxScroll);

        g.enableScissor(rightX + 1, scrollTop, rightX + rightW - 5, scrollBottom);

        int sy = scrollTop - detailScrollOffset;

        // Thin separator line
        g.fill(rightX + DETAIL_PAD, sy + 3, rightX + rightW - DETAIL_PAD - 6, sy + 4, 0xFF252540);
        sy += 8;

        // Description
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, rightX + DETAIL_PAD, sy, 0xFF9999BB, false);
            sy += 10;
        }
        sy += 8;

        // Powers list
        if (!powerNames.isEmpty()) {
            g.drawString(font, "Powers", rightX + DETAIL_PAD, sy, 0xFFCCCCDD, false);
            sy += 9 + 4;
            for (String name : powerNames) {
                // Bullet dot in blue
                g.fill(rightX + DETAIL_PAD, sy + 3, rightX + DETAIL_PAD + 3, sy + 6, 0xFF4A90D9);
                g.drawString(font, name, rightX + DETAIL_PAD + 8, sy, 0xFF7AACDA, false);
                sy += 11;
            }
        }

        g.disableScissor();

        // Scroll bar (only when content overflows)
        if (maxScroll > 0) {
            int barX  = rightX + rightW - 4;
            int barH  = scrollAreaH;
            int thumbH = Math.max(14, barH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) detailScrollOffset * (barH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 2, scrollBottom, 0xFF1A1A30);
            g.fill(barX, thumbY,   barX + 2, thumbY + thumbH, 0xFF4A90D9);
        }
    }

    private void drawImpactRow(GuiGraphics g, int cx, int y, Impact impact) {
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0     = cx - totalW / 2;
        int filled = impact.getDotCount();
        for (int i = 0; i < DOT_COUNT; i++) {
            int color = i < filled ? 0xFFFF8822 : 0xFF252540;
            g.fill(x0 + i * DOT_SPACING, y, x0 + i * DOT_SPACING + DOT_SIZE, y + DOT_SIZE, color);
        }
        // "Impact: Low/Medium/High/None" label to the right of dots
        Component label = Component.translatable("origins.gui.impact.impact")
            .append(": ")
            .append(switch (impact) {
                case NONE   -> Component.translatable("origins.gui.impact.none");
                case LOW    -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH   -> Component.translatable("origins.gui.impact.high");
            });
        g.drawString(font, label, cx + totalW / 2 + 6, y - 1, 0xFF666688, false);
    }

    // ── Scrolling ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (isOverLeftPanel(mx, my)) {
            int next = Mth.clamp(listScrollOffset + (sy > 0 ? -1 : 1), 0, getMaxListScroll());
            if (next != listScrollOffset) { listScrollOffset = next; refreshOriginWidgets(); }
            return true;
        }
        if (isOverRightPanel(mx, my)) {
            int scrollAreaH = (panelBottom - 2) - (PANEL_TOP + HEADER_H);
            int maxScroll   = Math.max(0, detailContentH - scrollAreaH);
            detailScrollOffset = Mth.clamp(detailScrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private boolean isOverLeftPanel(double mx, double my) {
        return mx >= panelX && mx <= panelX + LEFT_W && my >= PANEL_TOP && my <= panelBottom;
    }
    private boolean isOverRightPanel(double mx, double my) {
        return mx >= rightX && mx <= rightX + rightW && my >= PANEL_TOP && my <= panelBottom;
    }
    private int getMaxListScroll() {
        return Math.max(0, availableOriginIds.size() - listVisibleCount);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Converts a power Identifier path to a human-readable Title Case name. */
    private static String formatPowerId(Identifier id) {
        String[] parts = id.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
