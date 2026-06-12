package com.cyberday1.neoorigins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drag-and-drop editor for the NeoOrigins HUD: resource bars plus the
 * ability cluster. Click and drag to move; positions persist on close.
 *
 * <p>The ability cluster additionally supports:
 * <ul>
 *   <li><b>Rotate</b> — flips the cluster between horizontal and vertical
 *       (merged mode only; a single split slot has no orientation);</li>
 *   <li><b>Split / Merge</b> — split turns every slot into its own
 *       independently draggable element (keyed by power id); slots you never
 *       move keep their cluster-layout spot, and Merge returns to the single
 *       cluster without forgetting the per-slot positions;</li>
 *   <li><b>Resize</b> — the +/- buttons scale the selected element (the
 *       cluster, or in split mode the slot you last clicked) between 50% and
 *       200%; icon, sweep, countdown and labels scale together.</li>
 * </ul>
 * All geometry comes from {@link CooldownClusterLayout}, the same math the
 * HUD overlay renders and hit-tests with, so what you place here is exactly
 * what you get in-world.
 */
public class ResourceHudEditorScreen extends Screen {

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;
    private static final int GRAB_PAD = 6;       // extra grab area around bar
    private static final int LABEL_HEIGHT = 9;
    private static final int READOUT_HEIGHT = 9;

    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int BORDER_DRAG = 0xFF55FF55;
    private static final int BORDER_SELECTED = 0xFFFFD75A;
    private static final int LABEL_COLOR = 0xFFCCCCCC;
    private static final int CD_BAR_FILL = 0xFF4A90D9;

    private static final float SCALE_STEP = 0.25f;

    /**
     * One draggable element. {@code data != null} → resource bar.
     * {@code slot != null} → one split ability slot. Both null → the merged
     * ability cluster ({@code scale}/{@code vertical} only meaningful there
     * and on split slots; resource bars ignore them).
     */
    private record Widget(String id, ClientResourceState.ResourceEntry data,
                          CooldownHudOverlay.RenderSlot slot,
                          float xPct, float yPct, float scale, boolean vertical) {
        boolean isCluster() { return data == null && slot == null; }
        boolean isAbility() { return data == null; }
        Widget at(float x, float y)      { return new Widget(id, data, slot, x, y, scale, vertical); }
        Widget scaled(float s)           { return new Widget(id, data, slot, xPct, yPct, s, vertical); }
        Widget rotated()                 { return new Widget(id, data, slot, xPct, yPct, scale, !vertical); }
    }

    private final List<Widget> widgets = new ArrayList<>();
    /** Ability widgets (cluster or split slots) the user moved/scaled this session. */
    private final Set<String> touched = new HashSet<>();
    private boolean split;
    private int dragging = -1;
    private int selected = -1;           // ability widget targeted by +/- scale
    private double dragOffX, dragOffY;
    private Button rotateBtn, splitBtn;

    public ResourceHudEditorScreen() {
        super(Component.translatable("screen.neoorigins.hud_editor"));
    }

    @Override
    protected void init() {
        split = ResourceHudPositions.isSplitCooldown();
        rebuildWidgets(false);

        int cx = width / 2;
        rotateBtn = addRenderableWidget(Button.builder(
            Component.translatable("screen.neoorigins.hud_editor.rotate"), b -> {
                for (int i = 0; i < widgets.size(); i++) {
                    if (widgets.get(i).isCluster()) {
                        widgets.set(i, widgets.get(i).rotated());
                        touched.add(widgets.get(i).id());
                    }
                }
            }).bounds(cx - 155, 6, 70, 20).build());
        splitBtn = addRenderableWidget(Button.builder(splitLabel(), b -> {
            persistAbilityWidgets();           // keep current drag state across the rebuild
            split = !split;
            ResourceHudPositions.setSplitCooldown(split);
            rebuildWidgets(true);
            splitBtn.setMessage(splitLabel());
            rotateBtn.active = !split;
        }).bounds(cx - 80, 6, 70, 20).build());
        rotateBtn.active = !split;

        addRenderableWidget(Button.builder(Component.translatable("screen.neoorigins.hud_editor.reset"), b -> {
            for (var w : widgets) ResourceHudPositions.remove(w.id());
            ResourceHudPositions.remove(CooldownHudOverlay.POSITION_KEY);
            ResourceHudPositions.removeByPrefix(CooldownClusterLayout.SLOT_KEY_PREFIX);
            ResourceHudPositions.setSplitCooldown(false);
            ResourceHudPositions.save();
            split = false;
            touched.clear();
            rebuildWidgets(false);
            splitBtn.setMessage(splitLabel());
            rotateBtn.active = true;
        }).bounds(cx - 5, 6, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"),
            b -> nudgeScale(-SCALE_STEP)).bounds(cx + 70, 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
            b -> nudgeScale(SCALE_STEP)).bounds(cx + 95, 6, 20, 20).build());
    }

