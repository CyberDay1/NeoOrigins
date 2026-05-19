package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Registry-backed item picker: the generic {@link SearchPickerOverlay} over
 * every registered item id (modded items appear for free via
 * {@code BuiltInRegistries.ITEM}) plus an optional SNBT {@code components}
 * field. No item icons in v1 — a filtered text list, matching the in-repo
 * vanilla-widget style.
 */
public final class ItemPickerOverlay {

    /** Receives the chosen item id and the SNBT components text ("" if unused). */
    public interface Sink { void accept(String itemId, String componentsSnbt); }

    private static List<String> itemIds; // sorted, cached on first use

    private final SearchPickerOverlay picker = new SearchPickerOverlay();
    private boolean wantComponents;
    private EditBox components;
    private CreatorHost parent;
    private int x, y, w, h;

    public boolean isOpen() { return picker.isOpen(); }

    /** Open the picker; {@code withComponents} adds the SNBT components field. */
    public void open(boolean withComponents, Sink sink, Runnable onClose) {
        this.wantComponents = withComponents;
        picker.setBottomReserve(withComponents ? 44 : 22);
        picker.open("pick item",
            () -> {
                if (itemIds == null) {
                    itemIds = BuiltInRegistries.ITEM.keySet().stream()
                        .map(Object::toString).sorted().toList();
                }
                return itemIds;
            },
            id -> sink.accept(id,
                wantComponents && components != null ? components.getValue().trim() : ""),
            onClose);
    }

    public void build(CreatorHost parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        picker.build(parent, x, y, w, h);
        if (wantComponents) {
            Font font = parent.font();
            components = new EditBox(font, x + 6, y + h - 40, w - 12, 16,
                Component.literal("components snbt"));
            components.setMaxLength(32767);
            parent.register(components);
        }
    }

    public void renderBackdrop(GuiGraphics g) {
        if (!picker.isOpen() || parent == null) return;
        picker.renderBackdrop(g);
        if (wantComponents) {
            g.drawString(parent.font(), "components (SNBT, optional)",
                x + 8, y + h - 52,
                com.cyberday1.neoorigins.screen.creator.CreatorStyle.TEXT_DIM, false);
        }
    }

    public void render(GuiGraphics g) { /* widgets draw on top; chrome is backdrop */ }

    public boolean onScroll(double mx, double my, double sy) {
        return picker.onScroll(mx, my, sy);
    }
}
