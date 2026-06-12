package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the ability-cluster geometry: where each slot
 * cell sits on screen given the saved layout (position, horizontal/vertical
 * orientation, scale, split mode). The HUD overlay render pass, its
 * screen-event hover hit-test and the HUD editor (render + drag + hover) all
 * consume the same {@link Cell} math, so a cell can never render in one place
 * and hit-test in another.
 *
 * <p>A cell is anchored at its <em>bar row</em> top-left (the historical
 * anchor), with the 16×16 icon rising {@code ICON_SIZE - BAR_H} px above it.
 * All pixel offsets inside a cell are expressed in unscaled (local) units and
 * multiplied by the cell's scale when mapped to the screen.
 */
public final class CooldownClusterLayout {

    /** Unscaled step between neighbouring cells, both orientations. */
    public static final int STRIDE = CooldownHudOverlay.BAR_W + CooldownHudOverlay.BAR_GAP;

    /** Key prefix for per-slot saved positions in split mode. */
    public static final String SLOT_KEY_PREFIX = "neoorigins:cooldown_slot/";

    /**
     * One positioned slot cell.
     *
     * @param slot  the merged render data for the cell.
     * @param x     screen x of the cell anchor (bar-row top-left).
     * @param y     screen y of the cell anchor.
     * @param scale render scale (0.5–2.0); icon, text and sweep all scale.
     */
    public record Cell(CooldownHudOverlay.RenderSlot slot, int x, int y, float scale) {

        /** Screen x of the icon's top-left corner. */
        public int iconX() {
            return x + Math.round((CooldownHudOverlay.BAR_W - CooldownHudOverlay.ICON_SIZE) / 2.0f * scale);
        }

        /** Screen y of the icon's top-left corner (rises above the bar row). */
        public int iconY() {
            return y + Math.round((CooldownHudOverlay.BAR_H - CooldownHudOverlay.ICON_SIZE) * scale);
        }

        /** Scaled icon edge length in screen px. */
        public int iconSize() {
            return Math.round(CooldownHudOverlay.ICON_SIZE * scale);
        }

        /** True when (mx,my) is inside this cell's icon box (icon cells only). */
        public boolean iconHit(double mx, double my) {
            if (slot.icon() == null) return false;
            int ix = iconX(), iy = iconY(), s = iconSize();
            return mx >= ix && mx < ix + s && my >= iy && my < iy + s;
        }
    }

    /** Per-slot saved-position key: power id when known, else the slot index. */
    public static String slotKey(CooldownHudOverlay.RenderSlot rs) {
        Identifier id = rs.powerId();
        return SLOT_KEY_PREFIX + (id != null ? id.toString() : "#" + rs.slot());
    }

    /** Unscaled cluster length along its axis for {@code count} cells. */
    public static int clusterExtent(int count) {
        if (count <= 0) return 0;
        return count * CooldownHudOverlay.BAR_W
            + (count - 1) * CooldownHudOverlay.BAR_GAP;
    }

    /**
     * Cluster anchor (bar row of the first cell): the saved editor position
     * when set, else the historical centered / {@code screenH - 52} default
     * (vertical default keeps the same anchor; only the step direction turns).
     */
    public static int[] clusterOrigin(int screenW, int screenH, int count,
                                      ResourceHudPositions.Pos saved, float scale) {
        if (saved != null) {
            return new int[]{Math.round(saved.xPct() * screenW), Math.round(saved.yPct() * screenH)};
        }
        int totalW = Math.round(clusterExtent(count) * scale);
        return new int[]{(screenW - totalW) / 2, screenH - 52};
    }

    /**
     * Pure layout: cells for {@code slots} given an explicit cluster anchor,
     * orientation and scale (no split overrides). The editor feeds its live
     * drag state through this; {@link #fromSaved} feeds the persisted state.
     */
    public static List<Cell> clusterCells(int originX, int originY, boolean vertical, float scale,
                                          List<CooldownHudOverlay.RenderSlot> slots) {
        List<Cell> out = new ArrayList<>(slots.size());
        int idx = 0;
        for (var rs : slots) {
            int off = Math.round(idx * STRIDE * scale);
            out.add(new Cell(rs,
                vertical ? originX : originX + off,
                vertical ? originY + off : originY,
                scale));
            idx++;
        }
        return out;
    }

    /**
     * Layout from the persisted state in {@link ResourceHudPositions}: the
     * cluster position/orientation/scale, or — in split mode — each slot's own
     * saved position (slots never placed individually fall back to their
     * cluster-layout spot, so enabling split changes nothing until you drag).
     * Used identically by the HUD render pass and the hover hit-test.
     */
    public static List<Cell> fromSaved(int screenW, int screenH,
                                       List<CooldownHudOverlay.RenderSlot> slots) {
        ResourceHudPositions.Pos cluster = ResourceHudPositions.get(CooldownHudOverlay.POSITION_KEY);
        float clusterScale = cluster != null ? cluster.effScale() : 1.0f;
        boolean vertical = cluster != null && cluster.vertical();
        int[] origin = clusterOrigin(screenW, screenH, slots.size(), cluster, clusterScale);
        List<Cell> cells = clusterCells(origin[0], origin[1], vertical, clusterScale, slots);
        if (!ResourceHudPositions.isSplitCooldown()) return cells;

        List<Cell> out = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            ResourceHudPositions.Pos saved = ResourceHudPositions.get(slotKey(c.slot()));
            if (saved == null) {
                out.add(c);           // unplaced slot — keep its cluster spot
            } else {
                out.add(new Cell(c.slot(),
                    Math.round(saved.xPct() * screenW),
                    Math.round(saved.yPct() * screenH),
                    saved.effScale()));
            }
        }
        return out;
    }

    private CooldownClusterLayout() {}
}