    private Component splitLabel() {
        return Component.translatable(split
            ? "screen.neoorigins.hud_editor.merge"
            : "screen.neoorigins.hud_editor.split");
    }

    private void nudgeScale(float delta) {
        if (selected < 0 || selected >= widgets.size() || !widgets.get(selected).isAbility()) return;
        Widget w = widgets.get(selected);
        float s = Math.max(ResourceHudPositions.MIN_SCALE,
            Math.min(ResourceHudPositions.MAX_SCALE, w.scale() + delta));
        if (s != w.scale()) {
            widgets.set(selected, w.scaled(s));
            touched.add(w.id());
        }
    }

    /**
     * Rebuilds the widget list from live resource state + saved (or, with
     * {@code fromCurrent}, just-persisted) layout. Resource bars use their
     * saved spot, else the stacked default above the hotbar; ability widgets
     * come from the shared cluster layout.
     */
    private void rebuildWidgets(boolean fromCurrent) {
        widgets.clear();
        var resources = ClientResourceState.getResources();
        int defaultX = width / 2 - 91;
        int defaultBaseY = height - 49;
        int idx = 0;
        for (var entry : resources.entrySet()) {
            ResourceHudPositions.Pos saved = ResourceHudPositions.get(entry.getKey());
            float xPct, yPct;
            if (saved != null) {
                xPct = saved.xPct();
                yPct = saved.yPct();
            } else {
                xPct = (float) defaultX / width;
                yPct = (float) (defaultBaseY - idx * 16) / height;
            }
            widgets.add(new Widget(entry.getKey(), entry.getValue(), null, xPct, yPct, 1.0f, false));
            idx++;
        }

        var slots = editorSlotsOrMock();
        ResourceHudPositions.Pos cluster = ResourceHudPositions.get(CooldownHudOverlay.POSITION_KEY);
        float cScale = cluster != null ? cluster.effScale() : 1.0f;
        boolean cVert = cluster != null && cluster.vertical();
        int[] origin = CooldownClusterLayout.clusterOrigin(width, height, slots.size(), cluster, cScale);

        selected = -1;
        if (!split) {
            widgets.add(new Widget(CooldownHudOverlay.POSITION_KEY, null, null,
                origin[0] / (float) width, origin[1] / (float) height, cScale, cVert));
            selected = widgets.size() - 1;
        } else {
            var cells = CooldownClusterLayout.clusterCells(origin[0], origin[1], cVert, cScale, slots);
            for (var c : cells) {
                String key = CooldownClusterLayout.slotKey(c.slot());
                ResourceHudPositions.Pos saved = ResourceHudPositions.get(key);
                widgets.add(saved != null
                    ? new Widget(key, null, c.slot(), saved.xPct(), saved.yPct(), saved.effScale(), false)
                    : new Widget(key, null, c.slot(),
                        c.x() / (float) width, c.y() / (float) height, c.scale(), false));
                if (selected < 0) selected = widgets.size() - 1;
            }
        }
    }

    /** Writes the ability widgets' current state into {@link ResourceHudPositions} (no disk save). */
    private void persistAbilityWidgets() {
        for (Widget w : widgets) {
            if (w.data() != null) continue;
            if (w.isCluster()) {
                ResourceHudPositions.set(w.id(),
                    new ResourceHudPositions.Pos(w.xPct(), w.yPct(), w.scale(), w.vertical()));
            } else if (touched.contains(w.id()) || ResourceHudPositions.get(w.id()) != null) {
                // Only slots the user actually placed get their own entry —
                // untouched slots keep following the cluster layout.
                ResourceHudPositions.set(w.id(),
                    new ResourceHudPositions.Pos(w.xPct(), w.yPct(), w.scale(), false));
            }
        }
    }

