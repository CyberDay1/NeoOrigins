package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class OriginSelectionScreen extends Screen {

    private final boolean isOrb;
    private List<OriginLayer> pendingLayers = new ArrayList<>();
    private int currentLayerIndex = 0;
    private Identifier selectedOrigin = null;
    private final List<OriginButton> originButtons = new ArrayList<>();
    private final List<Identifier> availableOriginIds = new ArrayList<>();

    private Button confirmButton;
    private Button backButton;
    private Button randomButton;
    private Button scrollUpButton;
    private Button scrollDownButton;

    private int scrollOffset = 0;
    private int visibleButtons = 4;

    private static final int PANEL_TOP = 50;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_SPACING = 4;

    public OriginSelectionScreen(boolean isOrb) {
        super(Component.translatable("screen.neoorigins.choose_origin"));
        this.isOrb = isOrb;
    }

    @Override
    protected void init() {
        pendingLayers = LayerDataManager.INSTANCE.getSortedLayers().stream()
            .filter(layer -> !layer.hidden())
            .filter(layer -> !ClientOriginState.getOrigins().containsKey(layer.id()))
            .toList();

        if (pendingLayers.isEmpty()) {
            onClose();
            return;
        }

        int availableHeight = Math.max(120, height - 95);
        visibleButtons = Math.max(4, availableHeight / (BUTTON_HEIGHT + BUTTON_SPACING));

        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        originButtons.clear();
        availableOriginIds.clear();

        if (currentLayerIndex >= pendingLayers.size()) {
            onClose();
            return;
        }

        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        int panelWidth = Math.min(width - 60, 560);
        int panelX = (width - panelWidth) / 2;

        for (var conditionedOrigin : layer.origins()) {
            var origin = OriginDataManager.INSTANCE.getOrigin(conditionedOrigin.origin());
            if (origin != null) {
                availableOriginIds.add(conditionedOrigin.origin());
            }
        }

        scrollOffset = Mth.clamp(scrollOffset, 0, getMaxScrollOffset());

        int endExclusive = Math.min(availableOriginIds.size(), scrollOffset + visibleButtons);
        int btnY = PANEL_TOP;
        for (int i = scrollOffset; i < endExclusive; i++) {
            Identifier originId = availableOriginIds.get(i);
            var origin = OriginDataManager.INSTANCE.getOrigin(originId);
            if (origin == null) continue;

            var btn = new OriginButton(panelX, btnY, panelWidth, BUTTON_HEIGHT, origin, b -> selectOrigin(originId));
            btn.setSelected(originId.equals(selectedOrigin));
            originButtons.add(btn);
            addRenderableWidget(btn);
            btnY += BUTTON_HEIGHT + BUTTON_SPACING;
        }

        if (getMaxScrollOffset() > 0) {
            scrollUpButton = Button.builder(Component.literal("^"), b -> scrollBy(-1))
                .bounds(panelX + panelWidth + 6, PANEL_TOP, 20, 20)
                .build();
            scrollDownButton = Button.builder(Component.literal("v"), b -> scrollBy(1))
                .bounds(panelX + panelWidth + 6, PANEL_TOP + 24, 20, 20)
                .build();
            scrollUpButton.active = scrollOffset > 0;
            scrollDownButton.active = scrollOffset < getMaxScrollOffset();
            addRenderableWidget(scrollUpButton);
            addRenderableWidget(scrollDownButton);
        }

        randomButton = Button.builder(Component.translatable("button.neoorigins.random"), b -> {
            if (!layer.allowRandom()) return;
            if (!availableOriginIds.isEmpty()) {
                int idx = (int) (Math.random() * availableOriginIds.size());
                selectOrigin(availableOriginIds.get(idx));
            }
        }).bounds(20, height - 30, 80, 20).build();
        if (layer.allowRandom()) addRenderableWidget(randomButton);

        backButton = Button.builder(Component.literal("< Back"), b -> {
            if (currentLayerIndex > 0) {
                currentLayerIndex--;
                selectedOrigin = null;
                scrollOffset = 0;
                rebuildButtons();
            }
        }).bounds(width / 2 - 100, height - 30, 90, 20).build();
        addRenderableWidget(backButton);
        backButton.active = currentLayerIndex > 0;

        confirmButton = Button.builder(Component.literal("Confirm >"), b -> confirmSelection())
            .bounds(width / 2 + 10, height - 30, 90, 20)
            .build();
        addRenderableWidget(confirmButton);
        confirmButton.active = selectedOrigin != null;
    }

    private int getMaxScrollOffset() {
        return Math.max(0, availableOriginIds.size() - visibleButtons);
    }

    private void scrollBy(int amount) {
        int newOffset = Mth.clamp(scrollOffset + amount, 0, getMaxScrollOffset());
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            rebuildButtons();
        }
    }

    private void selectOrigin(Identifier id) {
        selectedOrigin = id;
        originButtons.forEach(btn -> btn.setSelected(btn.getOrigin().id().equals(id)));
        if (confirmButton != null) confirmButton.active = true;
    }

    private void confirmSelection() {
        if (selectedOrigin == null || currentLayerIndex >= pendingLayers.size()) return;

        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        ClientPacketDistributor.sendToServer(new ChooseOriginPayload(layer.id(), selectedOrigin));

        var currentOrigins = new java.util.HashMap<>(ClientOriginState.getOrigins());
        currentOrigins.put(layer.id(), selectedOrigin);
        ClientOriginState.setOrigins(currentOrigins, false);

        currentLayerIndex++;
        selectedOrigin = null;
        scrollOffset = 0;

        if (currentLayerIndex >= pendingLayers.size()) {
            onClose();
        } else {
            rebuildButtons();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (getMaxScrollOffset() <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int panelWidth = Math.min(width - 60, 560);
        int panelX = (width - panelWidth) / 2;
        int panelBottom = PANEL_TOP + visibleButtons * (BUTTON_HEIGHT + BUTTON_SPACING);
        boolean inList = mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= PANEL_TOP && mouseY <= panelBottom;
        if (inList && scrollY != 0) {
            scrollBy(scrollY > 0 ? -1 : 1);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0101010);

        if (currentLayerIndex >= pendingLayers.size()) return;
        OriginLayer layer = pendingLayers.get(currentLayerIndex);

        graphics.drawCenteredString(font, getTitle(), width / 2, 15, 0xFFFFFFFF);
        graphics.drawCenteredString(font, layer.name(), width / 2, 28, 0xFFCCCCCC);

        String progress = (currentLayerIndex + 1) + " / " + pendingLayers.size();
        graphics.drawString(font, progress, width - 20 - font.width(progress), 28, 0xFFAAAAAA);

        if (getMaxScrollOffset() > 0) {
            String scrollHint = "Scroll for more origins";
            graphics.drawString(font, scrollHint, 20, height - 44, 0xFFAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        Component impactLegend = Component.translatable("origins.gui.impact.impact")
            .append(": ")
            .append(Component.translatable("origins.gui.impact.none"))
            .append(" / ")
            .append(Component.translatable("origins.gui.impact.low"))
            .append(" / ")
            .append(Component.translatable("origins.gui.impact.medium"))
            .append(" / ")
            .append(Component.translatable("origins.gui.impact.high"));
        graphics.drawString(font, impactLegend, 20, height - 56, 0xFFCCCCCC);

    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}


