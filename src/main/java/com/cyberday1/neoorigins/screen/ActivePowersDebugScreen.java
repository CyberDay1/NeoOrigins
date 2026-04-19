package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 3 (2.0) debug screen: live view of the local player's granted powers
 * (with toggle state) and active capability tags. Pulls entirely from
 * {@link ClientActivePowers}, which is synced from the server via
 * {@code SyncActivePowersPayload}.
 *
 * <p>Opened from the Debug button on {@link OriginInfoScreen}. Purely client-side,
 * no permission gate required — displayed data is already authoritative on the client.
 */
public class ActivePowersDebugScreen extends Screen {

    private static final int PANEL_TOP = 32;
    private static final int PANEL_BTM_MARGIN = 32;
    private static final int PAD = 10;
    private static final int LINE_H = 10;

    private final Screen parent;

    private int panelX, panelW, panelBottom;
    private int scrollOffset = 0;
    private int contentH = 0;

    public ActivePowersDebugScreen(Screen parent) {
        super(Component.translatable("screen.neoorigins.debug_powers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 40, 420);
        panelX = (width - panelW) / 2;
        panelBottom = height - PANEL_BTM_MARGIN;

        addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.close"), b -> onClose())
            .bounds(width / 2 - 40, height - 24, 80, 20).build());

        recomputeContentHeight();
    }

    private void recomputeContentHeight() {
        Map<ResourceLocation, Boolean> powers = ClientActivePowers.all();
        Set<String> caps = ClientActivePowers.activeCapabilities();
        int h = PAD + 9 + 6;
        h += Math.max(1, caps.size()) * LINE_H;
        h += 10;
        h += 9 + 6;
        h += Math.max(1, powers.size()) * LINE_H;
        contentH = h + PAD;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);

        g.drawCenteredString(font, Component.translatable("screen.neoorigins.debug_powers"),
            width / 2, 12, 0xFFFFFFFF);

        g.fill(panelX, PANEL_TOP, panelX + panelW, panelBottom, 0xFF09091A);
        g.renderOutline(panelX - 1, PANEL_TOP - 1, panelW + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        int scrollTop = PANEL_TOP + 2;
        int scrollBottom = panelBottom - 2;
        int scrollAreaH = scrollBottom - scrollTop;
        int maxScroll = Math.max(0, contentH - scrollAreaH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        g.enableScissor(panelX + 1, scrollTop, panelX + panelW - 5, scrollBottom);
        int sy = scrollTop - scrollOffset + PAD;

        g.drawString(font, Component.translatable("gui.neoorigins.debug.capabilities_header"),
            panelX + PAD, sy, 0xFFCCCCDD, false);
        sy += 9 + 6;
        Set<String> caps = ClientActivePowers.activeCapabilities();
        if (caps.isEmpty()) {
            g.drawString(font, Component.translatable("gui.neoorigins.debug.none"),
                panelX + PAD + 8, sy, 0xFF556677, false);
            sy += LINE_H;
        } else {
            List<String> sorted = new ArrayList<>(caps);
            sorted.sort(String::compareTo);
            for (String cap : sorted) {
                g.fill(panelX + PAD, sy + 3, panelX + PAD + 3, sy + 6, 0xFF4A90D9);
                g.drawString(font, Component.literal(cap), panelX + PAD + 8, sy, 0xFF7AACDA, false);
                sy += LINE_H;
            }
        }

        sy += 10;

        g.drawString(font, Component.translatable("gui.neoorigins.debug.powers_header"),
            panelX + PAD, sy, 0xFFCCCCDD, false);
        sy += 9 + 6;
        Map<ResourceLocation, Boolean> powers = ClientActivePowers.all();
        if (powers.isEmpty()) {
            g.drawString(font, Component.translatable("gui.neoorigins.debug.none"),
                panelX + PAD + 8, sy, 0xFF556677, false);
        } else {
            List<Map.Entry<ResourceLocation, Boolean>> entries = new ArrayList<>(powers.entrySet());
            entries.sort((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
            for (Map.Entry<ResourceLocation, Boolean> entry : entries) {
                boolean active = Boolean.TRUE.equals(entry.getValue());
                int dotColor = active ? 0xFF44CC66 : 0xFF886644;
                int textColor = active ? 0xFFCCDDCC : 0xFF777788;
                g.fill(panelX + PAD, sy + 3, panelX + PAD + 3, sy + 6, dotColor);
                String label = entry.getKey().toString() + (active ? "" : "  (off)");
                g.drawString(font, Component.literal(label), panelX + PAD + 8, sy, textColor, false);
                sy += LINE_H;
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int barX = panelX + panelW - 4;
            int thumbH = Math.max(14, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) scrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 2, scrollBottom, 0xFF1A1A30);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF4A90D9);
        }

        String footer = "powers: " + powers.size() + "   caps: " + caps.size();
        g.drawString(font, Component.literal(footer), panelX + PAD, panelBottom + 6, 0xFF556677, false);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= panelX && mx <= panelX + panelW && my >= PANEL_TOP && my <= panelBottom) {
            int scrollAreaH = (panelBottom - 2) - (PANEL_TOP + 2);
            int maxScroll = Math.max(0, contentH - scrollAreaH);
            scrollOffset = Mth.clamp(scrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