    /**
     * Live-preview cells for the ability cluster: every synced roster slot,
     * shown regardless of the {@code hud_ability_display} mode so the whole
     * cluster can be placed and inspected here. Falls back to two mock cells
     * when nothing is synced yet (no origin / fresh login).
     */
    private static List<CooldownHudOverlay.RenderSlot> editorSlotsOrMock() {
        var cooldowns = ClientCooldownState.getCooldowns();
        List<CooldownHudOverlay.RenderSlot> out = new ArrayList<>();
        for (var e : ClientAbilitySlots.get()) {
            ClientCooldownState.CooldownEntry cd = cooldowns.get(e.slot());
            Object icon = e.icon().isEmpty() ? null : CooldownHudOverlay.resolveIcon(e.icon());
            // Same cooldown-payload icon fallback as the HUD overlay, so the
            // preview shows (and hit-tests) the exact cells the HUD draws.
            if (icon == null && cd != null && !cd.icon().isEmpty()) {
                icon = CooldownHudOverlay.resolveIcon(cd.icon());
            }
            boolean toggledOn = !e.toggleable() || ClientActivePowers.isActive(e.powerId());
            out.add(new CooldownHudOverlay.RenderSlot(e.slot(), e.powerId(), icon, cd,
                e.toggleable(), toggledOn, e.countdown() || (cd != null && cd.countdown())));
        }
        if (out.isEmpty()) {
            out.add(new CooldownHudOverlay.RenderSlot(0, null, null, null, false, true, false));
            out.add(new CooldownHudOverlay.RenderSlot(1, null, null, null, false, true, false));
        }
        return out;
    }

    /** Cells an ability widget currently occupies, in screen coordinates. */
    private List<CooldownClusterLayout.Cell> widgetCells(Widget w, List<CooldownHudOverlay.RenderSlot> slots) {
        int bx = Math.round(w.xPct() * width);
        int by = Math.round(w.yPct() * height);
        if (w.isCluster()) {
            return CooldownClusterLayout.clusterCells(bx, by, w.vertical(), w.scale(), slots);
        }
        return List.of(new CooldownClusterLayout.Cell(w.slot(), bx, by, w.scale()));
    }

