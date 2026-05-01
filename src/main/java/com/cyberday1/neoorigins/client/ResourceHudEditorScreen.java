package com.cyberday1.neoorigins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drag-and-drop editor for resource bar HUD positions.
 * Bars render at their current positions; click and drag to move.
 * Positions are saved on close.
 */
public class ResourceHudEditorScreen extends Screen {

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;
    private static final int GRAB_PAD = 6;       // extra grab area around bar
    private static final int LABEL_HEIGHT = 9;
    private static final int READOUT_HEIGHT = 9;
    // Total visual height of a bar widget: label + bar + readout
    private static final int WIDGET_HEIGHT = LABEL_HEIGHT + BAR_HEIGHT + READOUT_HEIGHT;

    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int BORDER_DRAG = 0xFF55FF55;
    private static final int LABEL_COLOR = 0xFFCCCCCC;

    private record BarWidget(String id, ClientResourceState.ResourceEntry data, float xPct, float yPct) {}

    private final List<BarWidget> widgets = new ArrayList<>();
    private int dragging = -1;
    private double dragOffX, dragOffY;

    public ResourceHudEditorScreen() {
        super(Component.translatable("screen.neoorigins.hud_editor"));
    }

    @Override
    protected void init() {
        // Populate widgets from current resource state + saved positions
        var resources = ClientResourceState.getResources();
        int screenW = width;
        int screenH = height;
        int defaultX = screenW / 2 - 91;
        int defaultBaseY = screenH - 49;

        widgets.clear();
        int idx = 0;
        for (var entry : resources.entrySet()) {
            ResourceHudPositions.Pos saved = ResourceHudPositions.get(entry.getKey());
            float xPct, yPct;
            if (saved != null) {
                xPct = saved.xPct();
                yPct = saved.yPct();
            } else {
                int y = defaultBaseY - (idx * 16);
                xPct = (float) defaultX / screenW;
                yPct = (float) y / screenH;
            }
            widgets.add(new BarWidget(entry.getKey(), entry.getValue(), xPct, yPct));
            idx++;
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.neoorigins.hud_editor.reset"), b -> {
            for (var w : widgets) ResourceHudPositions.remove(w.id);
            ResourceHudPositions.save();
            rebuildFromDefaults();
        }).bounds(width / 2 - 50, 6, 100, 20).build());
    }

    private void rebuildFromDefaults() {
        var resources = ClientResourceState.getResources();
        int defaultX = width / 2 - 91;
        int defaultBaseY = height - 49;
        widgets.clear();
        int idx = 0;
        for (var entry : resources.entrySet()) {
            int y = defaultBaseY - (idx * 16);
            float xPct = (float) defaultX / width;
            float yPct = (float) y / height;
            widgets.add(new BarWidget(entry.getKey(), entry.getValue(), xPct, yPct));
            idx++;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Dim background
        g.fill(0, 0, width, height, 0x88000000);

        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < widgets.size(); i++) {
            var w = widgets.get(i);
            int bx = Math.round(w.xPct * width);
            int by = Math.round(w.yPct * height);

            boolean isDragging = (i == dragging);
            int border = isDragging ? BORDER_DRAG : BORDER_COLOR;

            // Border + background
            g.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, border);
            g.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, BG_COLOR);

            // Fill bar
            float frac = w.data.fraction();
            int fillW = Math.round(BAR_WIDTH * frac);
            if (fillW > 0) {
                g.fill(bx, by, bx + fillW, by + BAR_HEIGHT, w.data.color());
            }

            // Label above
            String label = w.data.label();
            int labelW = mc.font.width(label);
            g.drawString(mc.font, label, bx + (BAR_WIDTH - labelW) / 2, by - LABEL_HEIGHT, LABEL_COLOR, false);

            // Value below
            String readout = w.data.value() + "/" + w.data.max();
            int readoutW = mc.font.width(readout);
            g.drawString(mc.font, readout, bx + (BAR_WIDTH - readoutW) / 2, by + BAR_HEIGHT + 2, LABEL_COLOR, false);
        }

        // Title
        g.drawCenteredString(mc.font, title, width / 2, height - 16, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        // Find which bar was clicked (iterate in reverse so topmost wins)
        for (int i = widgets.size() - 1; i >= 0; i--) {
            var w = widgets.get(i);
            int bx = Math.round(w.xPct * width);
            int by = Math.round(w.yPct * height);
            if (mx >= bx - GRAB_PAD && mx <= bx + BAR_WIDTH + GRAB_PAD
                && my >= by - LABEL_HEIGHT - GRAB_PAD && my <= by + BAR_HEIGHT + READOUT_HEIGHT + GRAB_PAD) {
                dragging = i;
                dragOffX = mx - bx;
                dragOffY = my - by;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0 && button == 0) {
            float newX = (float) (mx - dragOffX) / width;
            float newY = (float) (my - dragOffY) / height;
            // Clamp so the bar doesn't go off screen
            newX = Math.max(0, Math.min(newX, 1.0f - (float) BAR_WIDTH / width));
            newY = Math.max((float) LABEL_HEIGHT / height, Math.min(newY, 1.0f - (float) (BAR_HEIGHT + READOUT_HEIGHT) / height));
            var old = widgets.get(dragging);
            widgets.set(dragging, new BarWidget(old.id, old.data, newX, newY));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0 && button == 0) {
            dragging = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void onClose() {
        // Persist all positions
        for (var w : widgets) {
            ResourceHudPositions.set(w.id, w.xPct, w.yPct);
        }
        ResourceHudPositions.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
