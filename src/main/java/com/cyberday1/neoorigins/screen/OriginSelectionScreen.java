package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class OriginSelectionScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_TOP        = 44;
    private static final int PANEL_BTM_MARGIN = 32;
    private static final int SEARCH_H         = 16;
    private static final int SEARCH_GAP       = 3;
    private static final int LIST_BTN_H       = 22;
    private static final int LIST_BTN_GAP     = 2;
    private static final int LEFT_W           = 164;
    private static final int PANEL_GAP        = 8;
    private static final int DETAIL_PAD       = 10;
    private static final int HEADER_H         = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10; // 76
    private static final int DOT_SIZE         = 5;
    private static final int DOT_SPACING      = 8;
    private static final int DOT_COUNT        = 4;

    // ── Row model ─────────────────────────────────────────────────────────────
    private sealed interface ListRow permits ListRow.Header, ListRow.OriginEntry {
        record Header(String label) implements ListRow {}
        record OriginEntry(Identifier id) implements ListRow {}
    }
    private record VisibleHeader(int y, String label) {}

    // ── Screen state ──────────────────────────────────────────────────────────
    private final boolean isOrb;
    private List<OriginLayer> pendingLayers = new ArrayList<>();
    private int currentLayerIndex           = 0;
    private Identifier selectedOriginId     = null;
    private int listScrollOffset            = 0;
    private int detailScrollOffset          = 0;
    private String searchText               = "";

    // Full sorted+grouped list, filtered view, and flat list for Random
    private final List<ListRow>    allRows      = new ArrayList<>();
    private final List<ListRow>    filteredRows = new ArrayList<>();
    private final List<Identifier> allOriginIds = new ArrayList<>();

    // Headers visible in the current scroll window (populated in refreshOriginWidgets)
    private final List<VisibleHeader> visibleHeaders = new ArrayList<>();
    private final List<OriginButton>  originButtons  = new ArrayList<>();

    // Computed geometry
    private int panelX, panelBottom, rightX, rightW, listTop, listVisibleCount;

    // Cached right-panel detail content
    private List<FormattedCharSequence> descLines      = List.of();
    private List<String>                powerNames     = List.of();
    private List<String>                powerDescs     = List.of();
    private int                         detailContentH = 0;

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

        int totalW       = Math.min(width - 40, 660);
        panelX           = (width - totalW) / 2;
        panelBottom      = height - PANEL_BTM_MARGIN;
        rightX           = panelX + LEFT_W + PANEL_GAP;
        rightW           = totalW - LEFT_W - PANEL_GAP;
        listTop          = PANEL_TOP + SEARCH_H + SEARCH_GAP;
        listVisibleCount = Math.max(1, (panelBottom - listTop) / (LIST_BTN_H + LIST_BTN_GAP));

        rebuildLayer();
    }

    private void rebuildLayer() {
        allRows.clear();
        allOriginIds.clear();
        listScrollOffset   = 0;
        detailScrollOffset = 0;
        searchText         = "";

        if (currentLayerIndex >= pendingLayers.size()) { onClose(); return; }

        OriginLayer layer = pendingLayers.get(currentLayerIndex);

        // Collect valid origin IDs
        List<Identifier> rawIds = new ArrayList<>();
        for (var co : layer.origins()) {
            if (OriginDataManager.INSTANCE.hasOrigin(co.origin()))
                rawIds.add(co.origin());
        }

        // Group by namespace
        Map<String, List<Identifier>> byNamespace = new LinkedHashMap<>();
        for (Identifier id : rawIds)
            byNamespace.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);

        // Sort: neoorigins first, then alphabetically by mod display name
        List<String> namespaces = new ArrayList<>(byNamespace.keySet());
        namespaces.sort((a, b) -> {
            if ("neoorigins".equals(a)) return -1;
            if ("neoorigins".equals(b)) return 1;
            return getModName(a).compareToIgnoreCase(getModName(b));
        });

        for (String ns : namespaces) {
            allRows.add(new ListRow.Header(getModName(ns)));
            List<Identifier> nsIds = byNamespace.get(ns);
            nsIds.sort(Comparator.comparing(id -> getOriginDisplayName(id).toLowerCase(Locale.ROOT)));
            for (Identifier id : nsIds) {
                allRows.add(new ListRow.OriginEntry(id));
                allOriginIds.add(id);
            }
        }

        applySearch();
        refreshOriginWidgets();
        updateDetailCache();
    }

    /** Rebuilds filteredRows from allRows using the current searchText. */
    private void applySearch() {
        filteredRows.clear();
        if (searchText.isEmpty()) {
            filteredRows.addAll(allRows);
            return;
        }
        String lower = searchText.toLowerCase(Locale.ROOT);
        ListRow.Header pendingHeader = null;
        for (ListRow row : allRows) {
            if (row instanceof ListRow.Header h) {
                pendingHeader = h;
            } else if (row instanceof ListRow.OriginEntry e) {
                if (getOriginDisplayName(e.id()).toLowerCase(Locale.ROOT).contains(lower)) {
                    if (pendingHeader != null) {
                        filteredRows.add(pendingHeader);
                        pendingHeader = null;
                    }
                    filteredRows.add(e);
                }
            }
        }
    }

    private void refreshOriginWidgets() {
        clearWidgets();
        originButtons.clear();
        visibleHeaders.clear();

        // Search bar
        var search = new EditBox(font, panelX, PANEL_TOP + 1, LEFT_W, SEARCH_H,
            Component.literal("Search origins"));
        search.setMaxLength(64);
        search.setHint(Component.literal("Search..."));
        search.setBordered(true);
        search.setValue(searchText);
        search.setResponder(text -> {
            if (!text.equals(searchText)) {
                searchText = text;
                listScrollOffset = 0;
                applySearch();
                refreshOriginWidgets();
            }
        });
        addRenderableWidget(search);

        // Origin list rows
        int end  = Math.min(filteredRows.size(), listScrollOffset + listVisibleCount);
        int btnY = listTop;
        for (int i = listScrollOffset; i < end; i++) {
            ListRow row = filteredRows.get(i);
            if (row instanceof ListRow.Header h) {
                visibleHeaders.add(new VisibleHeader(btnY, h.label()));
            } else if (row instanceof ListRow.OriginEntry e) {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(e.id());
                if (origin != null) {
                    var btn = new OriginButton(panelX, btnY, LEFT_W, LIST_BTN_H, origin,
                        b -> selectOrigin(e.id()));
                    btn.setSelected(e.id().equals(selectedOriginId));
                    originButtons.add(btn);
                    addRenderableWidget(btn);
                }
            }
            btnY += LIST_BTN_H + LIST_BTN_GAP;
        }

        // Bottom buttons
        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        int cy = height - 24;
        int cx = width / 2;

        randomButton = Button.builder(Component.translatable("button.neoorigins.random"), b -> {
            if (!layer.allowRandom() || allOriginIds.isEmpty()) return;
            selectOrigin(allOriginIds.get((int) (Math.random() * allOriginIds.size())));
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
        var updated = new HashMap<>(ClientOriginState.getOrigins());
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
            descLines = List.of(); powerNames = List.of(); powerDescs = List.of(); detailContentH = 0; return;
        }
        Origin origin = OriginDataManager.INSTANCE.getOrigin(selectedOriginId);
        if (origin == null) { descLines = List.of(); powerNames = List.of(); powerDescs = List.of(); return; }

        int descW = rightW - DETAIL_PAD * 2 - 6;
        descLines = font.split(origin.description(), descW);

        powerNames = new ArrayList<>();
        powerDescs = new ArrayList<>();

        Map<Identifier, Identifier> subToParent = new HashMap<>();
        for (var entry : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.entrySet())
            for (Identifier subId : entry.getValue())
                subToParent.put(subId, entry.getKey());
        Set<Identifier> seenParents = new HashSet<>();

        net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();

        for (Identifier powerId : origin.powers()) {
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);

            String holderName = holder != null ? holder.name().getString() : "";
            String holderDesc = holder != null ? holder.description().getString() : "";

            String nameKey    = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".name";
            String descKey    = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".description";
            String resolvedName = !holderName.isEmpty() ? holderName
                : lang.has(nameKey) ? lang.getOrDefault(nameKey, "") : "";
            String resolvedDesc = !holderDesc.isEmpty() ? holderDesc
                : lang.has(descKey) ? lang.getOrDefault(descKey, "") : "";

            boolean isNamed  = !resolvedName.isEmpty();
            Identifier parentId = subToParent.get(powerId);

            if (parentId != null && !isNamed) {
                if (!seenParents.add(parentId)) continue;
                JsonObject display = OriginsMultipleExpander.MULTIPLE_DISPLAY_MAP.get(parentId);
                powerNames.add(display != null && display.has("name")
                    ? resolveDisplayString(display.get("name")) : formatPowerId(parentId));
                powerDescs.add(display != null && display.has("description")
                    ? resolveDisplayString(display.get("description")) : "");
                continue;
            }

            powerNames.add(isNamed ? resolvedName : formatPowerId(powerId));
            powerDescs.add(resolvedDesc);
        }

        int powerSectionH = 0;
        if (!powerNames.isEmpty()) {
            powerSectionH = 9 + 4;
            for (int i = 0; i < powerNames.size(); i++) {
                powerSectionH += 11;
                if (i < powerDescs.size() && !powerDescs.get(i).isEmpty()) powerSectionH += 10;
            }
        }
        detailContentH = 8 + descLines.size() * 10 + 8 + powerSectionH + 6;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);

        if (currentLayerIndex >= pendingLayers.size()) return;

        // Title only — no redundant layer name subtitle
        g.drawCenteredString(font, getTitle(), width / 2, 14, 0xFFFFFFFF);

        // Progress indicator
        String prog = (currentLayerIndex + 1) + " / " + pendingLayers.size();
        g.drawString(font, prog, width - 10 - font.width(prog), 26, 0xFF555577, false);

        // Left panel background
        g.fill(panelX - 1, PANEL_TOP - 1, panelX + LEFT_W + 1, panelBottom + 1, 0xFF0E0E1C);
        g.renderOutline(panelX - 1, PANEL_TOP - 1, LEFT_W + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        // Section headers (rendered before super so buttons are drawn on top)
        for (var vh : visibleHeaders) {
            g.fill(panelX, vh.y(), panelX + LEFT_W, vh.y() + LIST_BTN_H, 0xFF080818);
            g.fill(panelX, vh.y() + 5, panelX + 2, vh.y() + LIST_BTN_H - 5, 0xFF334488);
            g.drawString(font, vh.label().toUpperCase(), panelX + 6, vh.y() + 7, 0xFF445577, false);
        }

        // Scroll hint
        if (getMaxListScroll() > 0)
            g.drawString(font, "scroll for more", panelX, panelBottom + 3, 0xFF334466, false);

        super.render(g, mouseX, mouseY, partial);

        renderDetailPanel(g);
    }

    private void renderDetailPanel(GuiGraphics g) {
        g.fill(rightX, PANEL_TOP, rightX + rightW, panelBottom, 0xFF09091A);
        g.renderOutline(rightX - 1, PANEL_TOP - 1, rightW + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        if (selectedOriginId == null) {
            g.drawCenteredString(font, Component.literal("Select an origin to view details"),
                rightX + rightW / 2, PANEL_TOP + (panelBottom - PANEL_TOP) / 2 - 4, 0xFF333355);
            return;
        }

        Origin origin = OriginDataManager.INSTANCE.getOrigin(selectedOriginId);
        if (origin == null) return;

        int cx = rightX + rightW / 2;
        int y  = PANEL_TOP + DETAIL_PAD;

        // Icon
        g.fill(cx - 16, y, cx + 16, y + 32, 0xFF0D1830);
        g.renderOutline(cx - 16, y, 32, 32, 0xFF4A90D9);
        OriginButton.renderIcon(g, origin.icon(), cx - 8, y + 8);
        y += 32 + 6;

        g.drawCenteredString(font, origin.name(), cx, y, 0xFFFFFFFF);
        y += 9 + 4;

        drawImpactRow(g, cx, y, origin.impact());

        // Scrollable content
        int scrollTop   = PANEL_TOP + HEADER_H;
        int scrollBottom = panelBottom - 2;
        int scrollAreaH = scrollBottom - scrollTop;
        int maxScroll   = Math.max(0, detailContentH - scrollAreaH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxScroll);

        g.enableScissor(rightX + 1, scrollTop, rightX + rightW - 5, scrollBottom);
        int sy = scrollTop - detailScrollOffset;

        g.fill(rightX + DETAIL_PAD, sy + 3, rightX + rightW - DETAIL_PAD - 6, sy + 4, 0xFF252540);
        sy += 8;

        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, rightX + DETAIL_PAD, sy, 0xFF9999BB, false);
            sy += 10;
        }
        sy += 8;

        if (!powerNames.isEmpty()) {
            g.drawString(font, "Powers", rightX + DETAIL_PAD, sy, 0xFFCCCCDD, false);
            sy += 9 + 4;
            for (int i = 0; i < powerNames.size(); i++) {
                g.fill(rightX + DETAIL_PAD, sy + 3, rightX + DETAIL_PAD + 3, sy + 6, 0xFF4A90D9);
                g.drawString(font, powerNames.get(i), rightX + DETAIL_PAD + 8, sy, 0xFF7AACDA, false);
                sy += 11;
                if (i < powerDescs.size() && !powerDescs.get(i).isEmpty()) {
                    g.drawString(font, powerDescs.get(i), rightX + DETAIL_PAD + 8, sy, 0xFF445566, false);
                    sy += 10;
                }
            }
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int barX   = rightX + rightW - 4;
            int thumbH = Math.max(14, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) detailScrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 2, scrollBottom, 0xFF1A1A30);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF4A90D9);
        }
    }

    private void drawImpactRow(GuiGraphics g, int cx, int y, Impact impact) {
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0     = cx - totalW / 2;
        int filled = impact.getDotCount();
        for (int i = 0; i < DOT_COUNT; i++) {
            g.fill(x0 + i * DOT_SPACING, y, x0 + i * DOT_SPACING + DOT_SIZE, y + DOT_SIZE,
                i < filled ? 0xFFFF8822 : 0xFF252540);
        }
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
        return mx >= panelX && mx <= panelX + LEFT_W && my >= listTop && my <= panelBottom;
    }
    private boolean isOverRightPanel(double mx, double my) {
        return mx >= rightX && mx <= rightX + rightW && my >= PANEL_TOP && my <= panelBottom;
    }
    private int getMaxListScroll() {
        return Math.max(0, filteredRows.size() - listVisibleCount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String getModName(String namespace) {
        return ModList.get()
            .getModContainerById(namespace)
            .map(c -> c.getModInfo().getDisplayName())
            .orElseGet(() -> namespace.isEmpty() ? namespace
                : Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1));
    }

    private static String getOriginDisplayName(Identifier id) {
        Origin o = OriginDataManager.INSTANCE.getOrigin(id);
        return o != null ? o.name().getString() : id.getPath();
    }

    private static String resolveDisplayString(JsonElement el) {
        if (el == null) return "";
        if (el.isJsonPrimitive()) {
            String key = el.getAsString();
            return net.minecraft.locale.Language.getInstance().getOrDefault(key, key);
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text"))      return obj.get("text").getAsString();
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                return net.minecraft.locale.Language.getInstance().getOrDefault(key, key);
            }
        }
        return "";
    }

    private static String formatPowerId(Identifier id) {
        String path = id.getPath();
        int firstSlash = path.indexOf('/');
        if (firstSlash >= 0) path = path.substring(firstSlash + 1);
        String[] segments = path.split("/");
        StringBuilder out = new StringBuilder();
        for (String seg : segments) {
            if (out.length() > 0) out.append(": ");
            boolean firstWord = true;
            for (String word : seg.split("_")) {
                if (word.isEmpty()) continue;
                if (!firstWord) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0)));
                out.append(word.substring(1));
                firstWord = false;
            }
        }
        return out.isEmpty() ? path : out.toString();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