    /** Screen-space bounding box {x0, y0, x1, y1} of a widget (borders excluded). */
    private int[] widgetBounds(Widget w, List<CooldownHudOverlay.RenderSlot> slots) {
        int bx = Math.round(w.xPct() * width);
        int by = Math.round(w.yPct() * height);
        if (w.data() != null) {
            return new int[]{bx, by - LABEL_HEIGHT, bx + BAR_WIDTH, by + BAR_HEIGHT + READOUT_HEIGHT};
        }
        int count = w.isCluster() ? slots.size() : 1;
        int ext = Math.round(CooldownClusterLayout.clusterExtent(count) * w.scale());
        int cellW = Math.round(CooldownHudOverlay.BAR_W * w.scale());
        int cellH = Math.round(CooldownHudOverlay.BAR_H * w.scale());
        // Icon cells rise above the bar row; the label strip on bar cells
        // occupies a similar band, so always reserve the taller of the two.
        int topExt = Math.round(
            Math.max(CooldownHudOverlay.ICON_SIZE - CooldownHudOverlay.BAR_H, LABEL_HEIGHT) * w.scale());
        int wpx = w.isCluster() && w.vertical() ? cellW : (w.isCluster() ? ext : cellW);
        int hpx = w.isCluster() && w.vertical() ? ext : cellH;
        return new int[]{bx, by - topExt, bx + wpx, by + hpx};
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor g) {
        // No-op: suppress vanilla blur; we draw our own backdrop.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);

        Minecraft mc = Minecraft.getInstance();
        var slots = editorSlotsOrMock();
        CooldownHudOverlay.RenderSlot hoveredSlot = null;

        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            int bx = Math.round(w.xPct() * width);
            int by = Math.round(w.yPct() * height);
            int border = (i == dragging) ? BORDER_DRAG
                : (w.isAbility() && i == selected) ? BORDER_SELECTED : BORDER_COLOR;

            if (w.isAbility()) {
                int[] b = widgetBounds(w, slots);
                g.fill(b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, border);
                g.fill(b[0], b[1], b[2], b[3], 0x40000000);
                for (var cell : widgetCells(w, slots)) {
                    if (renderEditorCell(g, mc, cell, mouseX, mouseY)) {
                        hoveredSlot = cell.slot();
                    }
                }
                String tag = w.isCluster() ? "CD" : CooldownHudOverlay.legacyLabel(w.slot().slot());
                int tagW = mc.font.width(tag);
                g.text(mc.font, tag, bx + (b[2] - b[0] - tagW) / 2, b[3] + 2, LABEL_COLOR, false);
                continue;
            }

            // Border + background
            g.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, border);
            g.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, BG_COLOR);

            // Fill bar
            float frac = w.data().fraction();
            int fillW = Math.round(BAR_WIDTH * frac);
            if (fillW > 0) {
                g.fill(bx, by, bx + fillW, by + BAR_HEIGHT, w.data().color());
            }

            // Label above
            String label = w.data().label();
            int labelW = mc.font.width(label);
            g.text(mc.font, label, bx + (BAR_WIDTH - labelW) / 2, by - LABEL_HEIGHT, LABEL_COLOR, false);

            // Value below
            String readout = w.data().value() + "/" + w.data().max();
            int readoutW = mc.font.width(readout);
            g.text(mc.font, readout, bx + (BAR_WIDTH - readoutW) / 2, by + BAR_HEIGHT + 2, LABEL_COLOR, false);
        }

        // Scale readout for the selected ability element, under the buttons.
        if (selected >= 0 && selected < widgets.size() && widgets.get(selected).isAbility()) {
            int pct = Math.round(widgets.get(selected).scale() * 100);
            Component scaleText = Component.translatable("screen.neoorigins.hud_editor.scale", pct);
            g.text(mc.font, scaleText, width / 2 - mc.font.width(scaleText) / 2, 30, 0xFFFFFFFF, false);
        }

        // Title
        g.text(mc.font, title, width / 2 - mc.font.width(title) / 2, height - 16, 0xFFFFFFFF, false);

        super.extractRenderState(g, mouseX, mouseY, partial);

        // Power name + description tooltip over a hovered cluster icon
        // (drawn last so it sits above everything, including buttons).
        if (hoveredSlot != null && dragging < 0) {
            var tooltip = CooldownHudOverlay.tooltipFor(hoveredSlot.powerId());
            if (tooltip != null) {
                CooldownHudOverlay.drawTooltip(g, mc, tooltip, mouseX, mouseY, width, height);
            }
        }
    }

    /**
     * Draws one cluster/slot cell at its layout position: icon cells reuse the
     * overlay's {@link CooldownHudOverlay#renderIconSlot}; icon-less cells get
     * a half-filled mock bar + slot label. Returns true when the mouse is over
     * the cell's icon (tooltip target).
     */
    private static boolean renderEditorCell(GuiGraphicsExtractor g, Minecraft mc,
                                            CooldownClusterLayout.Cell cell,
                                            int mouseX, int mouseY) {
        var rs = cell.slot();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cell.x(), cell.y());
        pose.scale(cell.scale(), cell.scale());
        if (rs.icon() == null) {
            String cl = CooldownHudOverlay.legacyLabel(rs.slot());
            int clw = mc.font.width(cl);
            g.text(mc.font, cl, (CooldownHudOverlay.BAR_W - clw) / 2, -LABEL_HEIGHT, LABEL_COLOR, false);
            g.fill(0, 0, CooldownHudOverlay.BAR_W, CooldownHudOverlay.BAR_H, BG_COLOR);
            g.fill(0, 0, CooldownHudOverlay.BAR_W / 2, CooldownHudOverlay.BAR_H, CD_BAR_FILL);
        } else {
            CooldownHudOverlay.renderIconSlot(g, mc, rs,
                (CooldownHudOverlay.BAR_W - CooldownHudOverlay.ICON_SIZE) / 2,
                CooldownHudOverlay.BAR_H - CooldownHudOverlay.ICON_SIZE);
        }
        pose.popMatrix();
        return cell.iconHit(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromWidget) {
        if (super.mouseClicked(event, fromWidget)) return true;
        if (event.button() != 0) return false;
        double mx = event.x();
        double my = event.y();
        var slots = editorSlotsOrMock();
        // Find which widget was clicked (iterate in reverse so topmost wins)
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            int[] b = widgetBounds(w, slots);
            if (mx >= b[0] - GRAB_PAD && mx <= b[2] + GRAB_PAD
                && my >= b[1] - GRAB_PAD && my <= b[3] + GRAB_PAD) {
                dragging = i;
                if (w.isAbility()) selected = i;
                dragOffX = mx - Math.round(w.xPct() * width);
                dragOffY = my - Math.round(w.yPct() * height);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging >= 0 && event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            Widget w = widgets.get(dragging);
            var slots = editorSlotsOrMock();
            int[] b = widgetBounds(w, slots);
            int anchorX = Math.round(w.xPct() * width);
            int anchorY = Math.round(w.yPct() * height);
            int wpx = b[2] - b[0];                       // box size
            int topPad = anchorY - b[1];                 // anchor offset inside the box
            int botPad = b[3] - anchorY;
            float newX = (float) (mx - dragOffX) / width;
            float newY = (float) (my - dragOffY) / height;
            newX = Math.max(0, Math.min(newX, 1.0f - (float) wpx / width));
            newY = Math.max((float) topPad / height, Math.min(newY, 1.0f - (float) botPad / height));
            widgets.set(dragging, w.at(newX, newY));
            if (w.isAbility()) touched.add(w.id());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging >= 0 && event.button() == 0) {
            dragging = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        // Persist all positions
        for (Widget w : widgets) {
            if (w.data() != null) {
                ResourceHudPositions.set(w.id(), w.xPct(), w.yPct());
            }
        }
        persistAbilityWidgets();
        ResourceHudPositions.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
