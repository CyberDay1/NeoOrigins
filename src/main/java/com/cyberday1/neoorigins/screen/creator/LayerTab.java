package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layer tab — pick the target layer for this origin. A class is just an origin
 * in the {@code neoorigins:class} layer, so there is no separate class UI; the
 * selector covers both and a hint line states which kind the choice produces.
 */
public final class LayerTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.layer");
    private static final Identifier CLASS_LAYER =
        Identifier.fromNamespaceAndPath("neoorigins", "class");

    private OriginCreatorScreen parent;
    private CycleSelector<Identifier> selector;
    private final Map<Identifier, String> names = new LinkedHashMap<>();
    private int rowY;

    @Override public Component title() { return TITLE; }
    @Override public Component help() {
        return Component.literal(
            "Which picker this appears in. \"class\" makes it a class instead of an origin.");
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        rowY = y + 18;

        names.clear();
        List<OriginLayer> layers = LayerDataManager.INSTANCE.getSortedLayers();
        for (OriginLayer l : layers) names.put(l.id(), l.name().getString());
        List<Identifier> ids = layers.isEmpty()
            ? List.of(parent.draft().layerId)
            : List.copyOf(names.keySet());

        selector = new CycleSelector<>(ids,
            id -> names.getOrDefault(id, id.toString()) + "  (" + id + ")");
        parent.register(selector.build(x + 8, rowY, Math.min(w - 16, 320), 20));
    }

    @Override
    public void pullFromDraft() { selector.setValue(parent.draft().layerId); }

    @Override
    public void pushToDraft() { parent.draft().layerId = selector.value(); }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        boolean isClass = CLASS_LAYER.equals(selector.value());
        g.text(font, "Target layer:", x + 8, rowY - 12, 0xFFBBBBCC, false);
        g.text(font,
            isClass ? "→ This origin will be a CLASS (neoorigins:class layer)."
                    : "→ This origin will be a normal ORIGIN in this layer.",
            x + 8, rowY + 28, isClass ? 0xFF7FB0FF : 0xFF99AA99, false);
    }
}
